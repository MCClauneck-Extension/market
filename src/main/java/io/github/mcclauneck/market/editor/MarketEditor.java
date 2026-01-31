package io.github.mcclauneck.market.editor;

import io.github.mcclauneck.market.common.MarketProvider;
import io.github.mcclauneck.market.editor.util.EditorUtil;
import io.github.mcengine.mceconomy.api.enums.CurrencyType;
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
import java.util.*;
import java.util.concurrent.Executor;

/**
 * Manages the in-game GUI for editing market listings.
 * <p>
 * This class handles:
 * <ul>
 * <li>Opening a paginated GUI for specific markets.</li>
 * <li>Handling interactions to edit prices and currencies.</li>
 * <li>Saving items placed in the GUI to the market's YAML config via {@link EditorUtil}.</li>
 * <li>Capturing chat input for numeric values.</li>
 * </ul>
 */
public class MarketEditor implements Listener {

    private final JavaPlugin plugin;
    private final MarketProvider provider;
    private final File marketFolder;
    private final Executor executor;

    // Keys for storing data on the ItemStack itself
    private final NamespacedKey keyBuy;
    private final NamespacedKey keySell;
    private final NamespacedKey keyCurrency;

    // Session tracking
    private final Map<UUID, EditorSession> activeSessions = new HashMap<>();
    private final Map<UUID, EditAction> pendingChat = new HashMap<>();
    // Tracks players switching pages to prevent InventoryCloseEvent from killing the session
    private final Set<UUID> isSwitchingPages = new HashSet<>();

    /**
     * Constructs a new MarketEditor.
     *
     * @param plugin       The host plugin instance.
     * @param provider     The market data provider (used for cache invalidation).
     * @param marketFolder The folder containing market configuration files.
     * @param executor     The async executor for file saving.
     */
    public MarketEditor(JavaPlugin plugin, MarketProvider provider, File marketFolder, Executor executor) {
        this.plugin = plugin;
        this.provider = provider;
        this.marketFolder = marketFolder;
        this.executor = executor;
        this.keyBuy = new NamespacedKey(plugin, "market_buy");
        this.keySell = new NamespacedKey(plugin, "market_sell");
        this.keyCurrency = new NamespacedKey(plugin, "market_currency");
    }

    /**
     * Opens the editor GUI for a specific market (Default Page 1).
     *
     * @param player     The admin player opening the editor.
     * @param marketName The name of the market file (without extension).
     */
    public void openEditor(Player player, String marketName) {
        openEditor(player, marketName, 1);
    }

