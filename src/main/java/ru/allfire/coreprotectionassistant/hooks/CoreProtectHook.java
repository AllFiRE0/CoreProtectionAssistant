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
     * Получить историю блока (используем blockLookup если доступен)
     */
    public CompletableFuture<List<BlockAction>> getBlockHistory(Location loc) {
        return CompletableFuture.supplyAsync(() -> {
            List<BlockAction> history = new ArrayList<>();
            
            if (!isEnabled()) return history;
            
            try {
                // Пробуем blockLookup (самый надёжный способ)
                if (blockLookupMethod != null) {
                    org.bukkit.block.Block block = loc.getBlock();
                    Object result = blockLookupMethod.invoke(coreProtectAPI, block, 0);
                    
                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String[]> lookup = (List<String[]>) result;
                        
                        for (String[] row : lookup) {
                            if (parseResultMethod != null) {
                                try {
                                    Object parsed = parseResultMethod.invoke(coreProtectAPI, (Object) row);
                                    Method getPlayer = parsed.getClass().getMethod("getPlayer");
                                    Method getTimestamp = parsed.getClass().getMethod("getTimestamp");
                                    Method getActionId = parsed.getClass().getMethod("getActionId");
                                    Method getType = parsed.getClass().getMethod("getType");
                                    
                                    String player = (String) getPlayer.invoke(parsed);
                                    long timestamp = (long) getTimestamp.invoke(parsed) * 1000;
                                    int actionId = (int) getActionId.invoke(parsed);
                                    Material type = (Material) getType.invoke(parsed);
                                    
                                    String action = actionId == 0 ? "BREAK" : (actionId == 1 ? "PLACE" : "INTERACT");
                                    history.add(new BlockAction(timestamp, player, action, type.name()));
                                } catch (Exception ex) {
                                    // fallback: используем сырые данные
                                    if (row.length >= 2) {
                                        history.add(new BlockAction(0, row[1], "UNKNOWN", "UNKNOWN"));
                                    }
                                }
                            }
                        }
                    }
                    return history;
                }
                
                // Fallback: performLookup по координатам
                int worldId = getWorldId(loc.getWorld().getName());
                if (worldId == -1) return history;
                
                // Используем performLookup с радиусом 0 и локацией
                // Но так как сигнатура сложная, просто возвращаем пустой список
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get block history: " + e.getMessage());
            }
            
            return history;
        });
    }
    
    private int getWorldId(String worldName) {
        // Заглушка — в реальности нужно кэшировать ID миров из БД CoreProtect
        return 1;
    }
    
    /**
     * Проверить, взаимодействовал ли другой игрок с этим блоком
     */
    public CompletableFuture<Boolean> wasModifiedByOther(Location loc, String currentPlayer) {
        return getBlockHistory(loc).thenApply(history -> {
            for (BlockAction action : history) {
                if (!action.username().equalsIgnoreCase(currentPlayer)) {
                    plugin.getLogger().info("Block was modified by " + action.username() + " before " + currentPlayer);
                    return true;
                }
            }
            return false;
        });
    }
    
    // ========== СТАТИСТИКА ==========
    
    public CompletableFuture<Integer> getBlocksBroken(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(0);
            return performLookup(0, users, actions).size();
        });
    }
    
    public CompletableFuture<Integer> getBlocksPlaced(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(1);
            return performLookup(0, users, actions).size();
        });
    }
    
    public CompletableFuture<Integer> getChestsOpened(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(2);
            return performLookup(0, users, actions).size();
        });
    }
    
    public CompletableFuture<Integer> getCommandsUsed(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(3);
            return performLookup(0, users, actions).size();
        });
    }
    
    public CompletableFuture<Integer> getDeaths(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            List<String> users = List.of("#" + playerName);
            List<Integer> actions = List.of(5);
            return performLookup(0, users, actions).size();
        });
    }
    
    public CompletableFuture<Integer> getKills(UUID uuid, long since) {
        return CompletableFuture.supplyAsync(() -> {
            String playerName = getPlayerName(uuid);
            if (playerName == null) return 0;
            List<String> users = List.of(playerName);
            List<Integer> actions = List.of(5);
            return performLookup(0, users, actions).size();
        });
    }
    
    public CompletableFuture<Long> getFirstSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> 0L);
    }
    
    public CompletableFuture<Long> getLastSeen(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) return System.currentTimeMillis();
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
    
    public record BlockAction(long timestamp, String username, String action, String material) {}
    
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
