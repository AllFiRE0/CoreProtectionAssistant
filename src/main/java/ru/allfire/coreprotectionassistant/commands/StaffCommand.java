package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;
import ru.allfire.coreprotectionassistant.managers.StaffManager;

import java.util.List;
import java.util.UUID;

public class StaffCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    
    public StaffCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "staff";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"moder", "admin"};
    }
    
    @Override
    public String getDescription() {
        return "View staff member statistics";
    }
    
    @Override
    public String getUsage() {
        return "/cpa staff <player>";
    }
    
    @Override
    public String getPermission() {
        return "cpa.staff";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "staff_usage");
            return true;
        }
        
        String targetName = args[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            Lang.send(sender, "player_not_found", "player", targetName);
            return true;
        }
        
        UUID targetUuid = offlineTarget.getUniqueId();
        
        Lang.send(sender, "staff_header", "player", offlineTarget.getName());
        
        StaffManager.StaffStats stats = plugin.getStaffManager().getStaffStats(targetUuid);
        
        Lang.send(sender, "staff_bans", "value", String.valueOf(stats.bansCount));
        Lang.send(sender, "staff_mutes", "value", String.valueOf(stats.mutesCount));
        Lang.send(sender, "staff_kicks", "value", String.valueOf(stats.kicksCount));
        Lang.send(sender, "staff_gives", "value", String.valueOf(stats.givesCount));
        Lang.send(sender, "staff_gamemode_changes", "value", String.valueOf(stats.gamemodeChanges));
        
        int abuseScore = plugin.getAbuseScoreManager().getScore(targetUuid);
        String scoreColor = abuseScore >= 50 ? "&c" : (abuseScore >= 30 ? "&e" : "&a");
        String message = Lang.get("staff_abuse_score")
            .replace("%value%", scoreColor + abuseScore + "%");
        if (!message.isEmpty()) {
            sender.sendMessage(Lang.colorize(message));
        }
        
        plugin.getWarnManager().getActiveWarningsCount(targetUuid).thenAccept(count -> {
            Lang.send(sender, "staff_warnings_count", "value", String.valueOf(count));
        });
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}