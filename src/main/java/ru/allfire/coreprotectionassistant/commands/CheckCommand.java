package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class CheckCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    
    public CheckCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "check";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"scan", "inspect"};
    }
    
    @Override
    public String getDescription() {
        return "Quick player check";
    }
    
    @Override
    public String getUsage() {
        return "/cpa check <player>";
    }
    
    @Override
    public String getPermission() {
        return "cpa.moder";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "check_usage");
            return true;
        }
        
        String targetName = args[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            Lang.send(sender, "player_not_found", "player", targetName);
            return true;
        }
        
        UUID targetUuid = offlineTarget.getUniqueId();
        
        Lang.send(sender, "check_header", "player", offlineTarget.getName());
        
        // Предупреждения
        plugin.getWarnManager().getActiveWarningsCount(targetUuid).thenAccept(count -> {
            String color = count >= 3 ? "&c" : (count >= 1 ? "&e" : "&a");
            String message = Lang.get("check_warnings")
                .replace("%color%", color)
                .replace("%value%", String.valueOf(count));
            if (!message.isEmpty()) {
                sender.sendMessage(Lang.colorize(message));
            }
        });
        
        // Нарушения чата
        plugin.getDatabaseManager().getViolationCount(targetUuid).thenAccept(count -> {
            Lang.send(sender, "check_violations", "value", String.valueOf(count));
        });
        
        // Извинения
        plugin.getDatabaseManager().getApologiesCount(targetUuid).thenAccept(count -> {
            Lang.send(sender, "check_apologies", "value", String.valueOf(count));
        });
        
        // Соотношение извинений к нарушениям
        plugin.getDatabaseManager().getViolationsApologiesRatio(targetUuid).thenAccept(ratio -> {
            Lang.send(sender, "check_ratio", "value", ratio);
        });
        
        // Жалобы за 24 часа
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int reports24h = getReportsCount24h(targetUuid);
            String color = reports24h >= 5 ? "&c" : (reports24h >= 2 ? "&e" : "&a");
            String message = Lang.get("check_reports")
                .replace("%color%", color)
                .replace("%value%", String.valueOf(reports24h));
            if (!message.isEmpty()) {
                sender.sendMessage(Lang.colorize(message));
            }
        });
        
        // Статус
        if (offlineTarget.isOnline()) {
            Lang.send(sender, "check_online");
        } else {
            long lastSeen = offlineTarget.getLastSeen();
            String lastSeenStr = lastSeen > 0 ? 
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(lastSeen)) : 
                Lang.get("time_never");
            String message = Lang.get("check_offline").replace("%time%", lastSeenStr);
            if (!message.isEmpty()) {
                sender.sendMessage(Lang.colorize(message));
            }
        }
        
        return true;
    }
    
    private int getReportsCount24h(UUID targetUuid) {
        try {
            String sql = "SELECT COUNT(*) FROM cpa_reports WHERE target_uuid = ? AND timestamp > ?";
            
            try (var conn = plugin.getDatabaseManager().getConnection();
                 var ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, targetUuid.toString());
                ps.setLong(2, System.currentTimeMillis() - 86400000);
                
                var rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get reports count: " + e.getMessage());
        }
        return 0;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            
            // Онлайн игроки
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (name.toLowerCase().startsWith(partialName)) {
                    suggestions.add(name);
                }
            }
            
            // Оффлайн игроки (до 20)
            int offlineCount = 0;
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlineCount >= 20) break;
                String name = offlinePlayer.getName();
                if (name != null && name.toLowerCase().startsWith(partialName)) {
                    if (!suggestions.contains(name)) {
                        suggestions.add(name);
                        offlineCount++;
                    }
                }
            }
            
            suggestions.sort(String::compareToIgnoreCase);
            return suggestions;
        }
        return List.of();
    }
}
