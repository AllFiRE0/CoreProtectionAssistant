package ru.allfire.coreprotectionassistant.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

public class StaffActionListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    
    public StaffActionListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("cpa.staff")) return;
        
        // Логируем открытие чужих инвентарей (invsee)
        if (event.getInventory().getType() == InventoryType.PLAYER) {
            Player target = (Player) event.getInventory().getHolder();
            if (target != null && !target.equals(player)) {
                plugin.getStaffManager().logStaffAction(
                    player, 
                    "INVSEE", 
                    target.getName(), 
                    null
                );
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("cpa.staff")) return;
        
        // Логируем частые телепорты
        plugin.getStaffManager().logTeleport(player, event.getTo());
    }
}
