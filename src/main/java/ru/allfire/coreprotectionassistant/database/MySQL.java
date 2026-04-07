package ru.allfire.coreprotectionassistant.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQL implements IDatabase {
    
    private final CoreProtectionAssistant plugin;
    private HikariDataSource dataSource;
    
    public MySQL(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean connect() {
        try {
            var config = plugin.getConfigManager().getMainConfig();
            
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" +
                config.getString("database.mysql.host") + ":" +
                config.getInt("database.mysql.port") + "/" +
                config.getString("database.mysql.database") +
                "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true"
            );
            hikariConfig.setUsername(config.getString("database.mysql.username"));
            hikariConfig.setPassword(config.getString("database.mysql.password"));
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(5000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(hikariConfig);
            
            try (Connection conn = dataSource.getConnection()) {
                createTables();
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to MySQL: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    @Override
    public void createTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_command_logs (
                    id INT AUTO_INCREMENT PRIMARY KEY,
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
                    is_staff BOOLEAN,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_super_commands (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    command VARCHAR(64),
                    args TEXT,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp BIGINT,
                    INDEX idx_player_uuid (player_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_player_sessions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    action VARCHAR(16),
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp BIGINT,
                    INDEX idx_player_uuid (player_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_chat_violations (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    rule_name VARCHAR(64),
                    punishment VARCHAR(32),
                    timestamp BIGINT,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_staff_actions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    staff_uuid VARCHAR(36),
                    staff_name VARCHAR(32),
                    action VARCHAR(32),
                    target VARCHAR(32),
                    details TEXT,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp BIGINT,
                    INDEX idx_staff_uuid (staff_uuid),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_warnings (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    staff_uuid VARCHAR(36),
                    staff_name VARCHAR(32),
                    reason TEXT,
                    active BOOLEAN DEFAULT 1,
                    created_at BIGINT,
                    expires_at BIGINT,
                    cleared_at BIGINT,
                    cleared_by VARCHAR(32),
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_active (active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_reports (
                    id INT AUTO_INCREMENT PRIMARY KEY,
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
                    resolved_by VARCHAR(32),
                    INDEX idx_target_uuid (target_uuid),
                    INDEX idx_status (status),
                    INDEX idx_timestamp (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_prohibited_perms (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    permission VARCHAR(128),
                    timestamp BIGINT,
                    INDEX idx_player_uuid (player_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_abuse_scores (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(32),
                    score INT DEFAULT 0,
                    last_updated BIGINT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cpa_apologies (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(32),
                    target_name VARCHAR(32),
                    rule_name VARCHAR(64),
                    warnings_cleared INT,
                    timestamp BIGINT,
                    INDEX idx_player_uuid (player_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            
            plugin.getLogger().info("MySQL tables created successfully");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create MySQL tables: " + e.getMessage());
        }
    }
}
