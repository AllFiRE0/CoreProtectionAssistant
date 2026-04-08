package ru.allfire.coreprotectionassistant.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;
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
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO cpa_warnings (player_uuid, player_name, staff_uuid, staff_name, reason, active, created_at, expires_at) VALUES (?, ?, ?, ?, ?, 1, ?, ?)";
            
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
                
                if (plugin.getConfigManager().getMainConfig().getBoolean("console_logging.warnings", true)) {
                    String msg = Lang.get("warn_issued_log")
                        .replace("%target%", targetName)
                        .replace("%staff%", staffName)
                        .replace("%reason%", reason);
                    plugin.getLogger().info(Lang.colorize(msg));
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save warning: " + e.getMessage());
            }
        });
        
        warningsCache.computeIfAbsent(targetUuid, k -> new ArrayList<>()).add(warning);
        
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            String msg = Lang.get("warn_notify_target")
                .replace("%staff%", staffName)
                .replace("%reason%", reason);
            target.sendMessage(Color.colorize(msg));
        }
        
        if (staffUuid != null) {
            checkStaffWarningThreshold(staffUuid, staffName);
        }
    }
    
    public void clearWarnings(UUID playerUuid, int amount, String clearedBy) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE cpa_warnings SET active = 0, cleared_at = ?, cleared_by = ? WHERE player_uuid = ? AND active = 1 ORDER BY created_at ASC LIMIT ?";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, clearedBy);
                ps.setString(3, playerUuid.toString());
                ps.setInt(4, amount);
                
                int updated = ps.executeUpdate();
                
                if (updated > 0) {
                    warningsCache.remove(playerUuid);
                    if (plugin.getConfigManager().getMainConfig().getBoolean("console_logging.warnings", true)) {
                        String msg = Lang.get("warn_cleared_log")
                            .replace("%amount%", String.valueOf(updated))
                            .replace("%player%", Bukkit.getOfflinePlayer(playerUuid).getName());
                        plugin.getLogger().info(Lang.colorize(msg));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clear warnings: " + e.getMessage());
            }
        });
    }
    
    public void checkExpiredWarnings() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE cpa_warnings SET active = 0, cleared_at = ?, cleared_by = 'SYSTEM' WHERE active = 1 AND expires_at > 0 AND expires_at < ?";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                long now = System.currentTimeMillis();
                ps.setLong(1, now);
                ps.setLong(2, now);
                
                int updated = ps.executeUpdate();
                
                if (updated > 0) {
                    warningsCache.clear();
                    
                    if (plugin.getConfigManager().getMainConfig().getBoolean("console_logging.warnings", true)) {
                        String msg = Lang.get("warn_auto_cleared_log")
                            .replace("%amount%", String.valueOf(updated));
                        plugin.getLogger().info(Lang.colorize(msg));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clear expired warnings: " + e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Integer> getActiveWarningsCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_warnings WHERE player_uuid = ? AND active = 1";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get warnings count: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<List<StaffWarning>> getWarnings(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (warningsCache.containsKey(playerUuid)) {
                return warningsCache.get(playerUuid).stream()
                    .filter(StaffWarning::isActive)
                    .toList();
            }
            
            List<StaffWarning> warnings = new ArrayList<>();
            String sql = "SELECT * FROM cpa_warnings WHERE player_uuid = ? AND active = 1 ORDER BY created_at DESC";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                ResultSet rs = ps.executeQuery();
                
                while (rs.next()) {
                    StaffWarning warning = StaffWarning.builder()
                        .id(rs.getLong("id"))
                        .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                        .playerName(rs.getString("player_name"))
                        .staffUuid(rs.getString("staff_uuid") != null ? UUID.fromString(rs.getString("staff_uuid")) : null)
                        .staffName(rs.getString("staff_name"))
                        .reason(rs.getString("reason"))
                        .active(rs.getBoolean("active"))
                        .createdAt(rs.getLong("created_at"))
                        .expiresAt(rs.getLong("expires_at"))
                        .clearedAt(rs.getLong("cleared_at"))
                        .clearedBy(rs.getString("cleared_by"))
                        .build();
                    warnings.add(warning);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get warnings: " + e.getMessage());
            }
            
            warningsCache.put(playerUuid, warnings);
            return warnings;
        });
    }
    
    public void checkWarnClearConditions() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("warn_clear.enabled", true)) {
            return;
        }
        
        var checks = plugin.getConfigManager().getMainConfig().getConfigurationSection("warn_clear.checks");
        if (checks == null) return;
        
        for (String checkName : checks.getKeys(false)) {
            var check = checks.getConfigurationSection(checkName);
            if (check == null || !check.getBoolean("enabled", true)) continue;
            
            String condition = check.getString("condition", "");
            List<String> commands = check.getStringList("commands");
            
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
        
        var warnings = plugin.getConfigManager().getMainConfig().getConfigurationSection("staff_warnings");
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
