package io.github.mcclauneck.market.listener;

import io.github.mcclauneck.market.command.MarketCommand;
import io.github.mcclauneck.market.common.MarketProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles Bukkit inventory events to facilitate Market interactions.
 * <p>
 * This listener detects clicks in inventories with the "Market: " prefix,
 * handles pagination navigation, cancels the default click behavior (to prevent stealing GUI items),
 * and delegates buy/sell actions to the {@link MarketProvider}.
 * </p>
 */
public class MarketListener implements Listener {

    /**
     * Reference to the logic provider.
     */
    private final MarketProvider provider;
    
    /**
     * Reference to the command executor for re-opening GUIs (navigation).
     */
    private final MarketCommand commandExecutor;

    /**
     * Constructs a new MarketListener.
     *
     * @param provider        The provider instance to handle buy/sell logic.
     * @param commandExecutor The command executor to handle page switching.
     */
    public MarketListener(MarketProvider provider, MarketCommand commandExecutor) {
        this.provider = provider;
        this.commandExecutor = commandExecutor;
    }

    /**
     * Intercepts inventory clicks.
     * <p>
     * <b>Logic:</b>
     * <ul>
     * <li>Checks if the inventory title starts with "Market: ".</li>
     * <li>Parses the market name and page number from the title.</li>
     * <li>Cancels the event to protect GUI items.</li>
     * <li>Handles pagination button clicks (Previous/Next Page).</li>
     * <li>Left Click -> Triggers Buy.</li>
     * <li>Right Click -> Triggers Sell.</li>
     * </ul>
     * </p>
     *
     * @param event The InventoryClickEvent fired by Bukkit.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith("Market: ")) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // Ensure user clicked top inventory (Market), not their own bottom inventory
        if (event.getClickedInventory() == event.getView().getBottomInventory()) return;

        // Parse Title: "Market: ore | P1"
        String[] parts = title.replace("Market: ", "").split(" \\| P");
        String marketName = parts[0];
        int page = 1;
        if (parts.length > 1) {
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }

        int slot = event.getSlot();

        // Handle Pagination clicks (Slots 45-53)
        if (slot >= 45 && slot <= 53) {
            if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                // Prev Page
                commandExecutor.openMarketGui(player, marketName, provider.getMarketItems(marketName), page - 1);
            } else if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                // Next Page
                commandExecutor.openMarketGui(player, marketName, provider.getMarketItems(marketName), page + 1);
            }
            return;
        }

        // Handle Buy/Sell
        if (event.getClick() == ClickType.LEFT) {
            provider.buy(player, marketName, page, slot);
        } else if (event.getClick() == ClickType.RIGHT) {
            provider.sell(player, marketName, page, slot);
        }
    }
}
