package ru.allfire.coreprotectionassistant.hooks;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LuckPermsHook {
    
    private final CoreProtectionAssistant plugin;
    private Plugin luckPermsPlugin;
    private Object luckPermsAPI;
    private Method getUserManagerMethod;
    private Method getUserMethod;
    private Method getPrimaryGroupMethod;
    private Method getNodesMethod;
    private Method getKeyMethod;
    
    public LuckPermsHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        
        if (luckPermsPlugin == null) {
            plugin.getLogger().info("LuckPerms not found. Permission checking disabled.");
            return false;
        }
        
        try {
            // Получаем API через LuckPermsProvider.get()
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            luckPermsAPI = getMethod.invoke(null);
            
            // Получаем методы
            getUserManagerMethod = luckPermsAPI.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(luckPermsAPI);
            
            getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            getPrimaryGroupMethod = userClass.getMethod("getPrimaryGroup");
            getNodesMethod = userClass.getMethod("getNodes");
            
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            getKeyMethod = nodeClass.getMethod("getKey");
            
            plugin.getLogger().info("LuckPerms hook initialized successfully (reflection)");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize LuckPerms hook: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isEnabled() {
        return luckPermsAPI != null;
    }
    
    public Set<String> getPermissions(UUID uuid) {
        Set<String> permissions = new HashSet<>();
        
        if (!isEnabled()) return permissions;
        
        try {
            Object userManager = getUserManagerMethod.invoke(luckPermsAPI);
            Object user = getUserMethod.invoke(userManager, uuid);
            
            if (user != null) {
                Object nodes = getNodesMethod.invoke(user);
                if (nodes instanceof Iterable) {
                    for (Object node : (Iterable<?>) nodes) {
                        Object key = getKeyMethod.invoke(node);
                        if (key instanceof String) {
                            permissions.add((String) key);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Тишина
        }
        
        return permissions;
    }
    
    public String getPrimaryGroup(UUID uuid) {
        if (!isEnabled()) return "default";
        
        try {
            Object userManager = getUserManagerMethod.invoke(luckPermsAPI);
            Object user = getUserMethod.invoke(userManager, uuid);
            
            if (user != null) {
                Object group = getPrimaryGroupMethod.invoke(user);
                if (group instanceof String) {
                    return (String) group;
                }
            }
        } catch (Exception e) {
            // Тишина
        }
        
        return "default";
    }
    
    public boolean hasPermission(UUID uuid, String permission) {
        return getPermissions(uuid).contains(permission);
    }
}
