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
    private Method blockLookupMethod;
    
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
            Class<?> coreProtectClass = coreProtect.getClass();
            Method getAPI = coreProtectClass.getMethod("getAPI");
            coreProtectAPI = getAPI.invoke(coreProtect);
            
            if (coreProtectAPI == null) {
                plugin.getLogger().warning("CoreProtect API not available!");
                return false;
            }
            
            // Проверяем версию API
            try {
                Method apiVersionMethod = coreProtectAPI.getClass().getMethod("APIVersion");
                int apiVersion = (int) apiVersionMethod.invoke(coreProtectAPI);
                plugin.getLogger().info("CoreProtect API version: " + apiVersion);
            } catch (NoSuchMethodException e) {
                plugin.getLogger().info("CoreProtect API version: OLD");
            }
            
            // Ищем performLookup
            for (Method method : coreProtectAPI.getClass().getMethods()) {
                if (method.getName().equals("performLookup")) {
                    performLookupMethod = method;
                    plugin.getLogger().info("Found performLookup with " + method.getParameterCount() + " parameters");
                    break;
                }
            }
            
            // Ищем parseResult
            for (Method method : coreProtectAPI.getClass().getMethods()) {
                if (method.getName().equals("parseResult")) {
                    parseResultMethod = method;
                    break;
                }
            }
            
            // Ищем blockLookup
            for (Method method : coreProtectAPI.getClass().getMethods()) {
                if (method.getName().equals("blockLookup")) {
                    blockLookupMethod = method;
                    plugin.getLogger().info("Found blockLookup method");
                    break;
                }
            }
            
            if (performLookupMethod == null && blockLookupMethod == null) {
                plugin.getLogger().warning("No lookup methods found!");
                return false;
            }
            
            plugin.getLogger().info("CoreProtect hook initialized successfully");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize CoreProtect hook: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean isEnabled() {
        return coreProtect != null && coreProtectAPI != null;
    }
    
    private List<String[]> performLookup(int timeSeconds, List<String> users, List<Integer> actions) {
        if (!isEnabled() || performLookupMethod == null) return List.of();
        
        try {
            Object result = null;
            int paramCount = performLookupMethod.getParameterCount();
            
            if (paramCount == 8) {
                // API v10/v11
                result = performLookupMethod.invoke(coreProtectAPI,
                    timeSeconds, users, null, null, null, actions, 0, null);
            } else if (paramCount == 9) {
                // Старая версия
                result = performLookupMethod.invoke(coreProtectAPI,
                    timeSeconds, users, null, null, actions, null, null, 0, null);
            } else if (paramCount == 6) {
                // Ещё более старая
                result = performLookupMethod.invoke(coreProtectAPI,
                    100000, timeSeconds > 0 ? List.of(timeSeconds) : null, users, actions, null, null);
            } else {
                plugin.getLogger().warning("Unknown performLookup signature with " + paramCount + " parameters");
                return List.of();
            }
            
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
    
    /**
     * Проверить, взаимодействовал ли другой игрок с этим блоком
     */
    public CompletableFuture<Boolean> wasModifiedByOther(Location loc, String currentPlayer) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return false;
            
            try {
                // Способ 1: blockLookup (самый точный)
                if (blockLookupMethod != null) {
                    Object result = blockLookupMethod.invoke(coreProtectAPI, loc.getBlock(), 0);
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String[]> lookup = (List<String[]>) result;
                        
                        for (String[] row : lookup) {
                            if (row.length >= 2) {
                                String user = row[1];
                                if (user != null && !user.equalsIgnoreCase(currentPlayer) && !user.equals("#null")) {
                                    plugin.getLogger().info("Block was modified by " + user + " before " + currentPlayer);
                                    return true;
                                }
                            }
                        }
                    }
                }
                
                // Способ 2: performLookup с радиусом 0
                if (performLookupMethod != null) {
                    int paramCount = performLookupMethod.getParameterCount();
                    Object result;
                    
                    if (paramCount == 8) {
                        result = performLookupMethod.invoke(coreProtectAPI,
                            0, null, null, null, null, null, 0, loc);
                    } else {
                        return false;
                    }
                    
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String[]> lookup = (List<String[]>) result;
                        
                        for (String[] row : lookup) {
                            if (row.length >= 2) {
                                String user = row[1];
                                if (user != null && !user.equalsIgnoreCase(currentPlayer) && !user.equals("#null")) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check block history: " + e.getMessage());
            }
            
            return false;
        });
    }
    
    // ========== СТАТИСТИКА ==========
    
    public CompletableFuture<Integer> getBlocksBroken(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            return performLookup(0, List.of(playerName), List.of(0)).size();
        });
    }
    
    public CompletableFuture<Integer> getBlocksPlaced(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            return performLookup(0, List.of(playerName), List.of(1)).size();
        });
    }
    
    public CompletableFuture<Integer> getChestsOpened(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            return performLookup(0, List.of(playerName), List.of(2)).size();
        });
    }
    
    public CompletableFuture<Integer> getCommandsUsed(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            return performLookup(0, List.of(playerName), List.of(3)).size();
        });
    }
    
    public CompletableFuture<Integer> getDeaths(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            return performLookup(0, List.of("#" + playerName), List.of(5)).size();
        });
    }
    
    public CompletableFuture<Integer> getKills(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            return performLookup(0, List.of(playerName), List.of(5)).size();
        });
    }
    
    public CompletableFuture<Long> getFirstSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0L;
            List<String[]> results = performLookup(0, List.of(playerName), List.of(6));
            if (!results.isEmpty() && results.get(0).length > 0) {
                try {
                    return Long.parseLong(results.get(0)[0]) * 1000;
                } catch (NumberFormatException e) {}
            }
            return 0L;
        });
    }
    
    public CompletableFuture<Long> getLastSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) return System.currentTimeMillis();
            
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0L;
            List<String[]> results = performLookup(0, List.of(playerName), List.of(7));
            if (!results.isEmpty() && results.get(results.size() - 1).length > 0) {
                try {
                    return Long.parseLong(results.get(results.size() - 1)[0]) * 1000;
                } catch (NumberFormatException e) {}
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
