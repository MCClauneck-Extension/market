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
        // We only want to complete the first argument (market name)
        if (args.length == 1) {
            List<String> markets = new ArrayList<>(provider.getMarketNames());
            List<String> completions = new ArrayList<>();
            
            // Filter list based on what the user has already typed
            StringUtil.copyPartialMatches(args[0], markets, completions);
            
            // Sort for better UX
            Collections.sort(completions);
            return completions;
        }
        
        return Collections.emptyList();
    }
}
