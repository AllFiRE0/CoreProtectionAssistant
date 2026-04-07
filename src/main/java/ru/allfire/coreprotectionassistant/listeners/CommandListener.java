package ru.allfire.coreprotectionassistant.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.models.CommandLog;

import java.util.Arrays;
import java.util.List;

public class CommandListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    private final List<String> trackedModerCommands;
    private final List<String> trackedPlayerCommands;
    
    public CommandListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        this.trackedModerCommands = plugin.getConfigManager().getMainConfig()
            .getStringList("tracked_moder_commands");
        this.trackedPlayerCommands = plugin.getConfigManager().getMainConfig()
            .getStringList("tracked_player_commands");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        String message = event.getMessage();
        String command = extractCommand(message);
        String[] args = extractArgs(message);
        
        // Проверяем, нужно ли логировать
        boolean shouldLog = false;
        
        if (player.hasPermission("cpa.staff") || player.hasPermission("cpa.moder")) {
            shouldLog = trackedModerCommands.contains(command.toLowerCase());
        } else {
            shouldLog = trackedPlayerCommands.contains(command.toLowerCase());
        }
        
        if (shouldLog) {
            CommandLog log = CommandLog.builder()
                .playerUuid(player.getUniqueId())
                .playerName(player.getName())
                .command(command)
                .args(args)
                .fullCommand(message)
                .world(player.getWorld().getName())
                .x(player.getLocation().getX())
                .y(player.getLocation().getY())
                .z(player.getLocation().getZ())
                .timestamp(System.currentTimeMillis())
                .isStaff(player.hasPermission("cpa.staff"))
                .build();
            
            plugin.getDatabaseManager().saveCommandLog(log);
        }
        
        // Проверяем супер-команды
        checkSuperCommand(player, command, args);
        
        // Проверяем действия персонала
        if (player.hasPermission("cpa.staff")) {
            plugin.getStaffManager().processStaffCommand(player, command, args);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (event.isCancelled()) return;
        
        String message = event.getCommand();
        String command = extractCommand(message);
        
        // Логируем команды консоли
        if (trackedModerCommands.contains(command.toLowerCase())) {
            CommandLog log = CommandLog.builder()
                .playerUuid(null)
                .playerName("CONSOLE")
                .command(command)
                .args(extractArgs(message))
                .fullCommand(message)
                .world("N/A")
                .x(0)
                .y(0)
                .z(0)
                .timestamp(System.currentTimeMillis())
                .isStaff(true)
                .build();
            
            plugin.getDatabaseManager().saveCommandLog(log);
        }
    }
    
    private String extractCommand(String message) {
        String cmd = message.substring(1).split(" ")[0];
        if (cmd.contains(":")) {
            cmd = cmd.split(":")[1];
        }
        return cmd;
    }
    
    private String[] extractArgs(String message) {
        String[] parts = message.split(" ");
        if (parts.length <= 1) {
            return new String[0];
        }
        return Arrays.copyOfRange(parts, 1, parts.length);
    }
    
    private void checkSuperCommand(Player player, String command, String[] args) {
        var superCommands = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("super_commands");
        
        if (superCommands == null) return;
        
        for (String cmdName : superCommands.getKeys(false)) {
            if (command.equalsIgnoreCase(cmdName)) {
                boolean track = superCommands.getBoolean(cmdName + ".track", false);
                if (track) {
                    plugin.getDatabaseManager().saveSuperCommand(player, command, args);
                }
                break;
            }
        }
    }
}
