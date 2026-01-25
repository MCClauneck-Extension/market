package io.github.mcclauneck.market.editor;

import io.github.mcclauneck.market.common.MarketProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles the in-game editor for Market listings.
 * <p>
 * Interactions:
 * <ul>
 * <li><b>Drag/Drop:</b> Add items to the market.</li>
 * <li><b>Shift + Left Click:</b> Set Buy Price (Chat).</li>
 * <li><b>Shift + Right Click:</b> Set Sell Price (Chat).</li>
 * <li><b>Middle Click (Scroll):</b> Cycle Currency.</li>
 * </ul>
 * </p>
 */
public class MarketEditor implements Listener {

    private final JavaPlugin plugin;
    private final MarketProvider provider; // Added reference to provider
    private final File marketFolder;

    // Keys for storing data on the ItemStack itself
    private final NamespacedKey keyBuy;
    private final NamespacedKey keySell;
    private final NamespacedKey keyCurrency;

    // Session tracking
    private final Map<UUID, String> editingMarket = new HashMap<>();
    private final Map<UUID, EditAction> pendingChat = new HashMap<>();

    private final List<String> currencies = List.of("coin", "copper", "silver", "gold");

    /**
     * Constructs the MarketEditor.
     *
     * @param plugin       The host plugin.
     * @param provider     The market data provider (for cache refreshing).
     * @param marketFolder The folder containing market YML files.
     */
    public MarketEditor(JavaPlugin plugin, MarketProvider provider, File marketFolder) {
        this.plugin = plugin;
        this.provider = provider;
        this.marketFolder = marketFolder;
        this.keyBuy = new NamespacedKey(plugin, "market_buy");
        this.keySell = new NamespacedKey(plugin, "market_sell");
        this.keyCurrency = new NamespacedKey(plugin, "market_currency");
    }

