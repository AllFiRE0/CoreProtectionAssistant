package ru.allfire.coreprotectionassistant.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CoreProtectDatabaseHook {
    
    private final CoreProtectionAssistant plugin;
    private File coreProtectDbFile;
    private Connection connection;
    
    // Кэш для маппинга ID -> имена
    private final Map<Integer, String> userCache = new HashMap<>();
    private final Map<Integer, String> worldCache = new HashMap<>();
    private final Map<Integer, String> materialCache = new HashMap<>();
    
    // Отслеживаемые блоки из конфига
    private List<String> trackedBlocks;
    
    public CoreProtectDatabaseHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        // Путь к БД CoreProtect
        File coreProtectFolder = new File("plugins/CoreProtect");
        if (!coreProtectFolder.exists()) {
            plugin.getLogger().warning("CoreProtect plugin folder not found!");
            return false;
        }
        
        coreProtectDbFile = new File(coreProtectFolder, "database.db");
        if (!coreProtectDbFile.exists()) {
            plugin.getLogger().warning("CoreProtect database.db not found!");
            return false;
        }
        
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + coreProtectDbFile.getAbsolutePath());
            connection.setReadOnly(true); // Только чтение!
            
            // Загружаем кэш
            loadCache();
            
            // Загружаем отслеживаемые блоки из конфига
            trackedBlocks = plugin.getConfigManager().getMainConfig()
                .getStringList("grief_detection.tracked_blocks");
            
            plugin.getLogger().info("CoreProtect database hook initialized. Found " + 
                userCache.size() + " users, " + worldCache.size() + " worlds, " + 
                materialCache.size() + " materials");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to CoreProtect database: " + e.getMessage());
            return false;
        }
    }
    
    private void loadCache() {
        // Загружаем пользователей
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, user FROM co_user")) {
            while (rs.next()) {
                userCache.put(rs.getInt("id"), rs.getString("user"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load users: " + e.getMessage());
        }
        
        // Загружаем миры
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, world FROM co_world")) {
            while (rs.next()) {
                worldCache.put(rs.getInt("id"), rs.getString("world"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load worlds: " + e.getMessage());
        }
        
        // Загружаем материалы
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, material FROM co_material")) {
            while (rs.next()) {
                materialCache.put(rs.getInt("id"), rs.getString("material"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load materials: " + e.getMessage());
        }
    }
    
    /**
     * Получить историю блока по координатам
     */
    public CompletableFuture<List<BlockAction>> getBlockHistory(Location loc) {
        return CompletableFuture.supplyAsync(() -> {
            List<BlockAction> history = new ArrayList<>();
            
            // Находим ID мира
            int worldId = -1;
            for (Map.Entry<Integer, String> entry : worldCache.entrySet()) {
                if (entry.getValue().equals(loc.getWorld().getName())) {
                    worldId = entry.getKey();
                    break;
                }
            }
            
            if (worldId == -1) return history;
            
            String sql = """
                SELECT b.time, b.user, b.type, b.data, b.action, u.user as username
                FROM co_block b
                JOIN co_user u ON b.user = u.id
                WHERE b.wid = ? AND b.x = ? AND b.y = ? AND b.z = ?
                ORDER BY b.time ASC
                """;
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, worldId);
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long time = rs.getLong("time") * 1000; // В миллисекунды
                        String username = rs.getString("username");
                        int type = rs.getInt("type"); // 0=remove, 1=place
                        int materialId = rs.getInt("data");
                        String material = materialCache.getOrDefault(materialId, "UNKNOWN");
                        
                        BlockAction action = new BlockAction(
                            time, username, type == 0 ? "BREAK" : "PLACE", material
                        );
                        history.add(action);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get block history: " + e.getMessage());
            }
            
            return history;
        });
    }
    
    /**
     * Проверить, взаимодействовал ли другой игрок с этим блоком
     */
    public CompletableFuture<Boolean> wasModifiedByOther(Location loc, String currentPlayer) {
        return getBlockHistory(loc).thenApply(history -> {
            for (BlockAction action : history) {
                if (!action.username.equalsIgnoreCase(currentPlayer)) {
                    return true;
                }
            }
            return false;
        });
    }
    
    /**
     * Проверить похожие ники (AllF1RE vs AIIFiRE)
     */
    public static boolean areNicksSimilar(String nick1, String nick2) {
        if (nick1.equalsIgnoreCase(nick2)) return true;
        
        // Убираем частые замены
        String normalized1 = normalizeNick(nick1);
        String normalized2 = normalizeNick(nick2);
        
        return normalized1.equalsIgnoreCase(normalized2) || 
               levenshteinDistance(normalized1, normalized2) <= 2;
    }
    
    private static String normalizeNick(String nick) {
        return nick.toLowerCase()
            .replace('1', 'i')
            .replace('l', 'i')
            .replace('0', 'o')
            .replace('5', 's')
            .replace('6', 'b')
            .replace('3', 'e')
            .replace('4', 'a');
    }
    
    private static int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
    
    /**
     * Получить статистику игрока (количество сломанных/поставленных блоков)
     */
    public CompletableFuture<PlayerStats> getPlayerStats(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerStats stats = new PlayerStats();
            
            // Находим ID игрока
            int userId = -1;
            for (Map.Entry<Integer, String> entry : userCache.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(playerName)) {
                    userId = entry.getKey();
                    break;
                }
            }
            
            if (userId == -1) return stats;
            
            // Сломанные блоки
            String sql = "SELECT COUNT(*) as count FROM co_block WHERE user = ? AND type = 0";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) stats.blocksBroken = rs.getInt("count");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get blocks broken: " + e.getMessage());
            }
            
            // Поставленные блоки
            sql = "SELECT COUNT(*) as count FROM co_block WHERE user = ? AND type = 1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) stats.blocksPlaced = rs.getInt("count");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get blocks placed: " + e.getMessage());
            }
            
            return stats;
        });
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close CoreProtect database: " + e.getMessage());
        }
    }
    
    public boolean isEnabled() {
        return connection != null;
    }
    
    public record BlockAction(long timestamp, String username, String action, String material) {}
    
    public static class PlayerStats {
        public int blocksBroken = 0;
        public int blocksPlaced = 0;
    }
}
