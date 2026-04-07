package ru.allfire.coreprotectionassistant.hooks;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class STCPHook {
    
    private final CoreProtectionAssistant plugin;
    private Plugin stcpPlugin;
    
    public STCPHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        stcpPlugin = Bukkit.getPluginManager().getPlugin("STCP");
        
        if (stcpPlugin == null) {
            plugin.getLogger().info("STCP not found. Lag machine detection will be disabled.");
            return false;
        }
        
        plugin.getLogger().info("STCP hook initialized successfully");
        return true;
    }
    
    public boolean isEnabled() {
        return stcpPlugin != null;
    }
    
    public CompletableFuture<Integer> getLagMachineAttempts(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) return 0;
            
            try {
                // Пытаемся получить статистику через рефлексию
                Method getAPI = stcpPlugin.getClass().getMethod("getAPI");
                Object api = getAPI.invoke(stcpPlugin);
                
                Method getViolations = api.getClass().getMethod("getPlayerViolations", UUID.class);
                Object result = getViolations.invoke(api, uuid);
                
                if (result instanceof Integer) {
                    return (Integer) result;
                }
            } catch (Exception e) {
                // Тишина
            }
            return 0;
        });
    }
}
