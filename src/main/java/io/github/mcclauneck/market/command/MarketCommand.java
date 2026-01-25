package io.github.mcclauneck.market.command;

import io.github.mcclauneck.market.common.MarketProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the execution of the /market command.
 * <p>
 * Commands:
 * <ul>
 * <li>/market [name] - Opens the specified market GUI.</li>
 * </ul>
 * </p>
 */
public class MarketCommand implements CommandExecutor {

    /**
     * Reference to the market data provider.
     */
    private final MarketProvider provider;

    /**
     * Constructs a new MarketCommand executor.
     *
     * @param provider The provider instance used to fetch market items.
     */
    public MarketCommand(MarketProvider provider) {
        this.provider = provider;
    }

    /**
     * Executes the command logic.
     *
     * @param sender  The source of the command.
     * @param command The command executed.
     * @param label   The alias used.
     * @param args    The command arguments.
     * @return true if valid, false otherwise.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Note: The "edit" subcommand logic is intercepted in Market.java
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /market <name>");
            return true;
        }

        String marketName = args[0];
        Map<Integer, MarketProvider.MarketItem> items = provider.getMarketItems(marketName);

        if (items == null) {
            player.sendMessage(ChatColor.RED + "Market '" + marketName + "' not found.");
            return true;
        }

        openMarketGui(player, marketName, items);
        return true;
    }

    /**
     * Creates and opens the market inventory GUI for the player.
     * <p>
     * This method clones the items from the provider and appends pricing information
     * to the lore for display purposes. This ensures the player sees the price
     * but receives the clean item upon purchase.
     * </p>
     *
     * @param player     The player to open the GUI for.
     * @param marketName The name of the market (used in the title).
     * @param items      The map of items to populate the GUI with.
     */
    private void openMarketGui(Player player, String marketName, Map<Integer, MarketProvider.MarketItem> items) {
        // Create inventory with 54 slots (Double Chest size)
        // Title format must match what MarketListener expects ("Market: " + name)
        Inventory gui = Bukkit.createInventory(null, 54, "Market: " + marketName);

        items.forEach((slot, itemData) -> {
            if (slot >= 0 && slot < 54) {
                // CRITICAL FIX: Clone the stored item so we don't modify the cache or the item given to player
                ItemStack displayItem = itemData.itemStack().clone();
                ItemMeta meta = displayItem.getItemMeta();
                
                if (meta != null) {
                    // Append Price Lore to existing lore
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    
                    lore.add(ChatColor.DARK_GRAY + "----------------");
                    
                    if (itemData.buyPrice() >= 0) {
                        lore.add(ChatColor.GREEN + "Buy: " + itemData.buyPrice() + " " + itemData.currency());
                    } else {
                        lore.add(ChatColor.RED + "Buy: N/A");
                    }
                    
                    if (itemData.sellPrice() >= 0) {
                        lore.add(ChatColor.AQUA + "Sell: " + itemData.sellPrice() + " " + itemData.currency());
                    } else {
                        lore.add(ChatColor.RED + "Sell: N/A");
                    }
                    
                    lore.add(ChatColor.YELLOW + "Left-Click to Buy | Right-Click to Sell");
                    
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                
                gui.setItem(slot, displayItem);
            }
        });

        player.openInventory(gui);
    }
}
