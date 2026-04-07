package ru.allfire.coreprotectionassistant.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

public class PlayerListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    
    public PlayerListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Сохраняем вход в основном потоке
        plugin.getDatabaseManager().logPlayerJoin(player);
        
        // Проверяем запрещенные права АСИНХРОННО
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            checkProhibitedPermissions(player);
        });
        
        // Обновляем кэш CoreProtect
        if (plugin.getCoreProtectHook().isEnabled()) {
            plugin.getCoreProtectHook().updatePlayerCache(player.getUniqueId());
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Сохраняем выход
        plugin.getDatabaseManager().logPlayerQuit(player);
    }
    
    private void checkProhibitedPermissions(Player player) {
        var prohibitedPerms = plugin.getConfigManager().getMainConfig()
            .getStringList("prohibited_permissions");
        
        boolean hasProhibited = false;
        
        for (String perm : prohibitedPerms) {
            if (player.hasPermission(perm)) {
                hasProhibited = true;
                
                // Логируем
                plugin.getDatabaseManager().logProhibitedPermission(
                    player.getUniqueId(), perm
                );
                
                plugin.getLogger().warning("Player " + player.getName() + 
                    " has prohibited permission: " + perm);
            }
        }
        
        // Если есть запрещённые права и игрок - персонал
        if (hasProhibited && player.hasPermission("cpa.staff")) {
            plugin.getAbuseScoreManager().addScore(
                player.getUniqueId(), 
                "prohibited_permission", 
                15
            );
        }
    }
}
