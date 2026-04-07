package ru.allfire.coreprotectionassistant.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
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
    
    // ========== COMMAND LOGS ==========
    
    public CompletableFuture<Void> saveCommandLog(CommandLog log) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_command_logs " +
                "(player_uuid, player_name, command, args, full_command, " +
                "world, x, y, z, timestamp, is_staff) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, log.getPlayerUuid() != null ? 
                    log.getPlayerUuid().toString() : null);
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
    
    // ========== SUPER COMMANDS ==========
    
    public CompletableFuture<Void> saveSuperCommand(Player player, String command, String[] args) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_super_commands " +
                "(player_uuid, player_name, command, args, world, x, y, z, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = database.getConnection();
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
            String sql = "INSERT INTO cpa_player_sessions " +
                "(player_uuid, player_name, action, world, x, y, z, timestamp) " +
                "VALUES (?, ?, 'JOIN', ?, ?, ?, ?, ?)";
            
            try (Connection conn = database.getConnection();
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
            String sql = "INSERT INTO cpa_player_sessions " +
                "(player_uuid, player_name, action, world, x, y, z, timestamp) " +
                "VALUES (?, ?, 'QUIT', ?, ?, ?, ?, ?)";
            
            try (Connection conn = database.getConnection();
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
    
    public CompletableFuture<Void> logViolation(Player player, String ruleName, 
                                                  String punishment, long timestamp) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO cpa_chat_violations " +
                "(player_uuid, player_name, rule_name, punishment, timestamp) " +
                "VALUES (?, ?, ?, ?, ?)";
            
            try (Connection conn = database.getConnection();
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
            String sql = "INSERT INTO cpa_prohibited_perms " +
                "(player_uuid, permission, timestamp) VALUES (?, ?, ?)";
            
            try (Connection conn = database.getConnection();
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
    
    // ========== QUERIES ==========
    
    public CompletableFuture<Integer> getViolationCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_chat_violations WHERE player_uuid = ?";
            
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get violation count: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public CompletableFuture<Long> getLastViolationTime(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT timestamp FROM cpa_chat_violations " +
                "WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 1";
            
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get last violation time: " + e.getMessage());
            }
            return 0L;
        });
    }
    
    public CompletableFuture<Integer> getCommandCount(UUID uuid, String command) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM cpa_command_logs " +
                "WHERE player_uuid = ? AND command = ?";
            
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, uuid.toString());
                ps.setString(2, command);
                ResultSet rs = ps.executeQuery();
                
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get command count: " + e.getMessage());
            }
            return 0;
        });
    }
}
