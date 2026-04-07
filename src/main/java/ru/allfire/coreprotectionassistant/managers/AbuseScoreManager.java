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
        
        plugin.getLogger().info("Loaded " + weights.size() + " abuse score weights");
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
        
        // Сохраняем в БД асинхронно
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                if (playerName == null) playerName = "Unknown";
                
                // Сначала пробуем обновить существующую запись
                String updateSql = "UPDATE cpa_abuse_scores SET player_name = ?, score = ?, last_updated = ? WHERE player_uuid = ?";
                
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    
                    ps.setString(1, playerName);
                    ps.setInt(2, newScore);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setString(4, uuid.toString());
                    
                    int updated = ps.executeUpdate();
                    
                    // Если запись не обновилась - вставляем новую
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
                    
                    plugin.getLogger().info("Added " + weight + " abuse score to " + 
                        playerName + " (" + reason + "). Total: " + newScore);
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save abuse score: " + e.getMessage());
            }
        });
        
        // Проверяем пороги (выполняется в основном потоке для команд)
        String playerName = Bukkit.getOfflinePlayer(uuid).getName();
        if (playerName != null) {
            checkThresholds(uuid, playerName, newScore);
        }
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
            
            // Проверяем, что score >= порога, и предыдущее значение было меньше порога
            // (чтобы не спамить при каждом добавлении)
            if (score >= threshold) {
                // Проверяем, не было ли уже предупреждения на этом уровне
                if (hasRecentWarning(uuid, level, threshold)) {
                    continue;
                }
                
                // Выполняем команды в ОСНОВНОМ потоке
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (String cmd : levelConfig.getStringList("commands")) {
                        String processed = cmd
                            .replace("%player_name%", playerName)
                            .replace("%player_uuid%", uuid.toString())
                            .replace("%score%", String.valueOf(score))
                            .replace("%threshold%", String.valueOf(threshold));
                        
                        // Если команда с asConsole! - выполняем от консоли
                        if (processed.startsWith("asConsole! ")) {
                            String consoleCmd = processed.substring(11);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                ru.allfire.coreprotectionassistant.utils.Color.strip(consoleCmd));
                        } else if (processed.startsWith("broadcast! ")) {
                            String message = processed.substring(11);
                            Bukkit.broadcastMessage(ru.allfire.coreprotectionassistant.utils.Color.colorize(message));
                        } else if (processed.startsWith("message! ")) {
                            String message = processed.substring(9);
                            org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
                            if (player != null) {
                                player.sendMessage(ru.allfire.coreprotectionassistant.utils.Color.colorize(message));
                            }
                        }
                    }
                });
                
                // Логируем срабатывание
                plugin.getLogger().warning("Staff " + playerName + " reached abuse score threshold: " + 
                    level + " (" + score + "/" + threshold + ")");
            }
        }
    }
    
    private boolean hasRecentWarning(UUID uuid, String level, int threshold) {
        // Простая проверка - можно доработать
        return false;
    }
}
