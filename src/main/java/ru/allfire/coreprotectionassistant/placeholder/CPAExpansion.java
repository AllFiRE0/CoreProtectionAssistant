package ru.allfire.coreprotectionassistant.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.TimeUtil;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CPAExpansion extends PlaceholderExpansion {
    
    private final CoreProtectionAssistant plugin;
    private final ConcurrentHashMap<String, String> cachedValues = new ConcurrentHashMap<>();
    private boolean debug;
    
    public CPAExpansion(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfigManager().getMainConfig().getBoolean("debug", false);
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "cpa";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return "AllF1RE";
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public boolean canRegister() {
        return true;
    }
    
    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }
        
        UUID uuid = offlinePlayer.getUniqueId();
        
        if (debug) {
            plugin.getLogger().info("[PAPI] Request for " + offlinePlayer.getName() + ": " + params);
        }
        
        String result = processPlaceholder(uuid, params);
        
        if (debug) {
            plugin.getLogger().info("[PAPI] Result: " + (result.isEmpty() ? "<empty>" : result));
        }
        
        return result;
    }
    
    private String processPlaceholder(UUID uuid, String params) {
        // Если параметр содержит имя игрока (например, player_warnings_count_AllF1RE)
        if (params.contains("_")) {
            String[] parts = params.split("_");
            
            // Проверяем, не указано ли имя игрока в конце
            if (parts.length >= 3) {
                String possiblePlayerName = parts[parts.length - 1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(possiblePlayerName);
                if (target != null && target.hasPlayedBefore()) {
                    uuid = target.getUniqueId();
                    params = String.join("_", java.util.Arrays.copyOf(parts, parts.length - 1));
                }
            }
        }
        
        // Основная обработка
        if (params.startsWith("player_")) {
            return processPlayerPlaceholder(uuid, params.substring(7));
        } else if (params.startsWith("staff_")) {
            return processStaffPlaceholder(uuid, params.substring(6));
        } else if (params.startsWith("warnings_")) {
            return processWarningsPlaceholder(uuid, params.substring(9));
        }
        
        return "";
    }
    
    private String processPlayerPlaceholder(UUID uuid, String param) {
        var hook = plugin.getCoreProtectHook();
        
        // Убираем возможный суффикс с именем игрока
        if (param.contains("_")) {
            String[] parts = param.split("_");
            OfflinePlayer target = Bukkit.getOfflinePlayer(parts[parts.length - 1]);
            if (target != null && target.hasPlayedBefore()) {
                uuid = target.getUniqueId();
                param = String.join("_", java.util.Arrays.copyOf(parts, parts.length - 1));
            }
        }
        
        switch (param) {
            case "blocks_broken":
                return String.valueOf(hook.getBlocksBroken(uuid, 0).join());
            case "blocks_placed":
                return String.valueOf(hook.getBlocksPlaced(uuid, 0).join());
            case "chests_opened":
                return String.valueOf(hook.getChestsOpened(uuid, 0).join());
            case "commands_count":
                return String.valueOf(hook.getCommandsUsed(uuid, 0).join());
            case "deaths":
                return String.valueOf(hook.getDeaths(uuid, 0).join());
            case "kills":
                return String.valueOf(hook.getKills(uuid, 0).join());
            case "first_seen":
                long first = hook.getFirstSeen(uuid).join();
                return first > 0 ? TimeUtil.formatDateTime(first) : plugin.getConfigManager().getLangConfig().getString("time_never", "Never");
            case "last_seen":
                long last = hook.getLastSeen(uuid).join();
                return last > 0 ? TimeUtil.formatDateTime(last) : plugin.getConfigManager().getLangConfig().getString("time_never", "Never");
            case "warnings_count":
                return String.valueOf(plugin.getWarnManager().getActiveWarningsCount(uuid).join());
            case "violations_count":
                return String.valueOf(plugin.getDatabaseManager().getViolationCount(uuid).join());
            case "apologies_count":
                return String.valueOf(plugin.getDatabaseManager().getApologiesCount(uuid).join());
            case "violations_apologies_ratio":
                return plugin.getDatabaseManager().getViolationsApologiesRatio(uuid).join();
            case "time_since_last_violation":
                long lastTime = plugin.getDatabaseManager().getLastViolationTime(uuid).join();
                return lastTime > 0 ? String.valueOf((System.currentTimeMillis() - lastTime) / 1000) : "0";
            default:
                if (param.startsWith("cmd_")) {
                    String command = param.substring(4);
                    return String.valueOf(plugin.getDatabaseManager().getCommandCount(uuid, command).join());
                }
                return "0";
        }
    }
    
    private String processStaffPlaceholder(UUID uuid, String param) {
        var stats = plugin.getStaffManager().getStaffStats(uuid);
        int abuseScore = plugin.getAbuseScoreManager().getScore(uuid);
        
        switch (param) {
            case "bans_count":
                return String.valueOf(stats.bansCount);
            case "mutes_count":
                return String.valueOf(stats.mutesCount);
            case "kicks_count":
                return String.valueOf(stats.kicksCount);
            case "gives_count":
                return String.valueOf(stats.givesCount);
            case "abuse_score":
                return String.valueOf(abuseScore);
            case "warnings_count":
                return String.valueOf(plugin.getWarnManager().getActiveWarningsCount(uuid).join());
            default:
                return "0";
        }
    }
    
    private String processWarningsPlaceholder(UUID uuid, String param) {
        switch (param) {
            case "count":
                return String.valueOf(plugin.getWarnManager().getActiveWarningsCount(uuid).join());
            default:
                return "0";
        }
    }
}
