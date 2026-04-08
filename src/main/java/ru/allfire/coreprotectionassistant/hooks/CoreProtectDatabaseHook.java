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
    
    public CoreProtectDatabaseHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        // Пробуем SQLite
        File coreProtectFolder = new File("plugins/CoreProtect");
        File dbFile = new File(coreProtectFolder, "database.db");
        
        if (dbFile.exists()) {
            plugin.getLogger().info("Found CoreProtect SQLite database: " + dbFile.getAbsolutePath());
            return initSQLite(dbFile);
        }
        
        // Пробуем MySQL
        plugin.getLogger().info("CoreProtect SQLite not found, trying MySQL...");
        return initMySQL();
    }
    
    private boolean initSQLite(File dbFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath() + "?mode=ro";
            connection = DriverManager.getConnection(url);
            
            // Проверяем, какие таблицы реально существуют
            checkTables();
            
            // Загружаем кэш
            loadCache();
            
            plugin.getLogger().info("CoreProtect SQLite hook initialized. Users: " + userCache.size() + 
                ", Worlds: " + worldCache.size() + ", Materials: " + materialCache.size());
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to CoreProtect SQLite: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean initMySQL() {
        File configFile = new File("plugins/CoreProtect/config.yml");
        if (!configFile.exists()) {
            plugin.getLogger().warning("CoreProtect config.yml not found!");
            return false;
        }
        
        try {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
            
            // Проверяем, включён ли MySQL
            boolean useMySQL = config.getBoolean("use-mysql", false);
            if (!useMySQL) {
                plugin.getLogger().warning("CoreProtect is not using MySQL!");
                return false;
            }
            
            String host = config.getString("host", "localhost");
            int port = config.getInt("port", 3306);
            String database = config.getString("database", "coreprotect");
            String username = config.getString("username", "root");
            String password = config.getString("password", "");
            
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                "?useSSL=false&allowPublicKeyRetrieval=true";
            
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, username, password);
            connection.setReadOnly(true);
            
            plugin.getLogger().info("Connected to CoreProtect MySQL database: " + database);
            
            // Проверяем таблицы
            checkTables();
            
            // Загружаем кэш
            loadCache();
            
            plugin.getLogger().info("CoreProtect MySQL hook initialized. Users: " + userCache.size() + 
                ", Worlds: " + worldCache.size() + ", Materials: " + materialCache.size());
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to CoreProtect MySQL: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void checkTables() {
        try (Statement stmt = connection.createStatement()) {
            // Проверяем существование таблиц простым SELECT
            try {
                stmt.executeQuery("SELECT 1 FROM co_user LIMIT 1");
                userTable = "co_user";
            } catch (SQLException e) {
                try {
                    stmt.executeQuery("SELECT 1 FROM user LIMIT 1");
                    userTable = "user";
                } catch (SQLException e2) {
                    plugin.getLogger().warning("Cannot find user table!");
                }
            }
            
            try {
                stmt.executeQuery("SELECT 1 FROM co_world LIMIT 1");
                worldTable = "co_world";
            } catch (SQLException e) {
                try {
                    stmt.executeQuery("SELECT 1 FROM world LIMIT 1");
                    worldTable = "world";
                } catch (SQLException e2) {
                    plugin.getLogger().warning("Cannot find world table!");
                }
            }
            
            try {
                stmt.executeQuery("SELECT 1 FROM co_material LIMIT 1");
                materialTable = "co_material";
            } catch (SQLException e) {
                try {
                    stmt.executeQuery("SELECT 1 FROM material LIMIT 1");
                    materialTable = "material";
                } catch (SQLException e2) {
                    try {
                        stmt.executeQuery("SELECT 1 FROM co_material_map LIMIT 1");
                        materialTable = "co_material_map";
                    } catch (SQLException e3) {
                        plugin.getLogger().warning("Cannot find material table!");
                    }
                }
            }
            
            try {
                stmt.executeQuery("SELECT 1 FROM co_block LIMIT 1");
                blockTable = "co_block";
            } catch (SQLException e) {
                try {
                    stmt.executeQuery("SELECT 1 FROM block LIMIT 1");
                    blockTable = "block";
                } catch (SQLException e2) {
                    plugin.getLogger().warning("Cannot find block table!");
                }
            }
            
            plugin.getLogger().info("Using tables: " + userTable + ", " + worldTable + 
                ", " + materialTable + ", " + blockTable);
                
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to check tables: " + e.getMessage());
        }
    }
    
    private void loadCache() {
        // Загружаем пользователей
        if (userTable != null) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, user FROM " + userTable)) {
                while (rs.next()) {
                    userCache.put(rs.getInt("id"), rs.getString("user"));
                }
                plugin.getLogger().info("Loaded " + userCache.size() + " users from " + userTable);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load users: " + e.getMessage());
            }
        }
        
        // Загружаем миры
        if (worldTable != null) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, world FROM " + worldTable)) {
                while (rs.next()) {
                    worldCache.put(rs.getInt("id"), rs.getString("world"));
                }
                plugin.getLogger().info("Loaded " + worldCache.size() + " worlds from " + worldTable);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load worlds: " + e.getMessage());
            }
        }
        
        // Загружаем материалы
        if (materialTable != null) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, material FROM " + materialTable)) {
                while (rs.next()) {
                    materialCache.put(rs.getInt("id"), rs.getString("material"));
                }
                plugin.getLogger().info("Loaded " + materialCache.size() + " materials from " + materialTable);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load materials: " + e.getMessage());
            }
        }
    }
    
    public CompletableFuture<List<BlockAction>> getBlockHistory(Location loc) {
        return CompletableFuture.supplyAsync(() -> {
            List<BlockAction> history = new ArrayList<>();
            
            if (connection == null || blockTable == null) return history;
            
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
        return connection != null && userTable != null && !userCache.isEmpty();
    }
    
    public record BlockAction(long timestamp, String username, String action, String material) {}
}
