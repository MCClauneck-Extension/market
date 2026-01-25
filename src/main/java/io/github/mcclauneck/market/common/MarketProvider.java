package io.github.mcclauneck.market.common;

import io.github.mcclauneck.market.api.IMarket;
import io.github.mcengine.mceconomy.common.MCEconomyProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

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
     * Loads (or reloads) all .yml configuration files from the Market folder into memory.
     * <p>
     * This method iterates through files in {@link #marketFolder}, parses item data
     * (material, price, currency, position), and populates the {@link #marketCache}.
     * </p>
     */
    public void loadMarkets() {
        if (!marketFolder.exists()) return;

        for (File file : Objects.requireNonNull(marketFolder.listFiles((dir, name) -> name.endsWith(".yml")))) {
            String marketName = file.getName().replace(".yml", "");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Map<Integer, MarketItem> items = new HashMap<>();

            for (String key : config.getKeys(false)) {
                if (key.equals("name")) continue; // Skip the display name key

                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;

                int position = section.getInt("position");
                Material material = Material.matchMaterial(section.getString("material", "STONE"));
                int amount = section.getInt("item_amount", 1);
                int buyPrice = section.getInt("buy.price", -1);
                int sellPrice = section.getInt("sell.price", -1);
                String currency = section.getString("currency", "coin");

                items.put(position, new MarketItem(material, amount, buyPrice, sellPrice, currency));
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
     * <li>If payment succeeds, gives the item to the player (Sync).</li>
     * </ol>
     * </p>
     *
     * @param player     The player attempting to buy.
     * @param marketName The name of the market.
     * @param slot       The slot index clicked in the GUI.
     */
    @Override
    public void buy(Player player, String marketName, int slot) {
        MarketItem item = getItem(marketName, slot);
        if (item == null || item.buyPrice < 0) return;

        String accountType = "PLAYER"; 
        
        // 1. Check Inventory Space (Sync)
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Inventory full!");
            return;
        }

        // 2. Process Payment (Async)
        MCEconomyProvider.getInstance().minusCoin(player.getUniqueId().toString(), accountType, item.currency, item.buyPrice)
            .thenAccept(success -> {
                // 3. Give Item (Back to Sync Main Thread)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.getInventory().addItem(new ItemStack(item.material, item.amount));
                        player.sendMessage(ChatColor.GREEN + "Bought " + item.amount + "x " + item.material + " for " + item.buyPrice + " " + item.currency);
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
     * <li>Checks if the player has the required item (Sync).</li>
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
        MarketItem item = getItem(marketName, slot);
        if (item == null || item.sellPrice < 0) return;

        // 1. Check & Remove Item (Sync)
        ItemStack toRemove = new ItemStack(item.material, item.amount);
        if (!player.getInventory().containsAtLeast(toRemove, item.amount)) {
            player.sendMessage(ChatColor.RED + "You don't have enough items to sell!");
            return;
        }

        player.getInventory().removeItem(toRemove);

        // 2. Give Money (Async)
        String accountType = "PLAYER";
        MCEconomyProvider.getInstance().addCoin(player.getUniqueId().toString(), accountType, item.currency, item.sellPrice)
            .thenAccept(success -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GREEN + "Sold for " + item.sellPrice + " " + item.currency);
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
     * @return A set of strings representing valid market names.
     */
    public java.util.Set<String> getMarketNames() {
        return marketCache.keySet();
    }

    /**
     * A simple data record representing a single item in the market.
     *
     * @param material  The Bukkit Material of the item.
     * @param amount    The stack size (amount) of the item.
     * @param buyPrice  The cost to buy this item (negative if unbuyable).
     * @param sellPrice The reward for selling this item (negative if unsellable).
     * @param currency  The currency type used for the transaction.
     */
    public record MarketItem(Material material, int amount, int buyPrice, int sellPrice, String currency) {}
}
