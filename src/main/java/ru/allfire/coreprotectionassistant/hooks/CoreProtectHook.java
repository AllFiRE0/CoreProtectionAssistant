package ru.allfire.coreprotectionassistant.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
            
            // Ищем performLookup и запоминаем его сигнатуру
            for (Method method : coreProtectAPI.getClass().getMethods()) {
                if (method.getName().equals("performLookup")) {
                    performLookupMethod = method;
                    plugin.getLogger().info("Found performLookup with " + method.getParameterCount() + " parameters");
                    
                    // Выводим типы параметров для отладки
                    Class<?>[] paramTypes = method.getParameterTypes();
                    for (int i = 0; i < paramTypes.length; i++) {
                        plugin.getLogger().info("  param[" + i + "]: " + paramTypes[i].getName());
                    }
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
            Class<?>[] paramTypes = performLookupMethod.getParameterTypes();
            
            // Определяем, какой первый параметр
            if (paramCount == 6) {
                // Старая версия: (int maxLines, List<String> time, List<String> users, List<Integer> actions, ...)
                result = performLookupMethod.invoke(coreProtectAPI,
                    100000, 
                    timeSeconds > 0 ? List.of(String.valueOf(timeSeconds)) : null, 
                    users, 
                    actions, 
                    null, 
                    null);
            } else if (paramCount == 7) {
                // Промежуточная версия
                result = performLookupMethod.invoke(coreProtectAPI,
                    timeSeconds, users, null, null, actions, 0, null);
            } else if (paramCount == 8) {
                // API v10/v11: (int time, List<String> restrict_users, List<String> exclude_users, 
                //               List<Object> restrict_blocks, List<Object> exclude_blocks, 
                //               List<Integer> action_list, int radius, Location radius_location)
                result = performLookupMethod.invoke(coreProtectAPI,
                    timeSeconds, users, null, null, null, actions, 0, null);
            } else if (paramCount == 9) {
                // Ещё одна версия
                result = performLookupMethod.invoke(coreProtectAPI,
                    100000, timeSeconds, users, null, null, actions, 0, null, null);
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
            // Тишина, чтобы не спамить
        }
        
        return List.of();
    }
    
    /**
     * Получить владельца блока (кто его поставил ПОСЛЕДНИМ)
     */
    public CompletableFuture<String> getBlockOwner(Location loc) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return null;
            
            boolean debug = plugin.getConfigManager().getMainConfig().getBoolean("debug", false);
            
            try {
                if (blockLookupMethod != null) {
                    Object result = blockLookupMethod.invoke(coreProtectAPI, loc.getBlock(), 0);
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String[]> lookup = (List<String[]>) result;
                        
                        if (debug) {
                            plugin.getLogger().info("blockLookup returned " + lookup.size() + " entries");
                        }
                        
                        String lastOwner = null;
                        long lastTime = 0;
                        
                        for (String[] row : lookup) {
                            if (debug) {
                                plugin.getLogger().info("  row: " + Arrays.toString(row));
                            }
                            
                            if (row.length > 7) {
                                try {
                                    long timestamp = Long.parseLong(row[0]);
                                    String username = row[1];
                                    String actionType = row[7];
                                    
                                    if (actionType != null && actionType.equals("1")) {
                                        if (timestamp > lastTime) {
                                            lastTime = timestamp;
                                            lastOwner = username;
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    // Пропускаем
                                }
                            }
                        }
                        
                        if (lastOwner != null) {
                            if (debug) {
                                plugin.getLogger().info("Found owner: " + lastOwner + " (time: " + lastTime + ")");
                            }
                            return lastOwner;
                        }
                    }
                }
                
            } catch (Exception e) {
                if (debug) {
                    plugin.getLogger().warning("Failed to get block owner: " + e.getMessage());
                }
            }
            
            return null;
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
    
    public CompletableFuture<Integer> getCommandsUsed(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> 0);
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
