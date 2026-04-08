package ru.allfire.coreprotectionassistant.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLite implements IDatabase {
    
    private final CoreProtectionAssistant plugin;
    private HikariDataSource dataSource;
    
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
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            config.setConnectionTimeout(5000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setPoolName("CPA-SQLite");
            
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("busy_timeout", "5000");
            config.addDataSourceProperty("synchronous", "NORMAL");
            
            dataSource = new HikariDataSource(config);
            
            try (Connection conn = dataSource.getConnection()) {
                createTables(conn);
                plugin.getLogger().info("SQLite tables created successfully");
            }
            
            plugin.getLogger().info("SQLite connected with connection pool");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to SQLite: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("SQLite connection pool closed");
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            plugin.getLogger().warning("SQLite connection pool is closed, reconnecting...");
            if (!connect()) {
                throw new SQLException("Failed to reconnect to SQLite database");
            }
        }
        return dataSource.getConnection();
    }
    
    @Override
    public void createTables() {
        try (Connection conn = getConnection()) {
            createTables(conn);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }
    
    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_command_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), player_name VARCHAR(32), command VARCHAR(64), args TEXT, full_command TEXT, world VARCHAR(64), x DOUBLE, y DOUBLE, z DOUBLE, timestamp BIGINT, is_staff BOOLEAN)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_super_commands (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), player_name VARCHAR(32), command VARCHAR(64), args TEXT, world VARCHAR(64), x DOUBLE, y DOUBLE, z DOUBLE, timestamp BIGINT)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_player_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), player_name VARCHAR(32), action VARCHAR(16), world VARCHAR(64), x DOUBLE, y DOUBLE, z DOUBLE, timestamp BIGINT)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_chat_violations (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), player_name VARCHAR(32), rule_name VARCHAR(64), punishment VARCHAR(32), timestamp BIGINT)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_staff_actions (id INTEGER PRIMARY KEY AUTOINCREMENT, staff_uuid VARCHAR(36), staff_name VARCHAR(32), action VARCHAR(32), target VARCHAR(32), details TEXT, world VARCHAR(64), x DOUBLE, y DOUBLE, z DOUBLE, timestamp BIGINT)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_warnings (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), player_name VARCHAR(32), staff_uuid VARCHAR(36), staff_name VARCHAR(32), reason TEXT, active BOOLEAN DEFAULT 1, created_at BIGINT, expires_at BIGINT, cleared_at BIGINT, cleared_by VARCHAR(32))");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, reporter_uuid VARCHAR(36), reporter_name VARCHAR(32), target_uuid VARCHAR(36), target_name VARCHAR(32), category VARCHAR(32), reason TEXT, world VARCHAR(64), x DOUBLE, y DOUBLE, z DOUBLE, status VARCHAR(16) DEFAULT 'PENDING', timestamp BIGINT, resolved_at BIGINT, resolved_by VARCHAR(32))");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_prohibited_perms (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), permission VARCHAR(128), timestamp BIGINT)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_abuse_scores (player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(32), score INTEGER DEFAULT 0, last_updated BIGINT)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cpa_apologies (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), player_name VARCHAR(32), target_name VARCHAR(32), rule_name VARCHAR(64), warnings_cleared INTEGER, timestamp BIGINT)");
            
        }
    }
}
