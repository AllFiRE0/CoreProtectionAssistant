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
    private Connection connection;
    
    private final Map<Integer, String> userCache = new HashMap<>();
    private final Map<Integer, String> worldCache = new HashMap<>();
    private final Map<Integer, String> materialCache = new HashMap<>();
    
    private String userTable = "co_user";
    private String worldTable = "co_world";
    private String materialTable = "co_material";
    private String blockTable = "co_block";
    
    private boolean mysqlMode = false;
    
    public CoreProtectDatabaseHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        // Сначала пробуем SQLite
        File coreProtectFolder = new File("plugins/CoreProtect");
        File dbFile = new File(coreProtectFolder, "database.db");
        
        if (dbFile.exists()) {
            return initSQLite(dbFile);
        }
        
        // Если SQLite нет — пробуем MySQL из конфига CoreProtect
        return initMySQL();
    }
    
    private boolean initSQLite(File dbFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath() + "?mode=ro";
            connection = DriverManager.getConnection(url);
            mysqlMode = false;
            
            // Определяем реальные имена таблиц
            detectTableNames();
            
            // Загружаем кэш
            loadCache();
            
            plugin.getLogger().info("CoreProtect SQLite hook initialized. Found " + 
                userCache.size() + " users, " + worldCache.size() + " worlds, " + 
                materialCache.size() + " materials");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to CoreProtect SQLite: " + e.getMessage());
            return false;
        }
    }
    
    private boolean initMySQL() {
        // Читаем конфиг CoreProtect
        File configFile = new File("plugins/CoreProtect/config.yml");
        if (!configFile.exists()) {
            plugin.getLogger().warning("CoreProtect config.yml not found!");
            return false;
        }
        
        try {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
            
            String host = config.getString("host", "localhost");
            int port = config.getInt("port", 3306);
            String database = config.getString("database", "coreprotect");
            String username = config.getString("username", "root");
            String password = config.getString("password", "");
            
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true";
            
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, username, password);
            connection.setReadOnly(true);
            mysqlMode = true;
            
            // Определяем реальные имена таблиц
            detectTableNames();
            
            // Загружаем кэш
            loadCache();
            
            plugin.getLogger().info("CoreProtect MySQL hook initialized. Found " + 
                userCache.size() + " users, " + worldCache.size() + " worlds, " + 
                materialCache.size() + " materials");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to CoreProtect MySQL: " + e.getMessage());
            return false;
        }
    }
    
    private void detectTableNames() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = connection.getMetaData().getTables(null, null, "%", null)) {
            
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                
                if (tableName.equals("co_user") || tableName.equals("user")) {
                    userTable = tableName;
                } else if (tableName.equals("co_world") || tableName.equals("world")) {
                    worldTable = tableName;
                } else if (tableName.equals("co_material") || tableName.equals("material") || 
                           tableName.equals("co_material_map")) {
                    materialTable = tableName;
                } else if (tableName.equals("co_block") || tableName.equals("block")) {
                    blockTable = tableName;
                }
            }
            
            plugin.getLogger().info("Detected tables: " + userTable + ", " + worldTable + 
                ", " + materialTable + ", " + blockTable);
                
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to detect table names: " + e.getMessage());
        }
    }
    
    private void loadCache() {
        // Загружаем пользователей
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, user FROM " + userTable)) {
            while (rs.next()) {
                userCache.put(rs.getInt("id"), rs.getString("user"));
            }
            plugin.getLogger().info("Loaded " + userCache.size() + " users");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load users: " + e.getMessage());
        }
        
        // Загружаем миры
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, world FROM " + worldTable)) {
            while (rs.next()) {
                worldCache.put(rs.getInt("id"), rs.getString("world"));
            }
            plugin.getLogger().info("Loaded " + worldCache.size() + " worlds");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load worlds: " + e.getMessage());
        }
        
        // Загружаем материалы
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, material FROM " + materialTable)) {
            while (rs.next()) {
                materialCache.put(rs.getInt("id"), rs.getString("material"));
            }
            plugin.getLogger().info("Loaded " + materialCache.size() + " materials");
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
            
            if (connection == null) return history;
            
            // Находим ID мира
            int worldId = -1;
            for (Map.Entry<Integer, String> entry : worldCache.entrySet()) {
                if (entry.getValue().equals(loc.getWorld().getName())) {
                    worldId = entry.getKey();
                    break;
                }
            }
            
            if (worldId == -1) return history;
            
            String sql = "SELECT b.time, b.user, b.type, b.data, u.user as username " +
                        "FROM " + blockTable + " b " +
                        "JOIN " + userTable + " u ON b.user = u.id " +
                        "WHERE b.wid = ? AND b.x = ? AND b.y = ? AND b.z = ? " +
                        "ORDER BY b.time ASC";
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, worldId);
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long time = rs.getLong("time") * 1000;
                        String username = rs.getString("username");
                        int type = rs.getInt("type");
                        int materialId = rs.getInt("data");
                        String material = materialCache.getOrDefault(materialId, "UNKNOWN");
                        
                        String action = type == 0 ? "BREAK" : (type == 1 ? "PLACE" : "INTERACT");
                        history.add(new BlockAction(time, username, action, material));
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
                if (!action.username().equalsIgnoreCase(currentPlayer)) {
                    return true;
                }
            }
            return false;
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
}
