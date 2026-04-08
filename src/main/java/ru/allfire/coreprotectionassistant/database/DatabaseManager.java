package ru.allfire.coreprotectionassistant.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.models.CommandLog;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class DatabaseManager {
    
    private final CoreProtectionAssistant plugin;
    private final Gson gson;
    private IDatabase database;
    private boolean mysql;
    
    public DatabaseManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    public boolean init() {
        String type = plugin.getConfigManager().getMainConfig()
            .getString("database.type", "sqlite");
        mysql = type.equalsIgnoreCase("mysql");
        
        if (mysql) {
            database = new MySQL(plugin);
        } else {
            database = new SQLite(plugin);
        }
        
        return database.connect();
    }
    
    public void close() {
        if (database != null) {
            database.disconnect();
        }
    }
    
    public Connection getConnection() throws SQLException {
        return database.getConnection();
    }
    
    // ========== COMMAND LOGS ==========
    
    public CompletableFuture<Void> saveCommandLog(CommandLog log) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_command_logs (player_uuid, player_name, command, args, full_command, world, x, y, z, timestamp, is_staff) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, log.getPlayerUuid() != null ? log.getPlayerUuid().toString() : null);
                ps.setString(2, log.getPlayerName());
                ps.setString(3, log.getCommand());
                ps.setString(4, gson.toJson(log.getArgs()));
                ps.setString(5, log.getFullCommand());
                ps.setString(6, log.getWorld());
                ps.setDouble(7, log.getX());
                ps.setDouble(8, log.getY());
                ps.setDouble(9, log.getZ());
                ps.setLong(10, log.getTimestamp());
                ps.setBoolean(11, log.isStaff());
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save command log: " + e.getMessage());
            }
        });
    }
    
    // ========== PLAYER COMMANDS ==========
    
    public CompletableFuture<Void> logPlayerCommand(UUID playerUuid, String playerName, 
                                                      String command, String[] args, 
                                                      String fullCommand, String world,
                                                      double x, double y, double z, 
                                                      boolean isStaff) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_player_commands (player_uuid, player_name, command, args, full_command, world, x, y, z, timestamp, is_staff) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid != null ? playerUuid.toString() : null);
                ps.setString(2, playerName);
                ps.setString(3, command);
                ps.setString(4, gson.toJson(args));
                ps.setString(5, fullCommand);
                ps.setString(6, world);
                ps.setDouble(7, x);
                ps.setDouble(8, y);
                ps.setDouble(9, z);
                ps.setLong(10, System.currentTimeMillis());
                ps.setBoolean(11, isStaff);
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log player command: " + e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Integer> getPlayerCommandCount(UUID uuid, String command) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_player_commands WHERE player_uuid = ? AND command = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                ps.setString(2, command);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get command count: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<Integer> getTotalCommandsUsed(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_player_commands WHERE player_uuid = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get total commands: " + e.getMessage());
            }
            return 0;
        });
    }
    
    // ========== SUPER COMMANDS ==========
    
    public CompletableFuture<Void> saveSuperCommand(Player player, String command, String[] args) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_super_commands (player_uuid, player_name, command, args, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, command);
                ps.setString(4, gson.toJson(args));
                ps.setString(5, player.getWorld().getName());
                ps.setDouble(6, player.getLocation().getX());
                ps.setDouble(7, player.getLocation().getY());
                ps.setDouble(8, player.getLocation().getZ());
                ps.setLong(9, System.currentTimeMillis());
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save super command: " + e.getMessage());
            }
        });
    }
    
    // ========== PLAYER JOINS/QUITS ==========
    
    public CompletableFuture<Void> logPlayerJoin(Player player) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_player_sessions (player_uuid, player_name, action, world, x, y, z, timestamp) VALUES (?, ?, 'JOIN', ?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, player.getWorld().getName());
                ps.setDouble(4, player.getLocation().getX());
                ps.setDouble(5, player.getLocation().getY());
                ps.setDouble(6, player.getLocation().getZ());
                ps.setLong(7, System.currentTimeMillis());
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log player join: " + e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Void> logPlayerQuit(Player player) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_player_sessions (player_uuid, player_name, action, world, x, y, z, timestamp) VALUES (?, ?, 'QUIT', ?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, player.getWorld().getName());
                ps.setDouble(4, player.getLocation().getX());
                ps.setDouble(5, player.getLocation().getY());
                ps.setDouble(6, player.getLocation().getZ());
                ps.setLong(7, System.currentTimeMillis());
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log player quit: " + e.getMessage());
            }
        });
    }
    
    // ========== CHAT VIOLATIONS ==========
    
    public CompletableFuture<Void> logViolation(Player player, String ruleName, String punishment, long timestamp) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_chat_violations (player_uuid, player_name, rule_name, punishment, timestamp) VALUES (?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, ruleName);
                ps.setString(4, punishment);
                ps.setLong(5, timestamp);
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log violation: " + e.getMessage());
            }
        });
    }
    
    // ========== PROHIBITED PERMISSIONS ==========
    
    public CompletableFuture<Void> logProhibitedPermission(UUID uuid, String permission) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_prohibited_perms (player_uuid, permission, timestamp) VALUES (?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                ps.setString(2, permission);
                ps.setLong(3, System.currentTimeMillis());
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log prohibited permission: " + e.getMessage());
            }
        });
    }
    
    // ========== STAFF ACTIONS ==========
    
    public CompletableFuture<Void> logStaffAction(UUID staffUuid, String staffName, String action, String target, String details, String world, double x, double y, double z) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_staff_actions (staff_uuid, staff_name, action, target, details, world, x, y, z, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, staffUuid.toString());
                ps.setString(2, staffName);
                ps.setString(3, action);
                ps.setString(4, target);
                ps.setString(5, details);
                ps.setString(6, world);
                ps.setDouble(7, x);
                ps.setDouble(8, y);
                ps.setDouble(9, z);
                ps.setLong(10, System.currentTimeMillis());
                
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log staff action: " + e.getMessage());
            }
        });
    }
    
    // ========== APOLOGIES ==========
    
    public CompletableFuture<Void> logApology(Player player, String ruleName, int warningsCleared) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_apologies (player_uuid, player_name, rule_name, warnings_cleared, timestamp) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, ruleName);
                ps.setInt(4, warningsCleared);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log apology: " + e.getMessage());
            }
        });
    }
    
    public CompletableFuture<Integer> getApologiesCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_apologies WHERE player_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get apologies count: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<String> getViolationsApologiesRatio(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            int violations = getViolationCount(uuid).join();
            int apologies = getApologiesCount(uuid).join();
            if (violations == 0) return "0%";
            int ratio = (apologies * 100) / violations;
            return Math.min(ratio, 100) + "%";
        });
    }
    
    // ========== GRIEF ACTIONS ==========
    
    public CompletableFuture<Void> logGriefAction(Player player, Block block) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_grief_actions (player_uuid, player_name, world, x, y, z, block_type, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, block.getWorld().getName());
                ps.setInt(4, block.getX());
                ps.setInt(5, block.getY());
                ps.setInt(6, block.getZ());
                ps.setString(7, block.getType().name());
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log grief action: " + e.getMessage());
            }
        });
    }
    
    // ========== REPORTS STATISTICS ==========
    
    public CompletableFuture<Integer> getReportsAgainstPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_reports WHERE target_uuid = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get reports against player: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<Integer> getReportsAgainstPlayerByCategory(UUID uuid, String category) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_reports WHERE target_uuid = ? AND category = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                ps.setString(2, category);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get reports by category: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<Integer> getReportsFiledByPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_reports WHERE reporter_uuid = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get reports filed by player: " + e.getMessage());
            }
            return 0;
        });
    }
    
    // ========== CLEANUP ==========
    
    public void cleanupOldData() {
        var config = plugin.getConfigManager().getMainConfig();
        
        cleanupTable("cpa_player_commands", config.getInt("cleanup.player_commands", 30));
        cleanupTable("cpa_staff_actions", config.getInt("cleanup.staff_actions", 90));
        cleanupTable("cpa_chat_violations", config.getInt("cleanup.chat_violations", 30));
        cleanupTable("cpa_apologies", config.getInt("cleanup.apologies", 30));
        cleanupTable("cpa_grief_actions", config.getInt("cleanup.grief_actions", 30));
    }
    
    private void cleanupTable(String tableName, int days) {
        if (days <= 0) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long cutoff = System.currentTimeMillis() - (days * 24L * 3600000);
            String sql = "DELETE FROM " + tableName + " WHERE timestamp < ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, cutoff);
                int deleted = ps.executeUpdate();
                
                if (deleted > 0) {
                    plugin.getLogger().info("Cleaned up " + deleted + " old records from " + tableName);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to cleanup " + tableName + ": " + e.getMessage());
            }
        });
    }
    
    public void resetPlayerStats(UUID uuid, String type) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection()) {
                int totalDeleted = 0;
                
                switch (type.toLowerCase()) {
                    case "commands" -> {
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_player_commands WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_staff_actions WHERE staff_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_command_logs WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_super_commands WHERE player_uuid = ?", uuid);
                    }
                    case "ban" -> totalDeleted = executeDelete(conn, "DELETE FROM cpa_staff_actions WHERE staff_uuid = ? AND action = 'BAN'", uuid);
                    case "mute" -> totalDeleted = executeDelete(conn, "DELETE FROM cpa_staff_actions WHERE staff_uuid = ? AND action = 'MUTE'", uuid);
                    case "kick" -> totalDeleted = executeDelete(conn, "DELETE FROM cpa_staff_actions WHERE staff_uuid = ? AND action = 'KICK'", uuid);
                    case "give" -> totalDeleted = executeDelete(conn, "DELETE FROM cpa_staff_actions WHERE staff_uuid = ? AND action = 'GIVE'", uuid);
                    case "gm" -> totalDeleted = executeDelete(conn, "DELETE FROM cpa_staff_actions WHERE staff_uuid = ? AND action IN ('GAMEMODE', 'GM')", uuid);
                    case "rating" -> totalDeleted = executeDelete(conn, "DELETE FROM cpa_abuse_scores WHERE player_uuid = ?", uuid);
                    case "warn" -> totalDeleted = executeDelete(conn, "DELETE FROM cpa_warnings WHERE player_uuid = ?", uuid);
                    case "free" -> {
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_warnings WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_abuse_scores WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_reports WHERE target_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_reports WHERE reporter_uuid = ?", uuid);
                    }
                    case "all" -> {
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_player_commands WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_staff_actions WHERE staff_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_warnings WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_abuse_scores WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_reports WHERE target_uuid = ? OR reporter_uuid = ?", uuid, uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_chat_violations WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_apologies WHERE player_uuid = ?", uuid);
                        totalDeleted += executeDelete(conn, "DELETE FROM cpa_grief_actions WHERE player_uuid = ?", uuid);
                    }
                }
                
                if (totalDeleted > 0) {
                    plugin.getLogger().info("Reset " + totalDeleted + " records for " + Bukkit.getOfflinePlayer(uuid).getName() + " (type: " + type + ")");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to reset player stats: " + e.getMessage());
            }
        });
    }
    
    private int executeDelete(Connection conn, String sql, UUID... uuids) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < uuids.length; i++) {
                ps.setString(i + 1, uuids[i].toString());
            }
            return ps.executeUpdate();
        }
    }
    
    // ========== QUERIES ==========
    
    public CompletableFuture<Integer> getViolationCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_chat_violations WHERE player_uuid = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get violation count: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<Long> getLastViolationTime(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT timestamp FROM cpa_chat_violations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 1";
            
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get last violation time: " + e.getMessage());
            }
            return 0L;
        });
    }
    
    public CompletableFuture<Integer> getCommandCount(UUID uuid, String command) {
        return getPlayerCommandCount(uuid, command);
    }
}