    /**
     * Opens the editor GUI for a specific market and page.
     * <p>
     * Loads items from the YAML file, applies editor metadata (prices/currency) onto
     * the items, and displays them in a 54-slot inventory.
     * </p>
     *
     * @param player     The admin player opening the editor.
     * @param marketName The name of the market file.
     * @param page       The page number to open.
     */
    public void openEditor(Player player, String marketName, int page) {
        // Optimization: We could use provider cache here, but to ensure admin sees latest disk state,
        // we can keep reading file OR use cache. Since opening editor is rare, reading file is acceptable,
        // but let's stick to the current implementation for safety, or we could use provider.getMarketItems()
        // and reconstruct the editor view. For now, sticking to direct file load to ensure "what is on disk is shown".
        // The major lag comes from WRITING, not reading a small file once.
        File file = new File(marketFolder, marketName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Inventory gui = Bukkit.createInventory(null, 54, "Edit Market: " + marketName + " | P" + page);

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        int maxKey = 0;
        if (itemsSection != null) {
            for (String k : itemsSection.getKeys(false)) {
                try {
                    int keyVal = Integer.parseInt(k);
                    if (keyVal > maxKey) maxKey = keyVal;
                } catch (NumberFormatException ignored) {}
            }
        }

        int itemsPerPage = 45;
        int startKey = (page - 1) * itemsPerPage + 1;

        for (int i = 0; i < itemsPerPage; i++) {
            int currentKey = startKey + i;
            if (itemsSection != null && itemsSection.contains(String.valueOf(currentKey))) {
                ConfigurationSection section = itemsSection.getConfigurationSection(String.valueOf(currentKey));
                if (section == null) continue;

                ItemStack item = section.getItemStack("metadata");
                if (item == null) {
                    item = new ItemStack(Material.STONE);
                }
                item.setAmount(section.getInt("amount", item.getAmount()));

                int buy = section.getInt("buy.price", -1);
                int sell = section.getInt("sell.price", -1);
                String curStr = section.getString("currency", "coin");
                CurrencyType currency = CurrencyType.fromName(curStr);
                if (currency == null) currency = CurrencyType.COIN;

                EditorUtil.updateItemData(item, buy, sell, currency, keyBuy, keySell, keyCurrency);
                gui.setItem(i, item);
            }
        }

        // Controls Area (Bottom Row)
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.setDisplayName(" ");
        glass.setItemMeta(gMeta);
        for (int i = 45; i < 54; i++) gui.setItem(i, glass);

        if (page > 1) {
            gui.setItem(45, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGNlYzgwN2RjYzE0MzYzMzRmZDRkYzlhYjM0OTM0MmY2YzUyYzllN2IyYmYzNDY3MTJkYjcyYTBkNmQ3YTQifX19", "Previous Page"));
        }
        boolean pageFull = (gui.getItem(44) != null);
        if (maxKey > (page * itemsPerPage) || pageFull) {
            gui.setItem(53, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTAxYzdiNTcyNjE3ODk3NGIzYjNhMDFiNDJhNTkwZTU0MzY2MDI2ZmQ0MzgwOGYyYTc4NzY0ODg0M2E3ZjVhIn19fQ==", "Next Page"));
        }
        
        gui.setItem(49, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTc0MjgxZjk2NjlmMmNkY2Y3ODQ4NDQ4YTViYjYyODIzMmVlYTJiZmJkZmM3ZDRmMjBiZGE1MDMzZDAzMzY2YSJ9fX0=", "Save & Reload"));

        activeSessions.put(player.getUniqueId(), new EditorSession(marketName, page));
        player.openInventory(gui);
    }

    /**
     * Handles clicks within the editor GUI.
     * <p>
     * Detects shift-clicks for price editing, middle-clicks for currency cycling,
     * and clicks on pagination buttons.
     * </p>
     *
     * @param event The inventory click event.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!activeSessions.containsKey(player.getUniqueId())) return;
        if (!event.getView().getTitle().startsWith("Edit Market:")) return;

        EditorSession session = activeSessions.get(player.getUniqueId());

        // Block interaction with control bar
        if (event.getRawSlot() >= 45 && event.getRawSlot() <= 53) {
            event.setCancelled(true);
            
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;

            int targetPage = session.page;
            boolean shouldSwitch = false;

            if (event.getSlot() == 45 && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                targetPage--;
                shouldSwitch = true;
            } else if (event.getSlot() == 53 && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                targetPage++;
                shouldSwitch = true;
            } else if (event.getSlot() == 49) {
                // Save & Reload button
                shouldSwitch = true; // Reload same page
            }

            if (shouldSwitch) {
                // Snapshot the inventory contents on the Main Thread
                ItemStack[] contents = event.getView().getTopInventory().getContents();
                
                // Perform save async
                executor.execute(() -> {
                    EditorUtil.savePage(marketFolder, session.marketName, session.page, contents, keyBuy, keySell, keyCurrency);
                    provider.reloadMarket(session.marketName);
                });
                
                isSwitchingPages.add(player.getUniqueId());
                player.closeInventory();
                int finalPage = targetPage;
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.marketName, finalPage));
            }
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.isShiftClick()) {
            event.setCancelled(true);
            String type = event.isLeftClick() ? "BUY" : (event.isRightClick() ? "SELL" : null);
            
            if (type != null) {
                // Snapshot contents before closing for chat input
                ItemStack[] contents = event.getInventory().getContents();
                executor.execute(() -> {
                    EditorUtil.savePage(marketFolder, session.marketName, session.page, contents, keyBuy, keySell, keyCurrency);
                    // No need to reload cache yet, we are about to edit more
                });

                pendingChat.put(player.getUniqueId(), new EditAction(event.getSlot(), type));
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Enter " + type + " price in chat (-1 to disable):");
            }
        } else if (event.getClick() == ClickType.MIDDLE) {
            event.setCancelled(true);
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String curStr = pdc.getOrDefault(keyCurrency, PersistentDataType.STRING, "coin");
            CurrencyType current = CurrencyType.fromName(curStr);
            if (current == null) current = CurrencyType.COIN;
            
            CurrencyType[] vals = CurrencyType.values();
            CurrencyType next = vals[(current.ordinal() + 1) % vals.length];
            
            int buy = pdc.getOrDefault(keyBuy, PersistentDataType.INTEGER, -1);
            int sell = pdc.getOrDefault(keySell, PersistentDataType.INTEGER, -1);
            
            EditorUtil.updateItemData(item, buy, sell, next, keyBuy, keySell, keyCurrency);
        }
    }

    /**
     * Handles chat input for price editing.
     * <p>
     * Captures the number typed by the player, updates the item in the GUI,
     * and re-saves the market page.
     * </p>
     *
     * @param event The chat event.
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (pendingChat.containsKey(uuid)) {
            event.setCancelled(true);
            EditAction action = pendingChat.remove(uuid);
            EditorSession session = activeSessions.get(uuid);
            
            try {
                int price = Integer.parseInt(event.getMessage());
                // Must run on main thread to interact with Bukkit API
                Bukkit.getScheduler().runTask(plugin, () -> {
                   openEditor(event.getPlayer(), session.marketName, session.page);
                   Inventory inv = event.getPlayer().getOpenInventory().getTopInventory();
                   ItemStack item = inv.getItem(action.slot);
                   if (item != null) {
                       ItemMeta meta = item.getItemMeta();
                       PersistentDataContainer pdc = meta.getPersistentDataContainer();
                       int buy = pdc.getOrDefault(keyBuy, PersistentDataType.INTEGER, -1);
                       int sell = pdc.getOrDefault(keySell, PersistentDataType.INTEGER, -1);
                       String curStr = pdc.getOrDefault(keyCurrency, PersistentDataType.STRING, "coin");
                       CurrencyType cur = CurrencyType.fromName(curStr);
                       if (cur == null) cur = CurrencyType.COIN;
                       
                       if (action.type.equals("BUY")) buy = price;
                       if (action.type.equals("SELL")) sell = price;
                       
                       EditorUtil.updateItemData(item, buy, sell, cur, keyBuy, keySell, keyCurrency);
                       
                       // Snapshot and Save Async
                       ItemStack[] contents = inv.getContents();
                       executor.execute(() -> {
                           EditorUtil.savePage(marketFolder, session.marketName, session.page, contents, keyBuy, keySell, keyCurrency);
                           provider.reloadMarket(session.marketName);
                       });
                   }
                });
            } catch (NumberFormatException e) {
                event.getPlayer().sendMessage(ChatColor.RED + "Invalid number.");
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(event.getPlayer(), session.marketName, session.page));
            }
        }
    }

    /**
     * Saves the market page when the inventory is closed.
     *
     * @param event The inventory close event.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (isSwitchingPages.contains(player.getUniqueId())) {
                isSwitchingPages.remove(player.getUniqueId());
                return;
            }
            if (activeSessions.containsKey(player.getUniqueId()) && !pendingChat.containsKey(player.getUniqueId())) {
                EditorSession session = activeSessions.remove(player.getUniqueId());
                
                // Snapshot contents (Sync)
                ItemStack[] contents = event.getInventory().getContents();
                
                // Save and Reload (Async)
                executor.execute(() -> {
                    EditorUtil.savePage(marketFolder, session.marketName, session.page, contents, keyBuy, keySell, keyCurrency);
                    provider.reloadMarket(session.marketName);
                    player.sendMessage(ChatColor.GREEN + "Market saved!");
                });
            }
        }
    }

    private record EditorSession(String marketName, int page) {}
    private record EditAction(int slot, String type) {}
}
