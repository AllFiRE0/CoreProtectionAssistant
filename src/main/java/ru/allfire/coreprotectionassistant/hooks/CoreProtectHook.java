package ru.allfire.coreprotectionassistant.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CoreProtectHook {
    
    private final CoreProtectionAssistant plugin;
    private Plugin coreProtect;
    private Object coreProtectAPI;
    private Method performLookupMethod;
    
    private final Map<UUID, PlayerCache> playerCache = new ConcurrentHashMap<>();
    
    public CoreProtectHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        coreProtect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        
        if (coreProtect == null) {
            plugin.getLogger().warning("CoreProtect not found! Block/chest statistics will be unavailable.");
            return false;
        }
        
        try {
            // Получаем API через рефлексию
            Method getAPI = coreProtect.getClass().getMethod("getAPI");
            coreProtectAPI = getAPI.invoke(coreProtect);
            
            if (coreProtectAPI == null) {
                plugin.getLogger().warning("CoreProtect API not available!");
                return false;
            }
            
            // Ищем метод performLookup - сигнатура может отличаться в разных версиях
            Class<?> apiClass = coreProtectAPI.getClass();
            
            // Пробуем разные сигнатуры метода
            for (Method method : apiClass.getMethods()) {
                if (method.getName().equals("performLookup")) {
                    performLookupMethod = method;
                    break;
                }
            }
            
            if (performLookupMethod == null) {
                plugin.getLogger().warning("CoreProtect performLookup method not found!");
                return false;
            }
            
            plugin.getLogger().info("CoreProtect hook initialized successfully (reflection)");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize CoreProtect hook: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean isEnabled() {
        return coreProtect != null && coreProtectAPI != null && performLookupMethod != null;
    }
    
    // Безопасный метод для вызова performLookup
    private List<String[]> safePerformLookup(Integer rows, Integer time, List<String> users, List<Integer> actions) {
        if (!isEnabled()) {
            return List.of();
        }
        
        try {
            // Пробуем вызвать с разным количеством аргументов
            Object result;
            Class<?>[] paramTypes = performLookupMethod.getParameterTypes();
            
            if (paramTypes.length >= 9) {
                result = performLookupMethod.invoke(coreProtectAPI,
                    rows, time != null ? List.of(time) : null, users, null, null, actions, null, null, null);
            } else if (paramTypes.length >= 6) {
                result = performLookupMethod.invoke(coreProtectAPI,
                    rows, time != null ? List.of(time) : null, users, actions, null, null);
            } else {
                result = performLookupMethod.invoke(coreProtectAPI,
                    rows, time != null ? List.of(time) : null, users, actions);
            }
            
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<String[]> list = (List<String[]>) result;
                return list != null ? list : List.of();
            }
            
        } catch (Exception e) {
            // Тишина, просто возвращаем пустой список
        }
        
        return List.of();
    }
    
    // ========== БЛОКИ ==========
    
    public CompletableFuture<Integer> getBlocksBroken(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(0); // 0 = REMOVED (broken)
            
            List<String[]> results = safePerformLookup(100000, (int)(since / 1000), users, actions);
            return results.size();
        });
    }
    
    public CompletableFuture<Integer> getBlocksPlaced(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(1); // 1 = PLACED
            
            List<String[]> results = safePerformLookup(100000, (int)(since / 1000), users, actions);
            return results.size();
        });
    }
    
    // ========== КОНТЕЙНЕРЫ ==========
    
    public CompletableFuture<Integer> getChestsOpened(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(2); // 2 = INTERACTION
            
            List<String[]> results = safePerformLookup(100000, (int)(since / 1000), users, actions);
            
            return (int) results.stream()
                .filter(r -> {
                    if (r.length < 3) return false;
                    String material = r[2];
                    return material != null && (
                        material.contains("CHEST") ||
                        material.contains("SHULKER") ||
                        material.contains("BARREL")
                    );
                })
                .count();
        });
    }
    
    // ========== КОМАНДЫ ==========
    
    public CompletableFuture<Integer> getCommandsUsed(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(3); // 3 = COMMAND
            
            List<String[]> results = safePerformLookup(100000, (int)(since / 1000), users, actions);
            return results.size();
        });
    }
    
    // ========== СМЕРТИ/УБИЙСТВА ==========
    
    public CompletableFuture<Integer> getDeaths(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of("#" + playerName); // # = death
            List<Integer> actions = List.of(5); // 5 = KILL
            
            List<String[]> results = safePerformLookup(100000, (int)(since / 1000), users, actions);
            return results.size();
        });
    }
    
    public CompletableFuture<Integer> getKills(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(5); // 5 = KILL
            
            List<String[]> results = safePerformLookup(100000, (int)(since / 1000), users, actions);
            return results.size();
        });
    }
    
    // ========== СЕССИИ ==========
    
    public CompletableFuture<Long> getFirstSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0L;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0L;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(6); // 6 = SESSION (login)
            
            List<String[]> results = safePerformLookup(1, 0, users, actions);
            
            if (!results.isEmpty() && results.get(0).length > 0) {
                try {
                    return Long.parseLong(results.get(0)[0]) * 1000;
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
            return 0L;
        });
    }
    
    public CompletableFuture<Long> getLastSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0L;
            
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return System.currentTimeMillis();
            }
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0L;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(7); // 7 = LOGOUT
            
            List<String[]> results = safePerformLookup(1, 0, users, actions);
            
            if (!results.isEmpty() && results.get(0).length > 0) {
                try {
                    return Long.parseLong(results.get(0)[0]) * 1000;
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
            return 0L;
        });
    }
    
    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    
    private String getPlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player.getName();
        return Bukkit.getOfflinePlayer(uuid).getName();
    }
    
    public void updatePlayerCache(UUID uuid) {
        // Необязательно, можно оставить пустым
    }
    
    public PlayerCache getCachedPlayer(UUID uuid) {
        return playerCache.computeIfAbsent(uuid, k -> new PlayerCache());
    }
    
    public static class PlayerCache {
        public int blocksBroken = 0;
        public int blocksPlaced = 0;
        public int chestsOpened = 0;
        public int commandsUsed = 0;
        public int deaths = 0;
        public int kills = 0;
        public long firstSeen = 0L;
        public long lastSeen = 0L;
    }
}
