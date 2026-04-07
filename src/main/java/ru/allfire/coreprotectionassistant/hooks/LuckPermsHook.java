package ru.allfire.coreprotectionassistant.hooks;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LuckPermsHook {
    
    private final CoreProtectionAssistant plugin;
    private LuckPerms luckPerms;
    
    public LuckPermsHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public boolean init() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            plugin.getLogger().warning("LuckPerms not found! Some features will be disabled.");
            return false;
        }
        
        try {
            luckPerms = LuckPermsProvider.get();
            plugin.getLogger().info("LuckPerms hook initialized successfully");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize LuckPerms hook: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isEnabled() {
        return luckPerms != null;
    }
    
    public Set<String> getPermissions(UUID uuid) {
        if (!isEnabled()) return Set.of();
        
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) return Set.of();
            
            return user.getNodes().stream()
                .map(Node::getKey)
                .collect(Collectors.toSet());
                
        } catch (Exception e) {
            return Set.of();
        }
    }
    
    public String getPrimaryGroup(UUID uuid) {
        if (!isEnabled()) return "";
        
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) return "";
            return user.getPrimaryGroup();
        } catch (Exception e) {
            return "";
        }
    }
    
    public boolean hasPermission(UUID uuid, String permission) {
        if (!isEnabled()) return false;
        
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) return false;
            
            return user.getNodes().stream()
                .anyMatch(n -> n.getKey().equals(permission));
                
        } catch (Exception e) {
            return false;
        }
    }
}
