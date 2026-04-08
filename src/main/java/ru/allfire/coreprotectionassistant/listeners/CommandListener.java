package ru.allfire.coreprotectionassistant.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.util.Arrays;
import java.util.List;

public class CommandListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    private List<String> trackedModerCommands;
    private List<String> trackedPlayerCommands;
    private boolean debug;
    
    public CommandListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        reloadConfig();
    }
    
    public void reloadConfig() {
        this.trackedModerCommands = plugin.getConfigManager().getMainConfig()
            .getStringList("tracked_moder_commands");
        this.trackedPlayerCommands = plugin.getConfigManager().getMainConfig()
            .getStringList("tracked_player_commands");
        this.debug = plugin.getConfigManager().getMainConfig().getBoolean("debug", false);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        String message = event.getMessage();
        String command = extractCommand(message);
        String[] args = extractArgs(message);
        
        boolean isStaff = player.hasPermission("cpa.staff") || player.hasPermission("cpa.moder");
        
        if (debug) {
            plugin.getLogger().info("[CommandListener] Player: " + player.getName() + 
                ", Command: " + command + ", isStaff: " + isStaff);
        }
        
        // Логируем ВСЕ команды в cpa_player_commands
        boolean shouldLogInPlayerCommands = false;
        
        if (isStaff) {
            shouldLogInPlayerCommands = trackedModerCommands.contains(command.toLowerCase());
        } else {
            shouldLogInPlayerCommands = trackedPlayerCommands.contains(command.toLowerCase());
        }
        
        if (shouldLogInPlayerCommands) {
            if (debug) {
                plugin.getLogger().info("[CommandListener] Logging to cpa_player_commands: " + command);
            }
            
            plugin.getDatabaseManager().logPlayerCommand(
                player.getUniqueId(),
                player.getName(),
                command,
                args,
                message,
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                isStaff
            );
        }
        
        // Дополнительно логируем действия персонала в cpa_staff_actions
        if (isStaff && trackedModerCommands.contains(command.toLowerCase())) {
            if (debug) {
                plugin.getLogger().info("[CommandListener] Logging to cpa_staff_actions: " + command);
            }
            
            plugin.getDatabaseManager().logStaffAction(
                player.getUniqueId(),
                player.getName(),
                command.toUpperCase(),
                args.length > 0 ? args[0] : null,
                args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null,
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            );
            
            plugin.getStaffManager().processStaffCommand(player, command, args);
        }
        
        // Проверяем супер-команды
        checkSuperCommand(player, command, args);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (event.isCancelled()) return;
        
        String message = event.getCommand();
        String command = extractCommand(message);
        
        if (trackedModerCommands.contains(command.toLowerCase())) {
            if (debug) {
                plugin.getLogger().info("[CommandListener] Console command logged: " + command);
            }
            
            plugin.getDatabaseManager().logPlayerCommand(
                null,
                "CONSOLE",
                command,
                extractArgs(message),
                message,
                "N/A",
                0, 0, 0,
                true
            );
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