    /**
     * Opens the editor GUI for a specific market.
     *
     * @param player     The admin player.
     * @param marketName The name of the market file.
     */
    public void openEditor(Player player, String marketName) {
        File file = new File(marketFolder, marketName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Inventory gui = Bukkit.createInventory(null, 54, "Edit Market: " + marketName);

        // Load items from YAML
        for (String key : config.getKeys(false)) {
            if (key.equals("name")) continue; // Skip metadata
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            int slot = section.getInt("position");
            if (slot < 0 || slot >= 54) continue;

            ItemStack item = section.getItemStack("metadata");
            // Fallback if metadata isn't fully saved, reconstruct from parts
            if (item == null) {
                String matName = section.getString("material", "STONE");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.STONE;
                item = new ItemStack(mat, section.getInt("item_amount", 1));
            }

            int buy = section.getInt("buy.price", -1);
            int sell = section.getInt("sell.price", -1);
            String currency = section.getString("currency", "coin");

            // Apply Data & Lore
            updateItemData(item, buy, sell, currency);
            gui.setItem(slot, item);
        }

        editingMarket.put(player.getUniqueId(), marketName);
        player.openInventory(gui);
    }

    /**
     * Handles interactions in the editor GUI.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!editingMarket.containsKey(player.getUniqueId())) return;
        if (!event.getView().getTitle().startsWith("Edit Market:")) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Ensure user is clicking top inventory for edit actions
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        // Shift + Left: Edit Buy
        if (event.isShiftClick() && event.isLeftClick()) {
            event.setCancelled(true);
            saveMarket(player, event.getInventory()); // Save current state
            pendingChat.put(player.getUniqueId(), new EditAction(event.getSlot(), "BUY"));
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Enter BUY price in chat (-1 to disable):");
        }
        // Shift + Right: Edit Sell
        else if (event.isShiftClick() && event.isRightClick()) {
            event.setCancelled(true);
            saveMarket(player, event.getInventory()); // Save current state
            pendingChat.put(player.getUniqueId(), new EditAction(event.getSlot(), "SELL"));
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Enter SELL price in chat (-1 to disable):");
        }
        // Middle Click (Scroll): Cycle Currency
        else if (event.getClick() == ClickType.MIDDLE) {
            event.setCancelled(true);
            
            // Read current data
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            PersistentDataContainer container = meta.getPersistentDataContainer();
            
            String currentCurrency = container.getOrDefault(keyCurrency, PersistentDataType.STRING, "coin");
            int buy = container.getOrDefault(keyBuy, PersistentDataType.INTEGER, -1);
            int sell = container.getOrDefault(keySell, PersistentDataType.INTEGER, -1);

            // Cycle logic
            int index = currencies.indexOf(currentCurrency);
            int nextIndex = (index + 1) % currencies.size();
            String nextCurrency = currencies.get(nextIndex);

            // Apply Update
            updateItemData(item, buy, sell, nextCurrency);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }

    /**
     * Handles chat input for price editing.
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (pendingChat.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            EditAction action = pendingChat.remove(player.getUniqueId());
            String marketName = editingMarket.get(player.getUniqueId());

            try {
                int price = Integer.parseInt(event.getMessage());

                // We need to re-open the GUI, modify the item, and save.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    openEditor(player, marketName);
                    Inventory top = player.getOpenInventory().getTopInventory();
                    ItemStack item = top.getItem(action.slot);

                    if (item != null) {
                        ItemMeta meta = item.getItemMeta();
                        PersistentDataContainer pdc = meta.getPersistentDataContainer();
                        
                        int buy = pdc.getOrDefault(keyBuy, PersistentDataType.INTEGER, -1);
                        int sell = pdc.getOrDefault(keySell, PersistentDataType.INTEGER, -1);
                        String cur = pdc.getOrDefault(keyCurrency, PersistentDataType.STRING, "coin");

                        if (action.type.equals("BUY")) buy = price;
                        if (action.type.equals("SELL")) sell = price;

                        updateItemData(item, buy, sell, cur);
                        // Save triggers automatically on close/re-open or we can force save
                        saveMarket(player, top);
                    }
                });

            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number.");
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, marketName));
            }
        }
    }

    /**
     * Saves the market configuration when the inventory is closed.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && editingMarket.containsKey(player.getUniqueId())) {
            if (!pendingChat.containsKey(player.getUniqueId())) {
                saveMarket(player, event.getInventory());
                editingMarket.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Market saved.");
            }
        }
    }

    /**
     * Writes the current inventory state to the YAML file.
     */
    private void saveMarket(Player player, Inventory inv) {
        String marketName = editingMarket.get(player.getUniqueId());
        File file = new File(marketFolder, marketName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Clear existing items to prevent duplicates/ghosts
        for (String key : config.getKeys(false)) {
            if (!key.equals("name")) config.set(key, null);
        }

        for (int i = 0; i < 54; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            int buy = pdc.getOrDefault(keyBuy, PersistentDataType.INTEGER, -1);
            int sell = pdc.getOrDefault(keySell, PersistentDataType.INTEGER, -1);
            String currency = pdc.getOrDefault(keyCurrency, PersistentDataType.STRING, "coin");

            // Clean item for saving (Remove Editor Lore)
            ItemStack saveItem = item.clone();
            cleanItemForSave(saveItem);

            String key = String.valueOf(i + 1);
            config.set(key + ".position", i);
            config.set(key + ".material", saveItem.getType().name());
            config.set(key + ".item_amount", saveItem.getAmount());
            config.set(key + ".buy.price", buy);
            config.set(key + ".sell.price", sell);
            config.set(key + ".currency", currency);
            config.set(key + ".metadata", saveItem); // Save pure item metadata
        }

        try { 
            config.save(file);
            // CRITICAL FIX: Reload the provider cache immediately after saving
            provider.loadMarkets(); 
        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Updates an ItemStack with new price data and refreshes the Lore.
     */
    private void updateItemData(ItemStack item, int buy, int sell, String currency) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Save Raw Data
        pdc.set(keyBuy, PersistentDataType.INTEGER, buy);
        pdc.set(keySell, PersistentDataType.INTEGER, sell);
        pdc.set(keyCurrency, PersistentDataType.STRING, currency);

        // Update Visual Lore
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "----------------");
        lore.add(ChatColor.GREEN + "Buy: " + (buy >= 0 ? buy : "N/A"));
        lore.add(ChatColor.AQUA + "Sell: " + (sell >= 0 ? sell : "N/A"));
        lore.add(ChatColor.GOLD + "Currency: " + currency);
        lore.add(ChatColor.DARK_GRAY + "----------------");
        lore.add(ChatColor.YELLOW + "Shift+L: Set Buy | Shift+R: Set Sell");
        lore.add(ChatColor.YELLOW + "Middle Click: Cycle Currency");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Removes the editor-specific lore before saving to disk.
     */
    private void cleanItemForSave(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore != null) {
            // Remove our injected lines (last 7 lines based on updateItemData)
            if (lore.size() >= 7 && lore.get(lore.size() - 1).contains("Middle Click")) {
                for (int i = 0; i < 7; i++) {
                    lore.remove(lore.size() - 1);
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private record EditAction(int slot, String type) {}
}
