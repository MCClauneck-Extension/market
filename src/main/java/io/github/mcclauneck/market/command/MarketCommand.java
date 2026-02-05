package io.github.mcclauneck.market.command;

import io.github.mcclauneck.market.common.MarketProvider;
import io.github.mcclauneck.market.editor.util.EditorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * <li>/market [name] [page] - Opens the specified market GUI at the given page.</li>
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
            sender.sendMessage(Component.translatable("mcclauneck.market.command.only_players", NamedTextColor.RED));
            return true;
        }

        // Note: The "edit" and "create" subcommand logic is intercepted in Market.java
        if (args.length == 0) {
            player.sendMessage(Component.translatable("mcclauneck.market.command.usage", NamedTextColor.RED));
            return true;
        }

        String marketName = args[0];
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }
        if (page < 1) page = 1;

        Map<Integer, MarketProvider.MarketItem> items = provider.getMarketItems(marketName);

        if (items == null) {
            player.sendMessage(Component.translatable("mcclauneck.market.not_found", NamedTextColor.RED,
                Component.text(marketName)));
            return true;
        }

        openMarketGui(player, marketName, items, page);
        return true;
    }

    /**
     * Creates and opens the market inventory GUI for the player.
     * <p>
     * This method clones the items from the provider and appends pricing information
     * to the lore for display purposes. It also calculates and renders pagination controls.
     * </p>
     *
     * @param player     The player to open the GUI for.
     * @param marketName The name of the market (used in the title).
     * @param items      The map of items to populate the GUI with.
     * @param page       The current page number.
     */
    public void openMarketGui(Player player, String marketName, Map<Integer, MarketProvider.MarketItem> items, int page) {
        // Create inventory with 54 slots (Double Chest size)
        // Title format must match what MarketListener expects ("Market: " + name + " | P" + page)
        // Note: Listener check updated to handle component-based logic or string parsing
        Inventory gui = Bukkit.createInventory(null, 54, Component.translatable("mcclauneck.market.gui.title",
            Component.text(marketName),
            Component.text(page)));

        int itemsPerPage = 45;
        int startKey = (page - 1) * itemsPerPage + 1;

        // Calculate Max Key to determine if a "Next Page" button is needed
        int maxKey = 0;
        for (int k : items.keySet()) {
            if (k > maxKey) maxKey = k;
        }

        for (int i = 0; i < itemsPerPage; i++) {
            int currentKey = startKey + i;
            MarketProvider.MarketItem data = items.get(currentKey);

            if (data != null) {
                // CRITICAL: Clone the stored item so we don't modify the cache or the item given to player
                ItemStack displayItem = data.itemStack().clone();
                ItemMeta meta = displayItem.getItemMeta();
                
                if (meta != null) {
                    // Append Price Lore to existing lore
                    List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                    
                    lore.add(Component.text("----------------", NamedTextColor.DARK_GRAY));
                    
                    if (data.buyPrice() >= 0) {
                        lore.add(Component.translatable("mcclauneck.market.item.buy", NamedTextColor.GREEN,
                            Component.text(data.buyPrice()),
                            Component.text(data.currency().getName())));
                    } else {
                        lore.add(Component.translatable("mcclauneck.market.item.buy_na", NamedTextColor.RED));
                    }

                    if (data.sellPrice() >= 0) {
                        lore.add(Component.translatable("mcclauneck.market.item.sell", NamedTextColor.AQUA,
                            Component.text(data.sellPrice()),
                            Component.text(data.currency().getName())));
                    } else {
                        lore.add(Component.translatable("mcclauneck.market.item.sell_na", NamedTextColor.RED));
                    }
                    
                    lore.add(Component.translatable("mcclauneck.market.item.click_hint", NamedTextColor.YELLOW));
                    
                    meta.lore(lore);
                    displayItem.setItemMeta(meta);
                }
                
                gui.setItem(i, displayItem);
            }
        }

        // GUI Glass Filler for Bottom Row
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.displayName(Component.empty());
        glass.setItemMeta(gMeta);

        for (int i = 45; i < 54; i++) gui.setItem(i, glass);

        // Navigation Buttons
        if (page > 1) {
            gui.setItem(45, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGNlYzgwN2RjYzE0MzYzMzRmZDRkYzlhYjM0OTM0MmY2YzUyYzllN2IyYmYzNDY3MTJkYjcyYTBkNmQ3YTQifX19", 
                Component.translatable("mcclauneck.market.gui.previous_page")));
        }
        
        boolean pageFull = (gui.getItem(44) != null);
        if (maxKey > (page * itemsPerPage) || pageFull) {
             gui.setItem(53, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTAxYzdiNTcyNjE3ODk3NGIzYjNhMDFiNDJhNTkwZTU0MzY2MDI2ZmQ0MzgwOGYyYTc4NzY0ODg0M2E3ZjVhIn19fQ==", 
                Component.translatable("mcclauneck.market.gui.next_page")));
        }

        player.openInventory(gui);
    }
}
