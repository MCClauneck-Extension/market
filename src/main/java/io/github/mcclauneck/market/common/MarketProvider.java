package io.github.mcclauneck.market.common;

import io.github.mcclauneck.market.api.IMarket;
import io.github.mcengine.mceconomy.api.enums.CurrencyType;
import io.github.mcengine.mceconomy.common.MCEconomyProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The core logic implementation for the Market extension.
 * <p>
 * This class handles the loading of market configurations from YAML files,
 * manages the in-memory cache of market items, and executes the transactional
 * logic for buying and selling items safely using the Async/Sync pattern.
 * </p>
 */
public class MarketProvider implements IMarket {

    /**
     * The host plugin instance, used for scheduling synchronous tasks (e.g., giving items).
     */
    private final JavaPlugin plugin;

    /**
     * The directory containing the market configuration files.
     */
    private final File marketFolder;

    /**
     * In-memory cache of loaded markets.
     * <p>
     * Key: The market name (filename without extension).<br>
     * Value: A map of Slot Index -> MarketItem data.
     * </p>
     */
    private final Map<String, Map<Integer, MarketItem>> marketCache = new HashMap<>();

    /**
     * Constructs a new MarketProvider.
     *
     * @param plugin       The JavaPlugin instance.
     * @param marketFolder The data folder where market YAML files are stored.
     */
    public MarketProvider(JavaPlugin plugin, File marketFolder) {
        this.plugin = plugin;
        this.marketFolder = marketFolder;
        loadMarkets();
    }

    /**
     * Creates a new empty market file.
     *
     * @param name The name of the market to create.
     * @return true if created successfully, false if it already exists.
     */
    public boolean createMarket(String name) {
        File file = new File(marketFolder, name.toLowerCase() + ".yml");
        if (file.exists()) return false;

        try {
            if (file.createNewFile()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.set("name", name + " Market");
                config.save(file);
                
                // Refresh cache so the new market is recognized immediately
                loadMarkets();
                return true;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create market file: " + e.getMessage());
        }
        return false;
    }

    /**
     * Loads (or reloads) all .yml configuration files from the Market folder into memory.
     * <p>
     * This method iterates through files in {@link #marketFolder}, parses item data
     * (including full ItemStack metadata), and populates the {@link #marketCache}.
     * </p>
     */
    public void loadMarkets() {
        marketCache.clear(); // Clear cache before reloading
        if (!marketFolder.exists()) return;

        File[] files = marketFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String marketName = file.getName().replace(".yml", "");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Map<Integer, MarketItem> items = new HashMap<>();

            for (String key : config.getKeys(false)) {
                if (key.equals("name")) continue; // Skip the display name key

                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;

                int position = section.getInt("position");
                int buyPrice = section.getInt("buy.price", -1);
                int sellPrice = section.getInt("sell.price", -1);
                
                // Updated: Convert String currency from YAML to Enum
                String currencyStr = section.getString("currency", "coin");
                CurrencyType currency = CurrencyType.fromName(currencyStr);
                if (currency == null) currency = CurrencyType.COIN; // Default fallback

                // CRITICAL FIX: Load the full ItemStack from metadata to preserve NBT/CrackShot data
                ItemStack stack = section.getItemStack("metadata");
                if (stack == null) {
                    // Fallback for legacy files or manual configs
                    Material material = Material.matchMaterial(section.getString("material", "STONE"));
                    int amount = section.getInt("item_amount", 1);
                    stack = new ItemStack(material != null ? material : Material.STONE, amount);
                }

                items.put(position, new MarketItem(stack, buyPrice, sellPrice, currency));
            }
            marketCache.put(marketName, items);
            plugin.getLogger().info("Loaded Market: " + marketName + " with " + items.size() + " items.");
        }
    }

    /**
     * Retrieves the map of items for a specific market.
     *
     * @param marketName The identifier of the market.
     * @return A Map where Key is the GUI slot and Value is the MarketItem, or null if not found.
     */
    public Map<Integer, MarketItem> getMarketItems(String marketName) {
        return marketCache.get(marketName);
    }

