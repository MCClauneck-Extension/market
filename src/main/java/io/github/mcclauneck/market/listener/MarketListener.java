package io.github.mcclauneck.market.listener;

import io.github.mcclauneck.market.common.MarketProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles Bukkit inventory events to facilitate Market interactions.
 * <p>
 * This listener detects clicks in inventories with a specific title prefix ("Market: "),
 * cancels the default click behavior (to prevent stealing GUI items), and delegates
 * the action to the {@link MarketProvider}.
 * </p>
 */
public class MarketListener implements Listener {

    /**
     * Reference to the logic provider.
     */
    private final MarketProvider provider;

    /**
     * The prefix used to identify Market GUIs.
     * <p>Example: A GUI titled "Market: ore" matches this prefix.</p>
     */
    private final String GUI_PREFIX = "Market: "; // Assumed GUI Title format

    /**
     * Constructs a new MarketListener.
     *
     * @param provider The provider instance to handle buy/sell logic.
     */
    public MarketListener(MarketProvider provider) {
        this.provider = provider;
    }

    /**
     * Intercepts inventory clicks.
     * <p>
     * <b>Logic:</b>
     * <ul>
     * <li>Checks if the inventory title starts with "Market: ".</li>
     * <li>Cancels the event to protect GUI items.</li>
     * <li>Prevents interaction with the player's own inventory (bottom inventory).</li>
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
        if (!title.startsWith(GUI_PREFIX)) return;

        event.setCancelled(true); // Prevent taking items out of the GUI

        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // Ensure user clicked top inventory (Market), not their own bottom inventory
        if (event.getClickedInventory() == event.getView().getBottomInventory()) return;

        String marketName = title.replace(GUI_PREFIX, ""); // Extract "ore" from "Market: ore"
        int slot = event.getSlot();

        if (event.getClick() == ClickType.LEFT) {
            provider.buy(player, marketName, slot);
        } else if (event.getClick() == ClickType.RIGHT) {
            provider.sell(player, marketName, slot);
        }
    }
}
