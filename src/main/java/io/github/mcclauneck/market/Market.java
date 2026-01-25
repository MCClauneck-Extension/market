package io.github.mcclauneck.market;

import io.github.mcclauneck.market.command.MarketCommand;
import io.github.mcclauneck.market.common.MarketProvider;
import io.github.mcclauneck.market.listener.MarketListener;
import io.github.mcengine.mcextension.api.IMCExtension;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
     * <li>Creates the example 'ore.yml' file if it doesn't exist.</li>
     * <li>Initializes the {@link MarketProvider} with the plugin instance and data folder.</li>
     * <li>Registers the {@link MarketListener} with Bukkit's PluginManager.</li>
     * <li>Registers the {@link MarketCommand} to handle player commands.</li>
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

        // 2. Create Example File
        createExampleFile(marketFolder, plugin);

        // 3. Initialize Provider
        // We pass 'plugin' because we need to run Sync tasks (Give Item) after Async tasks (Economy)
        this.provider = new MarketProvider(plugin, marketFolder);

        // 4. Register Listener
        plugin.getServer().getPluginManager().registerEvents(new MarketListener(provider), plugin);

        // 5. Register Command
        // Note: Ensure "market" is defined in MCEconomy's plugin.yml or registered dynamically
        if (plugin.getCommand("market") != null) {
            plugin.getCommand("market").setExecutor(new MarketCommand(provider));
        } else {
            plugin.getLogger().warning("Could not register '/market' command. Is it defined in plugin.yml?");
        }
        
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

    /**
     * Generates a default 'ore.yml' file if no markets exist.
     * <p>
     * This provides a template for server administrators to understand how to configure
     * new markets.
     * </p>
     *
     * @param marketFolder The directory to save the file in.
     * @param plugin       The plugin instance for logging.
     */
    private void createExampleFile(File marketFolder, JavaPlugin plugin) {
        File oreFile = new File(marketFolder, "ore.yml");
        if (oreFile.exists()) return;

        try (FileWriter writer = new FileWriter(oreFile)) {
            String content = """
                    name: Ore Market
                    # Item Key (Can be any unique number or string)
                    1:
                      # GUI Slot (0 - 53)
                      position: 0
                      # Valid Bukkit Material Name
                      material: IRON_ORE
                      # Stack size
                      item_amount: 1
                      buy:
                        # Price to buy from server (Set -1 to disable buying)
                        price: 50
                      sell:
                        # Price to sell to server (Set -1 to disable selling)
                        price: 10
                      # Currency ID defined in MCEconomy
                      currency: coin
                    
                    # Example 2: Block of Iron (Bulk)
                    2:
                      position: 1
                      material: IRON_BLOCK
                      item_amount: 1
                      buy:
                        price: 450
                      sell:
                        price: 90
                      currency: coin
                    """;
            writer.write(content);
            plugin.getLogger().info("Created example market file: ore.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create example market file: " + e.getMessage());
        }
    }
}