    /**
     * Handles the logic for buying an item from the market.
     * <p>
     * <b>Flow:</b>
     * <ol>
     * <li>Checks if the player has inventory space (Sync).</li>
     * <li>Deducts money from the player's account (Async).</li>
     * <li>If payment succeeds, gives a <b>clone</b> of the specific item to the player (Sync).</li>
     * </ol>
     * </p>
     *
     * @param player     The player attempting to buy.
     * @param marketName The name of the market.
     * @param slot       The slot index clicked in the GUI.
     */
    @Override
    public void buy(Player player, String marketName, int slot) {
        MarketItem itemData = getItem(marketName, slot);
        if (itemData == null || itemData.buyPrice < 0) return;

        String accountType = "PLAYER"; 
        
        // 1. Check Inventory Space (Sync)
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Inventory full!");
            return;
        }

        // 2. Process Payment (Async)
        // Updated: Pass CurrencyType Enum directly to MCEconomyProvider
        MCEconomyProvider.getInstance().minusCoin(player.getUniqueId().toString(), accountType, itemData.currency, itemData.buyPrice)
            .thenAccept(success -> {
                // 3. Give Item (Back to Sync Main Thread)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        // CRITICAL FIX: Give a CLONE of the stored item so they get the exact NBT
                        player.getInventory().addItem(itemData.itemStack.clone());
                        
                        String itemName = itemData.itemStack.hasItemMeta() && itemData.itemStack.getItemMeta().hasDisplayName()
                            ? itemData.itemStack.getItemMeta().getDisplayName()
                            : itemData.itemStack.getType().name();
                            
                        player.sendMessage(ChatColor.GREEN + "Bought " + itemData.itemStack.getAmount() + "x " + itemName + 
                            " for " + itemData.buyPrice + " " + itemData.currency.getName());
                    } else {
                        player.sendMessage(ChatColor.RED + "Insufficient funds!");
                    }
                });
            });
    }

    /**
     * Handles the logic for selling an item to the market.
     * <p>
     * <b>Flow:</b>
     * <ol>
     * <li>Checks if the player has the required item (Exact NBT match) (Sync).</li>
     * <li>Removes the item from the player's inventory (Sync).</li>
     * <li>Adds money to the player's account (Async).</li>
     * </ol>
     * </p>
     *
     * @param player     The player attempting to sell.
     * @param marketName The name of the market.
     * @param slot       The slot index clicked in the GUI.
     */
    @Override
    public void sell(Player player, String marketName, int slot) {
        MarketItem itemData = getItem(marketName, slot);
        if (itemData == null || itemData.sellPrice < 0) return;

        // 1. Check & Remove Item (Sync)
        // We use clone() to ensure we are checking against the authoritative item data
        ItemStack toRemove = itemData.itemStack.clone();
        
        // Checks for item presence including NBT data (except amount logic handled by toRemove.amount)
        if (!player.getInventory().containsAtLeast(toRemove, toRemove.getAmount())) {
            player.sendMessage(ChatColor.RED + "You don't have enough items to sell!");
            return;
        }

        player.getInventory().removeItem(toRemove);

        // 2. Give Money (Async)
        String accountType = "PLAYER";
        // Updated: Pass CurrencyType Enum directly to MCEconomyProvider
        MCEconomyProvider.getInstance().addCoin(player.getUniqueId().toString(), accountType, itemData.currency, itemData.sellPrice)
            .thenAccept(success -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GREEN + "Sold for " + itemData.sellPrice + " " + itemData.currency.getName());
                });
            });
    }

    /**
     * Helper method to retrieve a specific item from the cache.
     *
     * @param marketName The market identifier.
     * @param slot       The slot index.
     * @return The MarketItem object, or null if invalid.
     */
    private MarketItem getItem(String marketName, int slot) {
        if (!marketCache.containsKey(marketName)) return null;
        return marketCache.get(marketName).get(slot);
    }

    /**
     * Gets a set of all loaded market names.
     *
     * @return A set of strings representing valid market names.
     */
    public java.util.Set<String> getMarketNames() {
        return marketCache.keySet();
    }

    /**
     * A data record representing a single item listing in the market.
     *
     * @param itemStack The full ItemStack (with NBT/Meta) being sold.
     * @param buyPrice  The cost to buy this item (negative if unbuyable).
     * @param sellPrice The reward for selling this item (negative if unsellable).
     * @param currency  The currency type used for the transaction.
     */
    public record MarketItem(ItemStack itemStack, int buyPrice, int sellPrice, CurrencyType currency) {}
}
