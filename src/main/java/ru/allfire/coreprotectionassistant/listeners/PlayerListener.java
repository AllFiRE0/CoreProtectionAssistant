package ru.allfire.coreprotectionassistant.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.util.UUID;

public class PlayerListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    
    public PlayerListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Сохраняем вход
        plugin.getDatabaseManager().logPlayerJoin(player);
        
        // Проверяем запрещенные права
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            checkProhibitedPermissions(player);
        });
        
        // Обновляем кэш
        plugin.getCoreProtectHook().updatePlayerCache(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Сохраняем выход
        plugin.getDatabaseManager().logPlayerQuit(player);
        
        // Очищаем временные данные
        plugin.getChatRuleManager().resetDailyApologies();
    }
    
    private void checkProhibitedPermissions(Player player) {
        var prohibitedPerms = plugin.getConfigManager().getMainConfig()
            .getStringList("prohibited_permissions");
        
        for (String perm : prohibitedPerms) {
            if (player.hasPermission(perm)) {
                // Логируем
                plugin.getDatabaseManager().logProhibitedPermission(
                    player.getUniqueId(), perm
                );
                
                // Уведомляем если игрок - персонал
                if (player.hasPermission("cpa.staff")) {
                    plugin.getAbuseScoreManager().addScore(
                        player.getUniqueId(), 
                        "prohibited_permission", 
                        15
                    );
                }
            }
        }
    }
}
