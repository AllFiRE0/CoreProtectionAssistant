package ru.allfire.coreprotectionassistant.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CoreProtectHook {
    
    private final CoreProtectionAssistant plugin;
    private Plugin coreProtect;
    private Object coreProtectAPI;
    private Method performLookupMethod;
    private Method parseResultMethod;
    
    public CoreProtectHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        coreProtect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        
        if (coreProtect == null) {
            plugin.getLogger().warning("CoreProtect not found!");
            return false;
        }
        
        try {
            // Получаем API через ((CoreProtect) plugin).getAPI()
            Class<?> coreProtectClass = coreProtect.getClass();
            Method getAPI = coreProtectClass.getMethod("getAPI");
            coreProtectAPI = getAPI.invoke(coreProtect);
            
            if (coreProtectAPI == null) {
                plugin.getLogger().warning("CoreProtect API not available!");
                return false;
            }
            
            // Проверяем версию API
            Method apiVersionMethod = coreProtectAPI.getClass().getMethod("APIVersion");
            int apiVersion = (int) apiVersionMethod.invoke(coreProtectAPI);
            plugin.getLogger().info("CoreProtect API version: " + apiVersion);
            
            // Ищем метод performLookup с правильной сигнатурой (8 параметров)
            for (Method method : coreProtectAPI.getClass().getMethods()) {
                if (method.getName().equals("performLookup")) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 8) {
                        performLookupMethod = method;
                        plugin.getLogger().info("Found performLookup with 8 parameters (API v10)");
                        break;
                    }
                }
            }
            
            // Ищем parseResult
            for (Method method : coreProtectAPI.getClass().getMethods()) {
                if (method.getName().equals("parseResult")) {
                    parseResultMethod = method;
                    break;
                }
            }
            
            if (performLookupMethod == null) {
                plugin.getLogger().warning("performLookup method not found!");
                return false;
            }
            
            plugin.getLogger().info("CoreProtect hook initialized successfully (API v" + apiVersion + ")");
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
    
    private List<String[]> performLookup(int timeSeconds, List<String> users, List<Integer> actions) {
        if (!isEnabled()) return List.of();
        
        try {
            // Правильный вызов для API v10:
            // performLookup(time, restrict_users, exclude_users, restrict_blocks, exclude_blocks, action_list, radius, radius_location)
            Object result = performLookupMethod.invoke(coreProtectAPI,
                timeSeconds,        // int time
                users,              // List<String> restrict_users
                null,               // List<String> exclude_users
                null,               // List<Object> restrict_blocks
                null,               // List<Object> exclude_blocks
                actions,            // List<Integer> action_list
                0,                  // int radius (0 = без радиуса)
                null                // Location radius_location
            );
            
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<String[]> list = (List<String[]>) result;
                return list != null ? list : List.of();
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("performLookup failed: " + e.getMessage());
        }
        
        return List.of();
    }
    
    // ========== СТАТИСТИКА ==========
    
    public CompletableFuture<Integer> getBlocksBroken(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(0); // 0 = REMOVED (broken)
            
            int timeSeconds = since > 0 ? (int) ((System.currentTimeMillis() - since) / 1000) : 0;
            List<String[]> results = performLookup(timeSeconds, users, actions);
            
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
            
            int timeSeconds = since > 0 ? (int) ((System.currentTimeMillis() - since) / 1000) : 0;
            List<String[]> results = performLookup(timeSeconds, users, actions);
            
            return results.size();
        });
    }
    
    public CompletableFuture<Integer> getChestsOpened(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(2); // 2 = INTERACTION
            
            int timeSeconds = since > 0 ? (int) ((System.currentTimeMillis() - since) / 1000) : 0;
            List<String[]> results = performLookup(timeSeconds, users, actions);
            
            // Фильтруем только контейнеры (если есть parseResult)
            if (parseResultMethod != null) {
                return (int) results.stream()
                    .filter(r -> {
                        try {
                            Object parseResult = parseResultMethod.invoke(coreProtectAPI, (Object) r);
                            Method getType = parseResult.getClass().getMethod("getType");
                            Object type = getType.invoke(parseResult);
                            if (type instanceof Material) {
                                Material mat = (Material) type;
                                return mat.name().contains("CHEST") || 
                                       mat.name().contains("SHULKER") ||
                                       mat.name().contains("BARREL") ||
                                       mat.name().contains("FURNACE") ||
                                       mat.name().contains("HOPPER");
                            }
                        } catch (Exception e) {}
                        return false;
                    })
                    .count();
            }
            
            return results.size();
        });
    }
    
    public CompletableFuture<Integer> getCommandsUsed(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(3); // 3 = COMMAND
            
            int timeSeconds = since > 0 ? (int) ((System.currentTimeMillis() - since) / 1000) : 0;
            List<String[]> results = performLookup(timeSeconds, users, actions);
            
            return results.size();
        });
    }
    
    public CompletableFuture<Integer> getDeaths(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            
            List<String> users = List.of("#" + playerName); // # = death target
            List<Integer> actions = List.of(5); // 5 = KILL
            
            int timeSeconds = since > 0 ? (int) ((System.currentTimeMillis() - since) / 1000) : 0;
            List<String[]> results = performLookup(timeSeconds, users, actions);
            
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
            
            int timeSeconds = since > 0 ? (int) ((System.currentTimeMillis() - since) / 1000) : 0;
            List<String[]> results = performLookup(timeSeconds, users, actions);
            
            return results.size();
        });
    }
    
    public CompletableFuture<Long> getFirstSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0L;
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0L;
            
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(6); // 6 = LOGIN
            
            List<String[]> results = performLookup(0, users, actions);
            
            if (!results.isEmpty()) {
                String[] first = results.get(0);
                if (first.length > 0) {
                    try {
                        return Long.parseLong(first[0]) * 1000;
                    } catch (NumberFormatException e) {}
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
            
            List<String[]> results = performLookup(0, users, actions);
            
            if (!results.isEmpty()) {
                String[] last = results.get(results.size() - 1);
                if (last.length > 0) {
                    try {
                        return Long.parseLong(last[0]) * 1000;
                    } catch (NumberFormatException e) {}
                }
            }
            return 0L;
        });
    }
    
    private String getPlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player.getName();
        return Bukkit.getOfflinePlayer(uuid).getName();
    }
    
    public void updatePlayerCache(UUID uuid) {}
    
    public PlayerCache getCachedPlayer(UUID uuid) {
        return new PlayerCache();
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
