// ========== APOLOGIES ==========

public CompletableFuture<Void> logApology(Player player, String ruleName, int warningsCleared) {
    return CompletableFuture.runAsync(() -> {
        String sql = "INSERT INTO cpa_apologies (player_uuid, player_name, rule_name, warnings_cleared, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, player.getName());
            ps.setString(3, ruleName);
            ps.setInt(4, warningsCleared);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log apology: " + e.getMessage());
        }
    });
}

public CompletableFuture<Integer> getApologiesCount(UUID uuid) {
    return CompletableFuture.supplyAsync(() -> {
        String sql = "SELECT COUNT(*) FROM cpa_apologies WHERE player_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get apologies count: " + e.getMessage());
        }
        return 0;
    });
}

public CompletableFuture<String> getViolationsApologiesRatio(UUID uuid) {
    return CompletableFuture.supplyAsync(() -> {
        int violations = getViolationCount(uuid).join();
        int apologies = getApologiesCount(uuid).join();
        if (violations == 0) return "0%";
        int ratio = (apologies * 100) / violations;
        return Math.min(ratio, 100) + "%";
    });
}
