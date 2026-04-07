public ReportResult createReport(Player reporter, OfflinePlayer target, String reason) {
    if (!enabled) {
        return new ReportResult(false, "Reports are disabled");
    }
    
    // Проверяем кулдаун
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
    
    // Проверяем лимиты (только если цель онлайн)
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
    
    // Определяем категорию
    ReportCategory category = detectCategory(reason);
    
    // Создаем жалобу
    Location loc = reporter.getLocation();
    
    PlayerReport report = PlayerReport.builder()
        .reporterUuid(reporter.getUniqueId())
        .reporterName(reporter.getName())
        .targetUuid(target.getUniqueId())
        .targetName(target.getName())
        .category(category.name)
        .reason(reason)
        .world(loc.getWorld().getName())
        .x(loc.getX())
        .y(loc.getY())
        .z(loc.getZ())
        .status(ReportStatus.PENDING)
        .timestamp(now)
        .build();
    
    // Сохраняем в БД
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
            .replace("%target%", target.getName())
            .replace("%id%", String.valueOf(reportId));
        
        return new ReportResult(true, Color.colorize(successMsg));
    }
    
    return new ReportResult(false, "Failed to save report");
}
