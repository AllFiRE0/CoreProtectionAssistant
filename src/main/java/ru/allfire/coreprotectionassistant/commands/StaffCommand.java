package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.managers.StaffManager;
import ru.allfire.coreprotectionassistant.utils.Color;

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
            sender.sendMessage(Color.colorize("&cUsage: " + getUsage()));
            return true;
        }
        
        String targetName = args[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            sender.sendMessage(Color.colorize(
                plugin.getConfigManager().getLangConfig().getString("messages.player_not_found",
                    "%prefix% &cPlayer not found")
                    .replace("%player%", targetName)
            ));
            return true;
        }
        
        UUID targetUuid = offlineTarget.getUniqueId();
        
        sender.sendMessage(Color.colorize(
            plugin.getConfigManager().getLangConfig().getString("messages.staff_header",
                "&8&m-----&r &cStaff: &f%player% &8&m-----")
                .replace("%player%", offlineTarget.getName())
        ));
        
        StaffManager.StaffStats stats = plugin.getStaffManager().getStaffStats(targetUuid);
        
        sendMessage(sender, "staff_bans", stats.bansCount);
        sendMessage(sender, "staff_mutes", stats.mutesCount);
        sendMessage(sender, "staff_kicks", stats.kicksCount);
        sendMessage(sender, "staff_gives", stats.givesCount);
        sendMessage(sender, "staff_gamemode_changes", stats.gamemodeChanges);
        
        // Abuse score
        int abuseScore = plugin.getAbuseScoreManager().getScore(targetUuid);
        String scoreColor = abuseScore >= 50 ? "&c" : (abuseScore >= 30 ? "&e" : "&a");
        sender.sendMessage(Color.colorize(
            plugin.getConfigManager().getLangConfig().getString("messages.staff_abuse_score",
                "&7Abuse score: " + scoreColor + "%value%%")
                .replace("%value%", String.valueOf(abuseScore))
        ));
        
        // Активные предупреждения
        plugin.getWarnManager().getActiveWarningsCount(targetUuid).thenAccept(count -> {
            sendMessage(sender, "staff_warnings_count", count);
        });
        
        return true;
    }
    
    private void sendMessage(CommandSender sender, String key, Object value) {
        String message = plugin.getConfigManager().getLangConfig()
            .getString("messages." + key, "&7" + key + ": &f%value%")
            .replace("%value%", String.valueOf(value));
        sender.sendMessage(Color.colorize(message));
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
