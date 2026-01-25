package io.github.mcclauneck.market.tabcompleter;

import io.github.mcclauneck.market.common.MarketProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles tab completion for the /market command.
 */
public class MarketTabCompleter implements TabCompleter {

    private final MarketProvider provider;

    public MarketTabCompleter(MarketProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> markets = new ArrayList<>(provider.getMarketNames());

        if (args.length == 1) {
            // Suggest market names AND the "edit" keyword
            List<String> suggestions = new ArrayList<>(markets);
            if (sender.hasPermission("market.admin")) {
                suggestions.add("edit");
            }
            StringUtil.copyPartialMatches(args[0], suggestions, completions);
        } 
        else if (args.length == 2) {
            // If first arg was "edit", suggest markets again for the second arg
            if (args[0].equalsIgnoreCase("edit")) {
                StringUtil.copyPartialMatches(args[1], markets, completions);
            }
        }
        
        Collections.sort(completions);
        return completions;
    }
}
