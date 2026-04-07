package ru.allfire.coreprotectionassistant.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.models.StaffWarning;
import ru.allfire.coreprotectionassistant.utils.Color;
import ru.allfire.coreprotectionassistant.utils.CommandExecutor;
import ru.allfire.coreprotectionassistant.utils.ConditionParser;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class WarnManager {
    
    private final CoreProtectionAssistant plugin;
    private final Map<UUID, List<StaffWarning>> warningsCache = new ConcurrentHashMap<>();
    
    public WarnManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public void warnPlayer(UUID targetUuid, String targetName, UUID staffUuid, 
                           String staffName, String reason, long durationTicks) {
        
        long now = System.currentTimeMillis();
        long expiresAt = durationTicks > 0 ? now + (durationTicks * 50) : 0;
        
        StaffWarning warning = StaffWarning.builder()
            .playerUuid(targetUuid)
            .playerName(targetName)
            .staffUuid(staffUuid)
            .staffName(staffName)
            .reason(reason)
            .active(true)
            .createdAt(now)
            .expiresAt(expiresAt)
            .build();
        
        // Сохраняем в БД
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = """
                    INSERT INTO cpa_warnings 
                    (player_uuid, player_name, staff_uuid, staff_name, reason, 
                     active, created_at, expires_at) 
                    VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """;
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setString(1, targetUuid.toString());
                    ps.setString(2, targetName);
                    ps.setString(3, staffUuid != null ? staffUuid.toString() : null);
                    ps.setString(4, staffName);
                    ps.setString(5, reason);
                    ps.setLong(6, now);
                    ps.setLong(7, expiresAt);
                    
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save warning: " + e.getMessage());
            }
        });
        
        // Обновляем кэш
        warningsCache.computeIfAbsent(targetUuid, k -> new ArrayList<>()).add(warning);
        
        // Уведомляем игрока если онлайн
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            String msg = plugin.getConfigManager().getLangConfig()
                .getString("messages.warn_notify_target", 
                    "%prefix% &cВы получили предупреждение от &f%staff%&c. &7(%reason%)")
                .replace("%staff%", staffName)
                .replace("%reason%", reason);
            target.sendMessage(Color.colorize(msg));
        }
        
        // Проверяем пороги для персонала
        if (staffUuid != null) {
            checkStaffWarningThreshold(staffUuid, staffName);
        }
    }
    
    public void clearWarnings(UUID playerUuid, int amount, String clearedBy) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = """
                    UPDATE cpa_warnings 
                    SET active = 0, cleared_at = ?, cleared_by = ? 
                    WHERE player_uuid = ? AND active = 1 
                    ORDER BY created_at ASC LIMIT ?
                """;
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, clearedBy);
                    ps.setString(3, playerUuid.toString());
                    ps.setInt(4, amount);
                    
                    int updated = ps.executeUpdate();
                    
                    if (updated > 0) {
                        // Очищаем кэш
                        warningsCache.remove(playerUuid);
                        
                        plugin.getLogger().info("Cleared " + updated + " warnings for " + 
                            Bukkit.getOfflinePlayer(playerUuid).getName());
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clear warnings: " + e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Integer> getActiveWarningsCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "SELECT COUNT(*) FROM cpa_warnings WHERE player_uuid = ? AND active = 1";
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setString(1, playerUuid.toString());
                    ResultSet rs = ps.executeQuery();
                    
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get warnings count: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<List<StaffWarning>> getWarnings(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            // Проверяем кэш
            if (warningsCache.containsKey(playerUuid)) {
                return warningsCache.get(playerUuid).stream()
                    .filter(StaffWarning::isActive)
                    .toList();
            }
            
            List<StaffWarning> warnings = new ArrayList<>();
            
            try {
                String sql = """
                    SELECT * FROM cpa_warnings 
                    WHERE player_uuid = ? AND active = 1 
                    ORDER BY created_at DESC
                """;
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setString(1, playerUuid.toString());
                    ResultSet rs = ps.executeQuery();
                    
                    while (rs.next()) {
                        StaffWarning warning = StaffWarning.builder()
                            .id(rs.getLong("id"))
                            .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                            .playerName(rs.getString("player_name"))
                            .staffUuid(rs.getString("staff_uuid") != null ? 
                                UUID.fromString(rs.getString("staff_uuid")) : null)
                            .staffName(rs.getString("staff_name"))
                            .reason(rs.getString("reason"))
                            .active(rs.getBoolean("active"))
                            .createdAt(rs.getLong("created_at"))
                            .expiresAt(rs.getLong("expires_at"))
                            .build();
                        
                        warnings.add(warning);
                    }
                }
                
                warningsCache.put(playerUuid, warnings);
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get warnings: " + e.getMessage());
            }
            
            return warnings;
        });
    }
    
    public void checkWarnClearConditions() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("warn_clear.enabled", true)) {
            return;
        }
        
        var checks = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("warn_clear.checks");
        
        if (checks == null) return;
        
        for (String checkName : checks.getKeys(false)) {
            var check = checks.getConfigurationSection(checkName);
            if (check == null || !check.getBoolean("enabled", true)) continue;
            
            String condition = check.getString("condition", "");
            List<String> commands = check.getStringList("commands");
            
            // Проверяем для всех онлайн игроков
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (ConditionParser.evaluate(plugin, player, condition)) {
                    for (String cmd : commands) {
                        CommandExecutor.execute(plugin, player, null, cmd);
                    }
                }
            }
        }
    }
    
    private void checkStaffWarningThreshold(UUID staffUuid, String staffName) {
        int abuseScore = plugin.getAbuseScoreManager().getScore(staffUuid);
        
        var warnings = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("staff_warnings");
        
        if (warnings == null) return;
        
        for (String level : warnings.getKeys(false)) {
            var levelConfig = warnings.getConfigurationSection(level);
            if (levelConfig == null || !levelConfig.getBoolean("enabled", true)) continue;
            
            int threshold = levelConfig.getInt("threshold", 100);
            
            if (abuseScore >= threshold) {
                List<String> commands = levelConfig.getStringList("commands");
                for (String cmd : commands) {
                    CommandExecutor.execute(plugin, null, staffName, cmd);
                }
            }
        }
    }
}
