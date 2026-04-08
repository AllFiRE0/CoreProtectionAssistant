package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;

import java.util.ArrayList;
import java.util.List;

public class ResetStatsCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    private final List<String> VALID_TYPES = List.of(
        "commands", "all", "ban", "mute", "kick", "give", "gm", "rating", "warn", "free"
    );
    
    public ResetStatsCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "resetstats";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"reset", "clearstats"};
    }
    
    @Override
    public String getDescription() {
        return "Reset player statistics";
    }
    
    @Override
    public String getUsage() {
        return "/cpa resetstats <player> <commands/all/ban/mute/kick/give/gm/rating/warn/free>";
    }
    
    @Override
    public String getPermission() {
        return "cpa.staff";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.colorize("&cUsage: " + getUsage()));
            sender.sendMessage(Lang.colorize("&7Types: " + String.join(", ", VALID_TYPES)));
            return true;
        }
        
        String targetName = args[0];
        String type = args[1].toLowerCase();
        
        if (!VALID_TYPES.contains(type)) {
            sender.sendMessage(Lang.colorize("&cInvalid type. Available: " + String.join(", ", VALID_TYPES)));
            return true;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Lang.colorize("&cPlayer not found: " + targetName));
            return true;
        }
        
        plugin.getDatabaseManager().resetPlayerStats(target.getUniqueId(), type);
        
        sender.sendMessage(Lang.colorize("&aReset " + type + " statistics for " + target.getName()));
        plugin.getLogger().info(sender.getName() + " reset " + type + " stats for " + target.getName());
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    players.add(p.getName());
                }
            }
            int count = 0;
            for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
                if (count >= 20) break;
                String name = off.getName();
                if (name != null && name.toLowerCase().startsWith(args[0].toLowerCase())) {
                    if (!players.contains(name)) {
                        players.add(name);
                        count++;
                    }
                }
            }
            return players;
        }
        if (args.length == 2) {
            return VALID_TYPES.stream()
                .filter(t -> t.startsWith(args[1].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
