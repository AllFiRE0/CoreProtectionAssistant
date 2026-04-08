package ru.allfire.coreprotectionassistant.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;
import ru.allfire.coreprotectionassistant.enums.ReportStatus;
import ru.allfire.coreprotectionassistant.models.PlayerReport;
import ru.allfire.coreprotectionassistant.utils.Color;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReportManager {
    
    private final CoreProtectionAssistant plugin;
    private final Map<UUID, Long> lastReportTime = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> reportHistory = new ConcurrentHashMap<>();
    
    private boolean enabled;
    private int cooldownSeconds;
    private int maxReportsPerHour;
    private int maxReportsPerTargetHour;
    private int abuseThreshold;
    private int cleanupDays;
    
    private Map<String, ReportCategory> categories = new HashMap<>();
    
    public ReportManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        var config = plugin.getConfigManager().getReportsConfig();
        
        enabled = config.getBoolean("reports.enabled", true);
        cooldownSeconds = config.getInt("reports.cooldown_seconds", 60);
        maxReportsPerHour = config.getInt("reports.max_reports_per_player_per_hour", 5);
        maxReportsPerTargetHour = config.getInt("reports.max_reports_per_target_per_hour", 10);
        abuseThreshold = config.getInt("reports.report_abuse_threshold", 8);
        cleanupDays = config.getInt("reports.cleanup_days", 30);
        
        var categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String key : categoriesSection.getKeys(false)) {
                String regex = categoriesSection.getString(key + ".regex", ".*");
                int weight = categoriesSection.getInt(key + ".weight", 1);
                String description = categoriesSection.getString(key + ".description", key);
                String color = categoriesSection.getString(key + ".color", "&7");
                
                categories.put(key, new ReportCategory(key, regex, weight, description, color));
            }
        }
    }
    
    public void reload() {
        loadConfig();
    }
    
    public ReportResult createReport(Player reporter, OfflinePlayer target, String reason) {
        if (!enabled) {
            return new ReportResult(false, "Reports are disabled");
        }
        
        Long lastTime = lastReportTime.get(reporter.getUniqueId());
        long now = System.currentTimeMillis();
        
        if (lastTime != null) {
            long secondsPassed = (now - lastTime) / 1000;
            if (secondsPassed < cooldownSeconds) {
                long remaining = cooldownSeconds - secondsPassed;
                String msg = plugin.getConfigManager().getLangConfig()
                    .getString("messages.report_cooldown", "%prefix% &cWait %seconds% seconds")
                    .replace("%seconds%", String.valueOf(remaining));
                return new ReportResult(false, msg);
            }
        }
        
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            if (!checkReportLimits(reporter, onlineTarget)) {
                return new ReportResult(false, Color.colorize(
                    plugin.getConfigManager().getLangConfig()
                        .getString("messages.report_target_limit", 
                            "%prefix% &cToo many reports for this player")
                ));
            }
        }
        
        ReportCategory category = detectCategory(reason);
        Location loc = reporter.getLocation();
        
        PlayerReport report = PlayerReport.builder()
            .reporterUuid(reporter.getUniqueId())
            .reporterName(reporter.getName())
            .targetUuid(target.getUniqueId())
            .targetName(target.getName() != null ? target.getName() : "Unknown")
            .category(category.name)
            .reason(reason)
            .world(loc.getWorld().getName())
            .x(loc.getX())
            .y(loc.getY())
            .z(loc.getZ())
            .status(ReportStatus.PENDING)
            .timestamp(now)
            .build();
        
        long reportId = saveReport(report);
        
        if (reportId > 0) {
            lastReportTime.put(reporter.getUniqueId(), now);
            reportHistory.computeIfAbsent(reporter.getUniqueId(), k -> new ArrayList<>()).add(now);
            notifyStaff(reportId, report);
            checkReportAbuse(reporter, onlineTarget);
            
            if (onlineTarget != null) {
                analyzeViolator(onlineTarget);
            }
            
            String successMsg = plugin.getConfigManager().getLangConfig()
                .getString("messages.report_success", 
                    "%prefix% &aReport sent. ID: &f%id%")
                .replace("%target%", target.getName() != null ? target.getName() : "Unknown")
                .replace("%id%", String.valueOf(reportId));
            
            return new ReportResult(true, Color.colorize(successMsg));
        }
        
        return new ReportResult(false, "Failed to save report");
    }
    
    private long saveReport(PlayerReport report) {
        String sql = """
            INSERT INTO cpa_reports 
            (reporter_uuid, reporter_name, target_uuid, target_name, category, reason, 
             world, x, y, z, status, timestamp) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, report.getReporterUuid().toString());
            ps.setString(2, report.getReporterName());
            ps.setString(3, report.getTargetUuid().toString());
            ps.setString(4, report.getTargetName());
            ps.setString(5, report.getCategory());
            ps.setString(6, report.getReason());
            ps.setString(7, report.getWorld());
            ps.setDouble(8, report.getX());
            ps.setDouble(9, report.getY());
            ps.setDouble(10, report.getZ());
            ps.setString(11, report.getStatus().name());
            ps.setLong(12, report.getTimestamp());
            
            ps.executeUpdate();
            
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save report: " + e.getMessage());
        }
        
        return -1;
    }
    
    private ReportCategory detectCategory(String reason) {
        String lowerReason = reason.toLowerCase();
        
        for (ReportCategory category : categories.values()) {
            if (lowerReason.matches(".*" + category.regex + ".*")) {
                return category;
            }
        }
        
        return categories.getOrDefault("other", 
            new ReportCategory("other", ".*", 1, "Other", "&7"));
    }
    
    private boolean checkReportLimits(Player reporter, Player target) {
        long hourAgo = System.currentTimeMillis() - 3600000;
        
        List<Long> times = reportHistory.getOrDefault(reporter.getUniqueId(), List.of());
        long reportsLastHour = times.stream().filter(t -> t > hourAgo).count();
        
        if (reportsLastHour >= maxReportsPerHour) {
            return false;
        }
        
        int reportsOnTarget = getReportsCount(target.getUniqueId(), hourAgo);
        
        return reportsOnTarget < maxReportsPerTargetHour;
    }
    
    private int getReportsCount(UUID targetUuid, long since) {
        String sql = "SELECT COUNT(*) FROM cpa_reports WHERE target_uuid = ? AND timestamp > ?";
        
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, targetUuid.toString());
            ps.setLong(2, since);
            
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get reports count: " + e.getMessage());
        }
        
        return 0;
    }
    
    private void notifyStaff(long reportId, PlayerReport report) {
        boolean notifyStaff = plugin.getConfigManager().getReportsConfig()
            .getBoolean("reports.notify_staff", true);
        
        if (!notifyStaff) return;
        
        String prefix = Lang.getPrefix();
        
        String notifyMsg = plugin.getConfigManager().getLangConfig()
            .getString("messages.report_notify_staff",
                "%prefix% &e[REPORT #%id%] &f%reporter% &7→ &c%target% &8| &7%reason%")
            .replace("%prefix%", prefix)
            .replace("%id%", String.valueOf(reportId))
            .replace("%reporter%", report.getReporterName())
            .replace("%target%", report.getTargetName())
            .replace("%reason%", report.getReason());
        
        String finalMsg = Color.colorize(notifyMsg);
        
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("cpa.report.see")) {
                staff.sendMessage(finalMsg);
            }
        }
        
        Bukkit.getConsoleSender().sendMessage(finalMsg);
    }
    
    private void checkReportAbuse(Player reporter, Player target) {
        long hourAgo = System.currentTimeMillis() - 3600000;
        List<Long> times = reportHistory.getOrDefault(reporter.getUniqueId(), List.of());
        long reportsLastHour = times.stream().filter(t -> t > hourAgo).count();
        
        if (reportsLastHour >= abuseThreshold) {
            var antiAbuse = plugin.getConfigManager().getReportsConfig()
                .getConfigurationSection("anti_abuse.actions");
            
            if (antiAbuse != null) {
                var warnConfig = antiAbuse.getConfigurationSection("warn_reporter");
                if (warnConfig != null && warnConfig.getBoolean("enabled", true)) {
                    int threshold = warnConfig.getInt("threshold", 5);
                    
                    if (reportsLastHour >= threshold) {
                        plugin.getWarnManager().warnPlayer(
                            reporter.getUniqueId(),
                            reporter.getName(),
                            null,
                            "CONSOLE",
                            "Report abuse",
                            36000
                        );
                    }
                }
            }
        }
    }
    
    private void analyzeViolator(Player target) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var analysis = plugin.getConfigManager().getReportsConfig()
                .getConfigurationSection("violator_analysis");
            
            if (analysis == null || !analysis.getBoolean("enabled", true)) return;
            
            long hourAgo = System.currentTimeMillis() - 3600000;
            int reportsCount = getReportsCount(target.getUniqueId(), hourAgo);
            
            int maxReportsThreshold = analysis.getInt("max_reports_threshold", 5);
            
            if (reportsCount >= maxReportsThreshold) {
                var actions = analysis.getConfigurationSection("actions.auto_warn");
                if (actions != null && actions.getBoolean("enabled", true)) {
                    int threshold = actions.getInt("threshold", 3);
                    
                    if (reportsCount >= threshold) {
                        plugin.getWarnManager().warnPlayer(
                            target.getUniqueId(),
                            target.getName(),
                            null,
                            "CONSOLE",
                            "Many player reports",
                            36000
                        );
                    }
                }
            }
        });
    }
    
    public void cleanupOldReports() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long cutoff = System.currentTimeMillis() - (cleanupDays * 24L * 3600000);
            
            String sql = "DELETE FROM cpa_reports WHERE timestamp < ? AND status IN ('RESOLVED', 'REJECTED')";
            
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setLong(1, cutoff);
                int deleted = ps.executeUpdate();
                
                if (deleted > 0) {
                    plugin.getLogger().info("Cleaned up " + deleted + " old reports");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to cleanup old reports: " + e.getMessage());
            }
        });
    }
    
    public record ReportCategory(String name, String regex, int weight, 
                                  String description, String color) {}
    
    public record ReportResult(boolean success, String message) {}
}