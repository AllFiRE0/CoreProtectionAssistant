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
        
        // Извлекаем полную команду (включая префикс плагина)
        String fullCommand = extractFullCommand(message);
        
        // Извлекаем только название команды (без префикса)
        String command = extractCommand(message);
        
        String[] args = extractArgs(message);
        
        boolean isStaff = player.hasPermission("cpa.staff") || player.hasPermission("cpa.moder");
        
        if (debug) {
            plugin.getLogger().info("[CommandListener] Player: " + player.getName() + 
                ", Full command: " + fullCommand + ", Command: " + command + ", isStaff: " + isStaff);
        }
        
        // Проверяем, нужно ли логировать (по полной команде или только по названию)
        boolean shouldLog = false;
        
        if (isStaff) {
            shouldLog = trackedModerCommands.contains(fullCommand.toLowerCase()) || 
                       trackedModerCommands.contains(command.toLowerCase());
        } else {
            shouldLog = trackedPlayerCommands.contains(fullCommand.toLowerCase()) || 
                       trackedPlayerCommands.contains(command.toLowerCase());
        }
        
        if (shouldLog) {
            if (debug) {
                plugin.getLogger().info("[CommandListener] Logging: " + fullCommand);
            }
            
            // Логируем в cpa_player_commands
            plugin.getDatabaseManager().logPlayerCommand(
                player.getUniqueId(),
                player.getName(),
                fullCommand,  // Сохраняем полную команду (cmi give)
                args,
                message,
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                isStaff
            );
            
            // Для персонала логируем в cpa_staff_actions
            if (isStaff) {
                plugin.getDatabaseManager().logStaffAction(
                    player.getUniqueId(),
                    player.getName(),
                    fullCommand.toUpperCase(),
                    args.length > 0 ? args[0] : null,
                    args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null,
                    player.getWorld().getName(),
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()
                );
                
                plugin.getStaffManager().processStaffCommand(player, fullCommand, args);
            }
        }
        
        // Проверяем супер-команды
        checkSuperCommand(player, fullCommand, args);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (event.isCancelled()) return;
        
        String message = event.getCommand();
        String fullCommand = extractFullCommand(message);
        String command = extractCommand(message);
        
        if (trackedModerCommands.contains(fullCommand.toLowerCase()) || 
            trackedModerCommands.contains(command.toLowerCase())) {
            
            plugin.getDatabaseManager().logPlayerCommand(
                null,
                "CONSOLE",
                fullCommand,
                extractArgs(message),
                message,
                "N/A",
                0, 0, 0,
                true
            );
        }
    }
    
    /**
     * Извлекает полную команду с префиксом плагина (например, "cmi give")
     */
    private String extractFullCommand(String message) {
        // Убираем начальный слэш
        String cmd = message.substring(1).split(" ")[0];
        // Если есть второй аргумент (подкоманда), добавляем его
        String[] parts = message.substring(1).split(" ");
        if (parts.length > 1) {
            return cmd + " " + parts[1];
        }
        return cmd;
    }
    
    /**
     * Извлекает только название команды (например, "give" из "cmi give")
     */
    private String extractCommand(String message) {
        String full = extractFullCommand(message);
        // Если есть пробел, берём вторую часть
        if (full.contains(" ")) {
            return full.split(" ")[1];
        }
        // Если есть двоеточие (cmi:give)
        if (full.contains(":")) {
            return full.split(":")[1];
        }
        return full;
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
            if (command.equalsIgnoreCase(cmdName) || command.endsWith(":" + cmdName)) {
                boolean track = superCommands.getBoolean(cmdName + ".track", false);
                if (track) {
                    plugin.getDatabaseManager().saveSuperCommand(player, command, args);
                }
                break;
            }
        }
    }
}
