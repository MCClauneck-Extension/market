package io.github.mcclauneck.market;

import io.github.mcclauneck.market.command.MarketCommand;
import io.github.mcclauneck.market.common.MarketProvider;
import io.github.mcclauneck.market.editor.MarketEditor;
import io.github.mcclauneck.market.editor.util.EditorUtil;
import io.github.mcclauneck.market.listener.MarketListener;
import io.github.mcclauneck.market.tabcompleter.MarketTabCompleter;
import io.github.mcengine.mcextension.api.IMCExtension;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
 * initializing the logic provider, and registering event listeners and commands.
 * </p>
 */
public class Market implements IMCExtension {

    /**
     * The provider instance that holds the market logic and cache.
     */
    private MarketProvider provider;
    
    /**
     * The editor instance for managing admin GUIs.
     */
    private MarketEditor editor;

    /**
     * Called when the extension is loaded by the MCEconomy core.
     * <p>
     * This method performs the following setup:
     * <ol>
     * <li>Ensures the extension data directory exists.</li>
     * <li>Creates the example 'ore.yml' file if it doesn't exist.</li>
     * <li>Initializes the {@link MarketProvider} and {@link MarketEditor}.</li>
     * <li>Registers the {@link MarketListener} with Bukkit's PluginManager.</li>
     * <li>Registers the {@link MarketCommand} and {@link MarketTabCompleter} via Reflection to handle subcommands like 'create' and 'edit'.</li>
     * </ol>
     * </p>
     *
     * @param plugin   The host plugin (MCEconomy) instance.
     * @param executor The async executor provided by the host.
     */
    @Override
    public void onLoad(JavaPlugin plugin, Executor executor) {
        // 1. Setup Data Folder
        File marketFolder = new File(plugin.getDataFolder(), "extensions/configs/Market");
        if (!marketFolder.exists()) {
            marketFolder.mkdirs();
        }

        // 2. Create Example File
        createExampleFile(marketFolder, plugin);

        // 3. Initialize Provider & Editor
        // We pass 'plugin' because we need to run Sync tasks (Give Item) after Async tasks (Economy)
        this.provider = new MarketProvider(plugin, marketFolder);
        this.editor = new MarketEditor(plugin, this.provider, marketFolder);

        MarketCommand marketExecutor = new MarketCommand(provider);

        // 4. Register Listeners
        plugin.getServer().getPluginManager().registerEvents(new MarketListener(provider, marketExecutor), plugin);
        plugin.getServer().getPluginManager().registerEvents(editor, plugin);

        // 5. Register Command & TabCompleter (Runtime Reflection)
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            MarketTabCompleter marketTabCompleter = new MarketTabCompleter(provider);

            // Create a dynamic Command object that intercepts 'create' and 'edit' subcommands
            Command cmd = new Command("market", "Open the market GUI", "/market <name> [page] | /market create <name> | /market edit <name>", Collections.emptyList()) {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    
                    if (args.length >= 1) {
                        // Handle "create" subcommand
                        if (args[0].equalsIgnoreCase("create")) {
                             if (!(sender instanceof Player player)) {
                                sender.sendMessage(Component.translatable("mcclauneck.market.command.only_players", NamedTextColor.RED));
                                return true;
                            }
                            if (!player.hasPermission("market.admin")) {
                                player.sendMessage(Component.translatable("mcclauneck.market.command.permission_denied", NamedTextColor.RED));
                                return true;
                            }
                            if (args.length < 2) {
                                player.sendMessage(Component.translatable("mcclauneck.market.command.usage_create", NamedTextColor.RED));
                                return true;
                            }
                            
                            boolean created = provider.createMarket(args[1]);
                            if (created) {
                                player.sendMessage(Component.translatable("mcclauneck.market.command.create_success", NamedTextColor.GREEN,
                                    Component.text(args[1])));
                                editor.openEditor(player, args[1]);
                            } else {
                                player.sendMessage(Component.translatable("mcclauneck.market.command.create_exists", NamedTextColor.RED,
                                    Component.text(args[1])));
                            }
                            return true;
                        }

                        // Handle "edit" subcommand
                        if (args[0].equalsIgnoreCase("edit")) {
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(Component.translatable("mcclauneck.market.command.only_players", NamedTextColor.RED));
                                return true;
                            }
                            if (!player.hasPermission("market.admin")) {
                                player.sendMessage(Component.translatable("mcclauneck.market.command.permission_denied", NamedTextColor.RED));
                                return true;
                            }
                            if (args.length < 2) {
                                player.sendMessage(Component.translatable("mcclauneck.market.command.usage_edit", NamedTextColor.RED));
                                return true;
                            }
                            int page = 1;
                            if (args.length >= 3) {
                                try { page = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                            }
                            editor.openEditor(player, args[1], page);
                            return true;
                        }
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
        this.provider = null;
        this.editor = null;
    }

    /**
     * Generates a default 'ore.yml' file if no markets exist.
     * <p>
     * Uses the YAML-to-Base64 method to ensure the example file is compatible.
     * </p>
     *
     * @param marketFolder The directory to save the file in.
     * @param plugin       The plugin instance for logging.
     */
    private void createExampleFile(File marketFolder, JavaPlugin plugin) {
        File oreFile = new File(marketFolder, "ore.yml");
        if (oreFile.exists()) return;

        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("name", "Ore Market");

            // Item 1: Iron Ore
            ItemStack ironOre = new ItemStack(Material.IRON_ORE);
            String b64Ore = EditorUtil.itemStackToBase64(ironOre);
            
            config.set("items.1.buy.price", 50);
            config.set("items.1.sell.price", 10);
            config.set("items.1.currency", "coin");
            config.set("items.1.amount", 1);
            config.set("items.1.metadata", b64Ore);

            // Item 2: Iron Block
            ItemStack ironBlock = new ItemStack(Material.IRON_BLOCK);
            String b64Block = EditorUtil.itemStackToBase64(ironBlock);

            config.set("items.2.buy.price", 450);
            config.set("items.2.sell.price", 90);
            config.set("items.2.currency", "coin");
            config.set("items.2.amount", 1);
            config.set("items.2.metadata", b64Block);

            config.save(oreFile);
            plugin.getLogger().info("Created example market file: ore.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create example market file: " + e.getMessage());
        }
    }
}
