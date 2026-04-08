package ru.allfire.coreprotectionassistant.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AbuseScoreManager {
    
    private final CoreProtectionAssistant plugin;
    private final Map<UUID, Integer> scoreCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> weights = new ConcurrentHashMap<>();
    
    public AbuseScoreManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        loadWeights();
    }
    
    private void loadWeights() {
        var config = plugin.getConfigManager().getMainConfig().getConfigurationSection("abuse_weights");
        if (config != null) {
            for (String key : config.getKeys(false)) {
                weights.put(key, config.getInt(key, 5));
            }
        }
    }
    
    public int getScore(UUID uuid) {
        return scoreCache.computeIfAbsent(uuid, k -> {
            String sql = "SELECT score FROM cpa_abuse_scores WHERE player_uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("score");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get abuse score: " + e.getMessage());
            }
            return 0;
        });
    }
    
    public void addScore(UUID uuid, String reason, int score) {
        int weight = weights.getOrDefault(reason, score);
        int currentScore = getScore(uuid);
        int newScore = Math.min(currentScore + weight, 100);
        
        scoreCache.put(uuid, newScore);
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            if (playerName == null) playerName = "Unknown";
            
            String updateSql = "UPDATE cpa_abuse_scores SET player_name = ?, score = ?, last_updated = ? WHERE player_uuid = ?";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(updateSql)) {
                
                ps.setString(1, playerName);
                ps.setInt(2, newScore);
                ps.setLong(3, System.currentTimeMillis());
                ps.setString(4, uuid.toString());
                
                int updated = ps.executeUpdate();
                
                if (updated == 0) {
                    String insertSql = "INSERT INTO cpa_abuse_scores (player_uuid, player_name, score, last_updated) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                        insertPs.setString(1, uuid.toString());
                        insertPs.setString(2, playerName);
                        insertPs.setInt(3, newScore);
                        insertPs.setLong(4, System.currentTimeMillis());
                        insertPs.executeUpdate();
                    }
                }
                
                if (plugin.getConfigManager().getMainConfig().getBoolean("console_logging.abuse_score", true)) {
                    String msg = Lang.get("abuse_score_added_log")
                        .replace("%weight%", String.valueOf(weight))
                        .replace("%player%", playerName)
                        .replace("%reason%", reason)
                        .replace("%total%", String.valueOf(newScore));
                    plugin.getLogger().info(Lang.colorize(msg));
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save abuse score: " + e.getMessage());
            }
        });
        
        String playerName = Bukkit.getOfflinePlayer(uuid).getName();
        if (playerName != null) {
            checkThresholds(uuid, playerName, newScore);
        }
    }
    
    public void resetScore(UUID uuid) {
        scoreCache.put(uuid, 0);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE cpa_abuse_scores SET score = 0, last_updated = ? WHERE player_uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to reset abuse score: " + e.getMessage());
            }
        });
    }
    
    private void checkThresholds(UUID uuid, String playerName, int score) {
        var warnings = plugin.getConfigManager().getMainConfig().getConfigurationSection("staff_warnings");
        if (warnings == null) return;
        
        for (String level : warnings.getKeys(false)) {
            var levelConfig = warnings.getConfigurationSection(level);
            if (levelConfig == null || !levelConfig.getBoolean("enabled", true)) continue;
            
            int threshold = levelConfig.getInt("threshold", 100);
            
            if (score >= threshold) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (String cmd : levelConfig.getStringList("commands")) {
                        String processed = cmd
                            .replace("%player_name%", playerName)
                            .replace("%player_uuid%", uuid.toString())
                            .replace("%score%", String.valueOf(score))
                            .replace("%threshold%", String.valueOf(threshold));
                        
                        if (processed.startsWith("asConsole! ")) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed.substring(11));
                        } else if (processed.startsWith("broadcast! ")) {
                            Bukkit.broadcastMessage(Lang.colorize(processed.substring(11)));
                        } else if (processed.startsWith("message! ")) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null) {
                                player.sendMessage(Lang.colorize(processed.substring(9)));
                            }
                        }
                    }
                });
            }
        }
    }
}
