package ru.allfire.coreprotectionassistant.managers;

import org.bukkit.Bukkit;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

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
        var config = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("abuse_weights");
        
        if (config != null) {
            for (String key : config.getKeys(false)) {
                weights.put(key, config.getInt(key, 5));
            }
        }
    }
    
    public int getScore(UUID uuid) {
        return scoreCache.computeIfAbsent(uuid, k -> {
            try {
                String sql = "SELECT score FROM cpa_abuse_scores WHERE player_uuid = ?";
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    
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
        
        // Сохраняем в БД
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                
                String sql = """
                    INSERT INTO cpa_abuse_scores (player_uuid, player_name, score, last_updated) 
                    VALUES (?, ?, ?, ?) 
                    ON DUPLICATE KEY UPDATE 
                    player_name = VALUES(player_name), 
                    score = VALUES(score), 
                    last_updated = VALUES(last_updated)
                """;
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setString(1, uuid.toString());
                    ps.setString(2, playerName);
                    ps.setInt(3, newScore);
                    ps.setLong(4, System.currentTimeMillis());
                    
                    ps.executeUpdate();
                }
                
                plugin.getLogger().info("Added " + weight + " abuse score to " + 
                    playerName + " (" + reason + "). Total: " + newScore);
                    
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save abuse score: " + e.getMessage());
            }
        });
        
        // Проверяем пороги
        checkThresholds(uuid, Bukkit.getOfflinePlayer(uuid).getName(), newScore);
    }
    
    public void resetScore(UUID uuid) {
        scoreCache.put(uuid, 0);
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "UPDATE cpa_abuse_scores SET score = 0, last_updated = ? WHERE player_uuid = ?";
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, uuid.toString());
                    
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to reset abuse score: " + e.getMessage());
            }
        });
    }
    
    private void checkThresholds(UUID uuid, String playerName, int score) {
        var warnings = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("staff_warnings");
        
        if (warnings == null) return;
        
        for (String level : warnings.getKeys(false)) {
            var levelConfig = warnings.getConfigurationSection(level);
            if (levelConfig == null || !levelConfig.getBoolean("enabled", true)) continue;
            
            int threshold = levelConfig.getInt("threshold", 100);
            
            if (score >= threshold) {
                // Проверяем, не было ли уже предупреждения на этом уровне
                if (hasRecentWarning(uuid, level, threshold)) {
                    continue;
                }
                
                // Выполняем команды
                for (String cmd : levelConfig.getStringList("commands")) {
                    String processed = cmd
                        .replace("%player_name%", playerName)
                        .replace("%player_uuid%", uuid.toString())
                        .replace("%score%", String.valueOf(score))
                        .replace("%threshold%", String.valueOf(threshold));
                    
                    plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(), 
                        processed.replace("asConsole! ", "")
                    );
                }
            }
        }
    }
    
    private boolean hasRecentWarning(UUID uuid, String level, int threshold) {
        // Проверяем, было ли предупреждение на этом уровне за последние 24 часа
        return false; // TODO: реализовать проверку
    }
}
