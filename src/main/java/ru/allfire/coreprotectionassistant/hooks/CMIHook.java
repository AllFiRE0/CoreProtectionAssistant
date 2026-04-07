package ru.allfire.coreprotectionassistant.hooks;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CMIHook {
    
    private final CoreProtectionAssistant plugin;
    private Plugin cmiPlugin;
    private Object cmiAPI;
    private Method getUserMethod;
    private Method getPlayerMethod;
    private Method getBalanceMethod;
    private Method getPlayTimeMethod;
    private Method getIPMethod;
    
    public CMIHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        cmiPlugin = Bukkit.getPluginManager().getPlugin("CMI");
        
        if (cmiPlugin == null) {
            plugin.getLogger().warning("CMI not found! Some features will be disabled.");
            return false;
        }
        
        try {
            // Пытаемся получить API через рефлексию
            Class<?> cmiClass = cmiPlugin.getClass();
            Method getAPI = cmiClass.getMethod("getInstance");
            Object instance = getAPI.invoke(null);
            
            Method getPlayerManager = instance.getClass().getMethod("getPlayerManager");
            Object playerManager = getPlayerManager.invoke(instance);
            
            getUserMethod = playerManager.getClass().getMethod("getUser", UUID.class);
            getPlayerMethod = playerManager.getClass().getMethod("getPlayer", UUID.class);
            
            Method getEconomyManager = instance.getClass().getMethod("getEconomyManager");
            Object economyManager = getEconomyManager.invoke(instance);
            getBalanceMethod = economyManager.getClass().getMethod("getBalance", UUID.class);
            
            plugin.getLogger().info("CMI hook initialized successfully");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize CMI hook: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isEnabled() {
        return cmiPlugin != null;
    }
    
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0.0;
            
            try {
                Object result = getBalanceMethod.invoke(null, uuid);
                if (result instanceof Double) {
                    return (Double) result;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get balance from CMI: " + e.getMessage());
            }
            return 0.0;
        });
    }
    
    public CompletableFuture<Long> getPlayTime(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0L;
            
            try {
                Object user = getUserMethod.invoke(null, uuid);
                if (user != null) {
                    Method getTotalPlayTime = user.getClass().getMethod("getTotalPlayTime");
                    Object result = getTotalPlayTime.invoke(user);
                    if (result instanceof Long) {
                        return (Long) result;
                    }
                }
            } catch (Exception e) {
                // Тишина
            }
            return 0L;
        });
    }
    
    public CompletableFuture<String> getIP(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return "";
            
            try {
                Object user = getUserMethod.invoke(null, uuid);
                if (user != null) {
                    Method getIP = user.getClass().getMethod("getIp");
                    Object result = getIP.invoke(user);
                    if (result instanceof String) {
                        return (String) result;
                    }
                }
            } catch (Exception e) {
                // Тишина
            }
            return "";
        });
    }
    
    public CompletableFuture<String> getNickname(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return "";
            
            try {
                Object user = getUserMethod.invoke(null, uuid);
                if (user != null) {
                    Method getNickname = user.getClass().getMethod("getNickName");
                    Object result = getNickname.invoke(user);
                    if (result instanceof String) {
                        return (String) result;
                    }
                }
            } catch (Exception e) {
                // Тишина
            }
            return "";
        });
    }
}
