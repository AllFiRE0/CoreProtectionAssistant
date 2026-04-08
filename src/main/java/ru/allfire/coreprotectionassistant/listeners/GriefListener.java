package ru.allfire.coreprotectionassistant.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.CommandExecutor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GriefListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    private final Map<UUID, Long> lastGriefTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractTime = new ConcurrentHashMap<>();
    
    public GriefListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Проверяет, разрешён ли антигриф в этом регионе WorldGuard
     */
    private boolean isGriefAllowedInRegion(Block block) {
        var config = plugin.getConfigManager().getMainConfig();
        List<String> allowedRegions = config.getStringList("grief_detection.allowed_regions");
        
        // Если список пуст - работаем везде (кроме защищённых регионов)
        if (allowedRegions.isEmpty()) {
            return !isInProtectedRegion(block);
        }
        
        // Проверяем WorldGuard
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return true; // WorldGuard не установлен - работаем везде
        }
        
        try {
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.regions.RegionContainer container = wg.getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location loc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(block.getLocation());
            
            com.sk89q.worldguard.protection.ApplicableRegionSet set = query.getApplicableRegions(loc);
            
            // Проверяем, есть ли среди регионов разрешённые
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : set) {
                String regionId = region.getId();
                if (allowedRegions.contains(regionId)) {
                    return true; // Нашли разрешённый регион
                }
            }
            
            return false; // Нет разрешённых регионов
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check WorldGuard region: " + e.getMessage());
            return true; // При ошибке - разрешаем (на всякий случай)
        }
    }
    
    /**
     * Проверяет, находится ли блок в защищённом регионе (НЕ __global__)
     */
    private boolean isInProtectedRegion(Block block) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return false;
        }
        
        try {
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.regions.RegionContainer container = wg.getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location loc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(block.getLocation());
            
            com.sk89q.worldguard.protection.ApplicableRegionSet set = query.getApplicableRegions(loc);
            
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : set) {
                String regionId = region.getId();
                if (!regionId.equals("__global__") && !regionId.equals("global")) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Проверяем, разрешён ли антигриф в этом регионе
        if (!isGriefAllowedInRegion(block)) return;
        
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
        
        hook.getBlockOwner(block.getLocation()).thenAccept(owner -> {
            if (owner == null || owner.equalsIgnoreCase(player.getName())) {
                plugin.getLogger().info("Block broken by owner or unknown: " + player.getName());
                return;
            }
            
            lastGriefTime.put(player.getUniqueId(), now);
            plugin.getLogger().warning("Possible grief: " + player.getName() + " broke " + blockType + " owned by " + owner);
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
                    .replace("%block%", blockType)
                    .replace("%owner%", owner);
                CommandExecutor.execute(plugin, player, null, processed);
            }
            
            if (player.hasPermission("cpa.staff")) {
                plugin.getAbuseScoreManager().addScore(player.getUniqueId(), "griefing", 
                    config.getInt("grief_detection.abuse_weight", 10));
            }
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isContainer(block.getType())) return;
        
        // Проверяем, разрешён ли антигриф в этом регионе
        if (!isGriefAllowedInRegion(block)) return;
        
        Player player = event.getPlayer();
        var config = plugin.getConfigManager().getMainConfig();
        
        if (!config.getBoolean("grief_detection.enabled", false)) return;
        
        String blockType = block.getType().name();
        List<String> trackedBlocks = config.getStringList("grief_detection.tracked_blocks");
        if (!trackedBlocks.contains(blockType)) return;
        
        long now = System.currentTimeMillis();
        Long lastTime = lastInteractTime.get(player.getUniqueId());
        if (lastTime != null && (now - lastTime) < 2000) return;
        
        var hook = plugin.getCoreProtectHook();
        if (hook == null || !hook.isEnabled()) return;
        
        hook.getBlockOwner(block.getLocation()).thenAccept(owner -> {
            if (owner != null && owner.equalsIgnoreCase(player.getName())) return;
            
            lastInteractTime.put(player.getUniqueId(), now);
            plugin.getLogger().info("Player " + player.getName() + " interacted with " + 
                blockType + " owned by " + (owner != null ? owner : "unknown"));
        });
    }
    
    private boolean isContainer(Material material) {
        String name = material.name();
        return name.contains("CHEST") || name.contains("SHULKER") || name.contains("BARREL") ||
               name.contains("FURNACE") || name.contains("HOPPER") || name.contains("DISPENSER") ||
               name.contains("DROPPER") || name.contains("BREWING_STAND");
    }
}
