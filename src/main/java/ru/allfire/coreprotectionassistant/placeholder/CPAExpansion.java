package ru.allfire.coreprotectionassistant.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.TimeUtil;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CPAExpansion extends PlaceholderExpansion {
    
    private final CoreProtectionAssistant plugin;
    private final ConcurrentHashMap<String, String> cachedValues = new ConcurrentHashMap<>();
    
    public CPAExpansion(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
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
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";
        
        UUID uuid = offlinePlayer.getUniqueId();
        
        String cacheKey = uuid + ":" + params;
        String cached = cachedValues.get(cacheKey);
        if (cached != null) return cached;
        
        String result = processPlaceholder(uuid, params);
        
        cachedValues.put(cacheKey, result);
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
            () -> cachedValues.remove(cacheKey), 20L);
        
        return result;
    }
    
    private String processPlaceholder(UUID uuid, String params) {
        String[] parts = params.split("_", 2);
        String category = parts[0];
        String subParam = parts.length > 1 ? parts[1] : "";
        
        return switch (category) {
            case "player" -> processPlayerPlaceholder(uuid, subParam);
            case "staff" -> processStaffPlaceholder(uuid, subParam);
            case "warnings" -> processWarningsPlaceholder(uuid, subParam);
            default -> "";
        };
    }
    
    private String processPlayerPlaceholder(UUID uuid, String param) {
        var hook = plugin.getCoreProtectHook();
        
        return switch (param) {
            case "blocks_broken" -> String.valueOf(hook.getBlocksBroken(uuid, 0).join());
            case "blocks_placed" -> String.valueOf(hook.getBlocksPlaced(uuid, 0).join());
            case "chests_opened" -> String.valueOf(hook.getChestsOpened(uuid, 0).join());
            case "commands_count" -> String.valueOf(hook.getCommandsUsed(uuid, 0).join());
            case "deaths" -> String.valueOf(hook.getDeaths(uuid, 0).join());
            case "kills" -> String.valueOf(hook.getKills(uuid, 0).join());
            case "first_seen" -> TimeUtil.formatDateTime(hook.getFirstSeen(uuid).join());
            case "last_seen" -> TimeUtil.formatDateTime(hook.getLastSeen(uuid).join());
            case "warnings_count" -> String.valueOf(plugin.getWarnManager().getActiveWarningsCount(uuid).join());
            case "violations_count" -> String.valueOf(plugin.getDatabaseManager().getViolationCount(uuid).join());
            case "apologies_count" -> String.valueOf(plugin.getDatabaseManager().getApologiesCount(uuid).join());
            case "violations_apologies_ratio" -> plugin.getDatabaseManager().getViolationsApologiesRatio(uuid).join();
            case "time_since_last_violation" -> {
                long lastTime = plugin.getDatabaseManager().getLastViolationTime(uuid).join();
                yield lastTime > 0 ? String.valueOf((System.currentTimeMillis() - lastTime) / 1000) : "0";
            }
            default -> {
                if (param.startsWith("cmd_")) {
                    String command = param.substring(4);
                    yield String.valueOf(plugin.getDatabaseManager().getCommandCount(uuid, command).join());
                }
                yield "";
            }
        };
    }
    
    private String processStaffPlaceholder(UUID uuid, String param) {
        var stats = plugin.getStaffManager().getStaffStats(uuid);
        int abuseScore = plugin.getAbuseScoreManager().getScore(uuid);
        
        return switch (param) {
            case "bans_count" -> String.valueOf(stats.bansCount);
            case "mutes_count" -> String.valueOf(stats.mutesCount);
            case "kicks_count" -> String.valueOf(stats.kicksCount);
            case "gives_count" -> String.valueOf(stats.givesCount);
            case "abuse_score" -> String.valueOf(abuseScore);
            case "warnings_count" -> String.valueOf(plugin.getWarnManager().getActiveWarningsCount(uuid).join());
            default -> "";
        };
    }
    
    private String processWarningsPlaceholder(UUID uuid, String param) {
        return switch (param) {
            case "count" -> String.valueOf(plugin.getWarnManager().getActiveWarningsCount(uuid).join());
            default -> "";
        };
    }
}
