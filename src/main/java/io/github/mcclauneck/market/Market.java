package io.github.mcclauneck.market;

import io.github.mcclauneck.market.common.MarketProvider;
import io.github.mcclauneck.market.listener.MarketListener;
import io.github.mcengine.mcextension.api.IMCExtension;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * The main entry point for the Market extension.
 * <p>
 * This class implements {@link IMCExtension}, allowing it to be loaded dynamically
 * by the MCEconomy core plugin. It is responsible for setting up the data folder,
 * initializing the logic provider, and registering event listeners.
 * </p>
 */
public class Market implements IMCExtension {

    /**
     * The provider instance that holds the market logic and cache.
     */
    private MarketProvider provider;

    /**
     * Called when the extension is loaded by the MCEconomy core.
     * <p>
     * This method performs the following setup:
     * <ol>
     * <li>Ensures the extension data directory exists.</li>
     * <li>Initializes the {@link MarketProvider} with the plugin instance and data folder.</li>
     * <li>Registers the {@link MarketListener} with Bukkit's PluginManager.</li>
     * </ol>
     * </p>
     *
     * @param plugin   The host plugin (MCEconomy) instance.
     * @param executor The async executor provided by the host.
     */
    @Override
    public void onLoad(JavaPlugin plugin, Executor executor) {
        // 1. Setup Data Folder
        File marketFolder = new File(plugin.getDataFolder(), "extensions/Market");
        if (!marketFolder.exists()) {
            marketFolder.mkdirs();
        }

        // 2. Initialize Provider
        // We pass 'plugin' because we need to run Sync tasks (Give Item) after Async tasks (Economy)
        this.provider = new MarketProvider(plugin, marketFolder);

        // 3. Register Listener
        plugin.getServer().getPluginManager().registerEvents(new MarketListener(provider), plugin);
        
        plugin.getLogger().info("Market Extension Loaded Successfully.");
    }

    /**
     * Called when the extension is disabled or unloaded.
     * <p>
     * Cleans up resources and references to prevent memory leaks.
     * </p>
     *
     * @param plugin   The host plugin instance.
     * @param executor The async executor provided by the host.
     */
    @Override
    public void onDisable(JavaPlugin plugin, Executor executor) {
        // Cleanup if needed
        this.provider = null;
    }
}
