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
    private Method parseResultMethod;
    private Method getBlockLookupMethod;
    private Method getSessionLookupMethod;
    
    private final Map<UUID, PlayerCache> playerCache = new ConcurrentHashMap<>();
    
    public CoreProtectHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        coreProtect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        
        if (coreProtect == null) {
            plugin.getLogger().warning("CoreProtect not found! Some features will be disabled.");
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
            
            // Получаем методы API
            Class<?> apiClass = coreProtectAPI.getClass();
            
            // performLookup(rows, time, restrictUsers, restrictWorlds, excludeUsers, 
            //               actionTypes, materials, blockData, locations)
            performLookupMethod = apiClass.getMethod("performLookup", 
                Integer.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class);
            
            // parseResult(result, type)
            parseResultMethod = apiClass.getMethod("parseResult", List.class, Integer.class);
            
            plugin.getLogger().info("CoreProtect hook initialized successfully");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize CoreProtect hook: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isEnabled() {
        return coreProtect != null && coreProtectAPI != null;
    }
    
    // ========== БЛОКИ ==========
    
    public CompletableFuture<Integer> getBlocksBroken(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return 0;
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(0); // 0 = REMOVED (broken)
                
                List<String[]> results = performLookup(100000, since, users, actions);
                return results.size();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get blocks broken: " + e.getMessage());
                return 0;
            }
        });
    }
    
    public CompletableFuture<Integer> getBlocksPlaced(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return 0;
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(1); // 1 = PLACED
                
                List<String[]> results = performLookup(100000, since, users, actions);
                return results.size();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get blocks placed: " + e.getMessage());
                return 0;
            }
        });
    }
    
    // ========== КОНТЕЙНЕРЫ ==========
    
    public CompletableFuture<Integer> getChestsOpened(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return 0;
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(2); // 2 = INTERACTION (chests)
                
                List<String[]> results = performLookup(100000, since, users, actions);
                
                // Фильтруем только сундуки
                return (int) results.stream()
                    .filter(r -> {
                        String material = r[2];
                        return material != null && (
                            material.contains("CHEST") ||
                            material.contains("SHULKER") ||
                            material.contains("BARREL") ||
                            material.contains("HOPPER") ||
                            material.contains("DISPENSER") ||
                            material.contains("DROPPER") ||
                            material.contains("FURNACE")
                        );
                    })
                    .count();
                    
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get chests opened: " + e.getMessage());
                return 0;
            }
        });
    }
    
    // ========== КОМАНДЫ ==========
    
    public CompletableFuture<Integer> getCommandsUsed(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return 0;
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(3); // 3 = COMMAND
                
                List<String[]> results = performLookup(100000, since, users, actions);
                return results.size();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get commands used: " + e.getMessage());
                return 0;
            }
        });
    }
    
    // ========== ЧАТ ==========
    
    public CompletableFuture<List<String>> getChatMessages(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return List.of();
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return List.of();
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(4); // 4 = CHAT
                
                List<String[]> results = performLookup(10000, since, users, actions);
                
                return results.stream()
                    .map(r -> r[3]) // message content
                    .filter(Objects::nonNull)
                    .toList();
                    
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get chat messages: " + e.getMessage());
                return List.of();
            }
        });
    }
    
    // ========== СМЕРТИ/УБИЙСТВА ==========
    
    public CompletableFuture<Integer> getDeaths(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return 0;
                
                List<String> users = List.of("#" + playerName); // # = death
                List<Integer> actions = List.of(5); // 5 = KILL
                
                List<String[]> results = performLookup(100000, since, users, actions);
                return results.size();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get deaths: " + e.getMessage());
                return 0;
            }
        });
    }
    
    public CompletableFuture<Integer> getKills(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return 0;
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(5); // 5 = KILL
                
                List<String[]> results = performLookup(100000, since, users, actions);
                return results.size();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get kills: " + e.getMessage());
                return 0;
            }
        });
    }
    
    // ========== СЕССИИ ==========
    
    public CompletableFuture<Long> getFirstSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0L;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                String playerName = player != null ? player.getName() : 
                    Bukkit.getOfflinePlayer(uuid).getName();
                
                if (playerName == null) return 0L;
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(6); // 6 = SESSION (login)
                
                List<String[]> results = performLookup(1, 0, users, actions);
                
                if (!results.isEmpty()) {
                    String timeStr = results.get(0)[0];
                    return Long.parseLong(timeStr) * 1000;
                }
                
                return 0L;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get first seen: " + e.getMessage());
                return 0L;
            }
        });
    }
    
    public CompletableFuture<Long> getLastSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0L;
            
            try {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    return System.currentTimeMillis();
                }
                
                String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                if (playerName == null) return 0L;
                
                List<String> users = List.of(playerName);
                List<Integer> actions = List.of(7); // 7 = LOGOUT
                
                List<String[]> results = performLookup(1, 0, users, actions);
                
                if (!results.isEmpty()) {
                    String timeStr = results.get(0)[0];
                    return Long.parseLong(timeStr) * 1000;
                }
                
                return 0L;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get last seen: " + e.getMessage());
                return 0L;
            }
        });
    }
    
    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    
    @SuppressWarnings("unchecked")
    private List<String[]> performLookup(int rows, long since, List<String> users, 
                                          List<Integer> actions) {
        try {
            List<String> restrictUsers = users;
            List<String> restrictWorlds = null;
            List<String> excludeUsers = null;
            List<Integer> actionTypes = actions;
            List<String> materials = null;
            List<String> blockData = null;
            List<Location> locations = null;
            
            int time = since > 0 ? (int) ((System.currentTimeMillis() - since) / 1000) : 0;
            
            List<String[]> result = (List<String[]>) performLookupMethod.invoke(
                coreProtectAPI,
                rows,
                time > 0 ? List.of(time) : null,
                restrictUsers,
                restrictWorlds,
                excludeUsers,
                actionTypes,
                materials,
                blockData,
                locations
            );
            
            return result != null ? result : List.of();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to perform CoreProtect lookup: " + e.getMessage());
            return List.of();
        }
    }
    
    public void updatePlayerCache(UUID uuid) {
        playerCache.computeIfAbsent(uuid, k -> new PlayerCache());
        
        CompletableFuture.allOf(
            getBlocksBroken(uuid, 0).thenAccept(count -> 
                playerCache.get(uuid).blocksBroken = count),
            getBlocksPlaced(uuid, 0).thenAccept(count -> 
                playerCache.get(uuid).blocksPlaced = count),
            getChestsOpened(uuid, 0).thenAccept(count -> 
                playerCache.get(uuid).chestsOpened = count),
            getCommandsUsed(uuid, 0).thenAccept(count -> 
                playerCache.get(uuid).commandsUsed = count),
            getDeaths(uuid, 0).thenAccept(count -> 
                playerCache.get(uuid).deaths = count),
            getKills(uuid, 0).thenAccept(count -> 
                playerCache.get(uuid).kills = count),
            getFirstSeen(uuid).thenAccept(time -> 
                playerCache.get(uuid).firstSeen = time),
            getLastSeen(uuid).thenAccept(time -> 
                playerCache.get(uuid).lastSeen = time)
        );
    }
    
    public PlayerCache getCachedPlayer(UUID uuid) {
        return playerCache.getOrDefault(uuid, new PlayerCache());
    }
    
    // ========== КЭШ ИГРОКА ==========
    
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
