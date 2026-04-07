package ru.allfire.coreprotectionassistant.database;

import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLite implements IDatabase {
    
    private final CoreProtectionAssistant plugin;
    private Connection connection;
    
    public SQLite(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File dbFile = new File(dataFolder, "database.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to SQLite: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }
    
    @Override
    public void createTables() {
        try (Statement stmt = connection.createStatement()) {
            
            // Командные логи
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_command_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    command VARCHAR(64),
                    args TEXT,
                    full_command TEXT,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp BIGINT,
                    is_staff BOOLEAN
                )
            """);
            
            // Супер-команды
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_super_commands (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    command VARCHAR(64),
                    args TEXT,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp BIGINT
                )
            """);
            
            // Сессии игроков
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_player_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    action VARCHAR(16),
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp BIGINT
                )
            """);
            
            // Нарушения чата
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_chat_violations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    rule_name VARCHAR(64),
                    punishment VARCHAR(32),
                    timestamp BIGINT
                )
            """);
            
            // Действия персонала
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_staff_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    staff_uuid VARCHAR(36),
                    staff_name VARCHAR(32),
                    action VARCHAR(32),
                    target VARCHAR(32),
                    details TEXT,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp BIGINT
                )
            """);
            
            // Предупреждения
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_warnings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    staff_uuid VARCHAR(36),
                    staff_name VARCHAR(32),
                    reason TEXT,
                    active BOOLEAN DEFAULT 1,
                    created_at BIGINT,
                    expires_at BIGINT,
                    cleared_at BIGINT,
                    cleared_by VARCHAR(32)
                )
            """);
            
            // Жалобы
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reporter_uuid VARCHAR(36),
                    reporter_name VARCHAR(32),
                    target_uuid VARCHAR(36),
                    target_name VARCHAR(32),
                    category VARCHAR(32),
                    reason TEXT,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    status VARCHAR(16) DEFAULT 'PENDING',
                    timestamp BIGINT,
                    resolved_at BIGINT,
                    resolved_by VARCHAR(32)
                )
            """);
            
            // Запрещенные права
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_prohibited_perms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    permission VARCHAR(128),
                    timestamp BIGINT
                )
            """);
            
            // Abuse Score
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_abuse_scores (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(32),
                    score INTEGER DEFAULT 0,
                    last_updated BIGINT
                )
            """);
            
            // Извинения
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_apologies (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    target_name VARCHAR(32),
                    rule_name VARCHAR(64),
                    warnings_cleared INTEGER,
                    timestamp BIGINT
                )
            """);
            
            plugin.getLogger().info("SQLite tables created successfully");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }
}
