package ru.allfire.coreprotectionassistant.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.CommandExecutor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GriefListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    private final Map<UUID, Long> lastGriefTime = new ConcurrentHashMap<>();
    
    public GriefListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        var config = plugin.getConfigManager().getMainConfig();
        if (!config.getBoolean("grief_detection.enabled", false)) return;
        
        String blockType = block.getType().name();
        List<String> trackedBlocks = config.getStringList("grief_detection.tracked_blocks");
        if (!trackedBlocks.contains(blockType)) return;
        
        long now = System.currentTimeMillis();
        Long lastTime = lastGriefTime.get(player.getUniqueId());
        long minTimeMs = config.getLong("grief_detection.min_time_between_actions", 5) * 1000;
        if (lastTime != null && (now - lastTime) < minTimeMs) return;
        
        var hook = plugin.getCoreProtectHook();
        if (hook == null || !hook.isEnabled()) return;
        
        hook.wasModifiedByOther(block.getLocation(), player.getName()).thenAccept(wasModified -> {
            if (wasModified) {
                lastGriefTime.put(player.getUniqueId(), now);
                plugin.getLogger().warning("Possible grief: " + player.getName() + " broke " + blockType);
                plugin.getDatabaseManager().logGriefAction(player, block);
                
                List<String> commands = player.hasPermission("cpa.staff") ?
                    config.getStringList("grief_detection.staff_grief_commands") :
                    config.getStringList("grief_detection.grief_commands");
                
                for (String cmd : commands) {
                    String processed = cmd
                        .replace("%player_name%", player.getName())
                        .replace("%world%", block.getWorld().getName())
                        .replace("%x%", String.valueOf(block.getX()))
                        .replace("%y%", String.valueOf(block.getY()))
                        .replace("%z%", String.valueOf(block.getZ()))
                        .replace("%block%", blockType);
                    CommandExecutor.execute(plugin, player, null, processed);
                }
                
                if (player.hasPermission("cpa.staff")) {
                    plugin.getAbuseScoreManager().addScore(player.getUniqueId(), "griefing", 
                        config.getInt("grief_detection.abuse_weight", 10));
                }
            }
        });
    }
}
