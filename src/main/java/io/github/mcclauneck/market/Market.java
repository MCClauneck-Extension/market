package io.github.mcclauneck.market;

import io.github.mcclauneck.market.command.MarketCommand;
import io.github.mcclauneck.market.common.MarketProvider;
import io.github.mcclauneck.market.editor.MarketEditor;
import io.github.mcclauneck.market.listener.MarketListener;
import io.github.mcclauneck.market.tabcompleter.MarketTabCompleter;
import io.github.mcengine.mcextension.api.IMCExtension;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
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
    private MarketEditor editor;

    /**
     * Called when the extension is loaded by the MCEconomy core.
     * <p>
     * This method performs the following setup:
     * <ol>
     * <li>Ensures the extension data directory exists.</li>
     * <li>Creates the example 'ore.yml' file if it doesn't exist.</li>
     * <li>Initializes the {@link MarketProvider} with the plugin instance and data folder.</li>
     * <li>Registers the {@link MarketListener} with Bukkit's PluginManager.</li>
     * <li>Registers the {@link MarketCommand} and {@link MarketTabCompleter} via Reflection.</li>
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

        // 3. Initialize Provider & Editor
        // We pass 'plugin' because we need to run Sync tasks (Give Item) after Async tasks (Economy)
        this.provider = new MarketProvider(plugin, marketFolder);
        this.editor = new MarketEditor(plugin, marketFolder);

        // 4. Register Listeners
        plugin.getServer().getPluginManager().registerEvents(new MarketListener(provider), plugin);
        plugin.getServer().getPluginManager().registerEvents(editor, plugin);

        // 5. Register Command & TabCompleter (Runtime Reflection)
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            MarketCommand marketExecutor = new MarketCommand(provider);
            MarketTabCompleter marketTabCompleter = new MarketTabCompleter(provider);

            // Create a dynamic Command object
            Command cmd = new Command("market", "Open the market GUI", "/market <name> | /market edit <name>", Collections.emptyList()) {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    // Intercept "edit" subcommand
                    if (args.length >= 1 && args[0].equalsIgnoreCase("edit")) {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                            return true;
                        }
                        if (!player.hasPermission("market.admin")) {
                            player.sendMessage(ChatColor.RED + "No permission.");
                            return true;
                        }
                        if (args.length < 2) {
                            player.sendMessage(ChatColor.RED + "Usage: /market edit <name>");
                            return true;
                        }
                        editor.openEditor(player, args[1]);
                        return true;
                    }
                    
                    // Fallback to standard open command
                    return marketExecutor.onCommand(sender, this, commandLabel, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                    return marketTabCompleter.onTabComplete(sender, this, alias, args);
                }
            };

            // Register with the plugin's name as the fallback prefix
            commandMap.register(plugin.getName(), cmd);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register /market command: " + e.getMessage());
            e.printStackTrace();
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
        this.editor = null;
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
