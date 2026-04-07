package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.Color;

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
            plugin.getConfigManager().getLangConfig().getString("messages.check_header",
                "&8&m-----&r &cCheck: &f%player% &8&m-----")
                .replace("%player%", offlineTarget.getName())
        ));
        
        // Предупреждения
        plugin.getWarnManager().getActiveWarningsCount(targetUuid).thenAccept(count -> {
            String color = count >= 3 ? "&c" : (count >= 1 ? "&e" : "&a");
            sender.sendMessage(Color.colorize(
                plugin.getConfigManager().getLangConfig().getString("messages.check_warnings",
                    "&7Active warnings: " + color + "%value%")
                    .replace("%value%", String.valueOf(count))
            ));
        });
        
        // Нарушения чата
        plugin.getDatabaseManager().getViolationCount(targetUuid).thenAccept(count -> {
            sender.sendMessage(Color.colorize(
                plugin.getConfigManager().getLangConfig().getString("messages.check_violations",
                    "&7Chat violations: &f%value%")
                    .replace("%value%", String.valueOf(count))
            ));
        });
        
        // Жалобы за 24 часа
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int reports24h = getReportsCount24h(targetUuid);
            String color = reports24h >= 5 ? "&c" : (reports24h >= 2 ? "&e" : "&a");
            sender.sendMessage(Color.colorize(
                plugin.getConfigManager().getLangConfig().getString("messages.check_reports",
                    "&7Reports (24h): " + color + "%value%")
                    .replace("%value%", String.valueOf(reports24h))
            ));
        });
        
        // Статус
        if (offlineTarget.isOnline()) {
            sender.sendMessage(Color.colorize("&7Status: &aOnline"));
        } else {
            long lastSeen = offlineTarget.getLastSeen();
            String lastSeenStr = lastSeen > 0 ? 
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(lastSeen)) : 
                "Never";
            sender.sendMessage(Color.colorize("&7Status: &cOffline &7(Last seen: &f" + lastSeenStr + "&7)"));
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
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
