package io.github.mcclauneck.market.api;

import org.bukkit.entity.Player;

/**
 * Defines the contract for Market operations.
 */
public interface IMarket {

    /**
     * Initiates a buy transaction for a player.
     *
     * @param player     The player attempting to buy.
     * @param marketName The name of the market file (e.g., "ore").
     * @param slot       The GUI slot index where the item is located.
     */
    void buy(Player player, String marketName, int slot);

    /**
     * Initiates a sell transaction for a player.
     *
     * @param player     The player attempting to sell.
     * @param marketName The name of the market file (e.g., "ore").
     * @param slot       The GUI slot index where the item is located.
     */
    void sell(Player player, String marketName, int slot);
}
