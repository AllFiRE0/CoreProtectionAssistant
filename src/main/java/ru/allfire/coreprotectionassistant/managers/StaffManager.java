package ru.allfire.coreprotectionassistant.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StaffManager {
    
    private final CoreProtectionAssistant plugin;
    private final Map<UUID, StaffStats> statsCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> teleportHistory = new ConcurrentHashMap<>();
    
    public StaffManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public void processStaffCommand(Player staff, String command, String[] args) {
        String action = command.toUpperCase();
        String target = args.length > 0 ? args[0] : null;
        String details = args.length > 1 ? String.join(" ", 
            Arrays.copyOfRange(args, 1, args.length)) : null;
        
        logStaffAction(staff, action, target, details);
        updateAbuseScore(staff, command, args);
    }
    
    public void logStaffAction(Player staff, String action, String target, String details) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO cpa_staff_actions (staff_uuid, staff_name, action, target, details, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, staff.getUniqueId().toString());
                ps.setString(2, staff.getName());
                ps.setString(3, action);
                ps.setString(4, target);
                ps.setString(5, details);
                ps.setString(6, staff.getWorld().getName());
                ps.setDouble(7, staff.getLocation().getX());
                ps.setDouble(8, staff.getLocation().getY());
                ps.setDouble(9, staff.getLocation().getZ());
                ps.setLong(10, System.currentTimeMillis());
                
                ps.executeUpdate();
                
                if (plugin.getConfigManager().getMainConfig().getBoolean("console_logging.staff_commands", true)) {
                    plugin.getLogger().info("Staff " + staff.getName() + " used /" + action + 
                        (target != null ? " on " + target : ""));
                }
                
                StaffStats stats = statsCache.computeIfAbsent(
                    staff.getUniqueId(), k -> new StaffStats()
                );
                
                switch (action.toLowerCase()) {
                    case "ban" -> stats.bansCount++;
                    case "mute" -> stats.mutesCount++;
                    case "kick" -> stats.kicksCount++;
                    case "give" -> stats.givesCount++;
                    case "gamemode" -> stats.gamemodeChanges++;
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log staff action: " + e.getMessage());
            }
        });
    }
    
    public void logTeleport(Player staff, Location to) {
        List<Long> times = teleportHistory.computeIfAbsent(
            staff.getUniqueId(), k -> new ArrayList<>()
        );
        
        long now = System.currentTimeMillis();
        times.add(now);
        times.removeIf(t -> now - t > 3600000);
        
        if (times.size() > 10) {
            plugin.getAbuseScoreManager().addScore(
                staff.getUniqueId(),
                "teleport_spam",
                5
            );
        }
    }
    
    private void updateAbuseScore(Player staff, String command, String[] args) {
        int score = 0;
        String reason = "";
        
        switch (command.toLowerCase()) {
            case "ban" -> {
                if (args.length < 2) {
                    score = plugin.getConfigManager().getMainConfig()
                        .getInt("abuse_weights.ban_without_reason", 10);
                    reason = "ban_without_reason";
                }
            }
            case "give" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase(staff.getName())) {
                    score = plugin.getConfigManager().getMainConfig()
                        .getInt("abuse_weights.self_give_items", 8);
                    reason = "self_give_items";
                }
            }
            case "gamemode" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase(staff.getName())) {
                    score = plugin.getConfigManager().getMainConfig()
                        .getInt("abuse_weights.self_gamemode_change", 3);
                    reason = "self_gamemode_change";
                }
            }
            case "op" -> {
                score = plugin.getConfigManager().getMainConfig()
                    .getInt("abuse_weights.op_self", 20);
                reason = "op_self";
            }
            case "kick" -> {
                if (args.length < 2) {
                    score = plugin.getConfigManager().getMainConfig()
                        .getInt("abuse_weights.kick_without_reason", 6);
                    reason = "kick_without_reason";
                }
            }
            case "mute" -> {
                if (args.length < 2) {
                    score = plugin.getConfigManager().getMainConfig()
                        .getInt("abuse_weights.mute_without_reason", 4);
                    reason = "mute_without_reason";
                }
            }
        }
        
        if (score > 0) {
            plugin.getAbuseScoreManager().addScore(staff.getUniqueId(), reason, score);
        }
    }
    
    public StaffStats getStaffStats(UUID uuid) {
        return statsCache.computeIfAbsent(uuid, k -> {
            StaffStats stats = new StaffStats();
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String sql = "SELECT SUM(CASE WHEN action = 'BAN' THEN 1 ELSE 0 END) as bans, SUM(CASE WHEN action = 'MUTE' THEN 1 ELSE 0 END) as mutes, SUM(CASE WHEN action = 'KICK' THEN 1 ELSE 0 END) as kicks, SUM(CASE WHEN action = 'GIVE' THEN 1 ELSE 0 END) as gives, SUM(CASE WHEN action = 'GAMEMODE' THEN 1 ELSE 0 END) as gamemodes FROM cpa_staff_actions WHERE staff_uuid = ?";
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    
                    if (rs.next()) {
                        stats.bansCount = rs.getInt("bans");
                        stats.mutesCount = rs.getInt("mutes");
                        stats.kicksCount = rs.getInt("kicks");
                        stats.givesCount = rs.getInt("gives");
                        stats.gamemodeChanges = rs.getInt("gamemodes");
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to load staff stats: " + e.getMessage());
                }
            });
            
            return stats;
        });
    }
    
    public static class StaffStats {
        public int bansCount = 0;
        public int mutesCount = 0;
        public int kicksCount = 0;
        public int givesCount = 0;
        public int gamemodeChanges = 0;
    }
}
