package io.github.mcclauneck.market.listener;

import io.github.mcclauneck.market.command.MarketCommand;
import io.github.mcclauneck.market.common.MarketProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

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
     * <li>Checks if the inventory title matches the market format (Component-aware).</li>
     * <li>Parses the market name and page number.</li>
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
        Component titleComponent = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(titleComponent);
        
        // Robust check: 
        // 1. English Prefix "Market: "
        // 2. Thai Prefix "ตลาด: " (Common translation)
        // 3. Translatable Key "mcclauneck.market.gui.title"
        boolean isMarket = plainTitle.startsWith("Market: ") || plainTitle.startsWith("ตลาด: ");
        
        if (!isMarket && titleComponent instanceof TranslatableComponent tc) {
            if (tc.key().equals("mcclauneck.market.gui.title")) {
                isMarket = true;
            }
        }

        if (!isMarket) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // Ensure user clicked top inventory (Market), not their own bottom inventory
        if (event.getClickedInventory() == event.getView().getBottomInventory()) return;

        // Parse Title to get Market Name and Page
        String marketName = "unknown";
        int page = 1;

        if (titleComponent instanceof TranslatableComponent tc && tc.key().equals("mcclauneck.market.gui.title")) {
            // Args: [0]=Name, [1]=Page
            List<Component> args = tc.args();
            if (!args.isEmpty()) {
                marketName = PlainTextComponentSerializer.plainText().serialize(args.get(0));
            }
            if (args.size() >= 2) {
                try {
                    String pageStr = PlainTextComponentSerializer.plainText().serialize(args.get(1));
                    page = Integer.parseInt(pageStr);
                } catch (NumberFormatException ignored) {}
            }
        } else {
            // Fallback: Legacy String Parsing
            // Handle both "Market: name | P1" and "ตลาด: name | หน้า 1"
            String cleanTitle = plainTitle;
            if (cleanTitle.startsWith("Market: ")) cleanTitle = cleanTitle.replace("Market: ", "");
            else if (cleanTitle.startsWith("ตลาด: ")) cleanTitle = cleanTitle.replace("ตลาด: ", "");
            
            // Split by pipe
            String[] parts = cleanTitle.split(" \\| ");
            if (parts.length > 0) {
                marketName = parts[0];
            }
            
            if (parts.length > 1) {
                // Parse Page part: "P1" or "หน้า 1"
                String pagePart = parts[1].trim();
                // Strip non-digits
                String numStr = pagePart.replaceAll("[^0-9]", "");
                try {
                    page = Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {}
            }
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
