package ru.allfire.coreprotectionassistant.utils;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

public class CommandExecutor {
    
    public static void execute(CoreProtectionAssistant plugin, Player player, String target, String command) {
        if (command == null || command.isEmpty()) {
            return;
        }
        
        String processed = command;
        
        // Заменяем переменные
        if (player != null) {
            processed = processed
                .replace("%player_name%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName())
                .replace("%x%", String.valueOf(player.getLocation().getBlockX()))
                .replace("%y%", String.valueOf(player.getLocation().getBlockY()))
                .replace("%z%", String.valueOf(player.getLocation().getBlockZ()));
        }
        
        if (target != null) {
            processed = processed.replace("%target%", target);
        }
        
        // Определяем префикс
        String prefix = "";
        String cmd = processed;
        
        if (processed.contains("!")) {
            String[] parts = processed.split("!", 2);
            prefix = parts[0];
            cmd = parts[1];
        }
        
        // Выполняем в зависимости от префикса
        switch (prefix.toLowerCase()) {
            case "asconsole" -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Color.strip(cmd));
            }
            case "asplayer" -> {
                if (player != null) {
                    player.performCommand(Color.strip(cmd));
                }
            }
            case "message" -> {
                if (player != null) {
                    player.sendMessage(Color.colorize(cmd));
                }
            }
            case "message_target" -> {
                if (target != null) {
                    Player targetPlayer = Bukkit.getPlayer(target);
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(Color.colorize(cmd));
                    }
                }
            }
            case "broadcast" -> {
                Bukkit.broadcastMessage(Color.colorize(cmd));
            }
            case "title" -> {
                if (player != null) {
                    player.sendTitle(Color.colorize(cmd), "", 10, 70, 20);
                }
            }
            case "subtitle" -> {
                if (player != null) {
                    player.sendTitle("", Color.colorize(cmd), 10, 70, 20);
                }
            }
            case "actionbar" -> {
                if (player != null) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(Color.colorize(cmd)));
                }
            }
            case "sound" -> {
                if (player != null) {
                    String[] soundParts = cmd.split(" ");
                    try {
                        Sound sound = Sound.valueOf(soundParts[0].toUpperCase());
                        float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                        float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                        player.playSound(player.getLocation(), sound, volume, pitch);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound: " + soundParts[0]);
                    }
                }
            }
            case "delay" -> {
                try {
                    int ticks = Integer.parseInt(cmd.trim());
                    // Для delay нужно выполнять синхронно с задержкой
                    // Это обрабатывается отдельно в вызывающем коде
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid delay ticks: " + cmd);
                }
            }
            case "log" -> {
                plugin.getLogger().info("[CPA] " + Color.strip(cmd));
            }
            default -> {
                // Без префикса - выполняем от консоли
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Color.strip(processed));
            }
        }
    }
    
    public static void executeWithDelay(CoreProtectionAssistant plugin, Player player, 
                                         String target, String command, int delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, 
            () -> execute(plugin, player, target, command), delayTicks);
    }
}
