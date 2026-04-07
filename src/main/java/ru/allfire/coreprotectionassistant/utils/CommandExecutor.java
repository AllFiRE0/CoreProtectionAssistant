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
            prefix = parts[0].toLowerCase();
            cmd = parts[1];
        }
        
        final String finalCmd = cmd.trim();
        final String finalPrefix = prefix;
        final Player finalPlayer = player;
        final String finalTarget = target;
        
        // Проверяем, в каком потоке мы находимся
        boolean isPrimaryThread = Bukkit.isPrimaryThread();
        
        // Команды, которые МОГУТ выполняться асинхронно (сообщения, звуки, тайтлы)
        switch (finalPrefix) {
            case "message" -> {
                if (finalPlayer != null) {
                    finalPlayer.sendMessage(Color.colorize(finalCmd));
                }
            }
            case "message_target" -> {
                if (finalTarget != null) {
                    Player targetPlayer = Bukkit.getPlayer(finalTarget);
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(Color.colorize(finalCmd));
                    }
                }
            }
            case "title" -> {
                if (finalPlayer != null) {
                    finalPlayer.sendTitle(Color.colorize(finalCmd), "", 10, 70, 20);
                }
            }
            case "subtitle" -> {
                if (finalPlayer != null) {
                    finalPlayer.sendTitle("", Color.colorize(finalCmd), 10, 70, 20);
                }
            }
            case "actionbar" -> {
                if (finalPlayer != null) {
                    finalPlayer.sendActionBar(net.kyori.adventure.text.Component.text(Color.colorize(finalCmd)));
                }
            }
            case "sound" -> {
                if (finalPlayer != null) {
                    String[] soundParts = finalCmd.split(" ");
                    try {
                        Sound sound = Sound.valueOf(soundParts[0].toUpperCase());
                        float volume = soundParts.length > 1 ? Float.parseFloat(soundParts[1]) : 1.0f;
                        float pitch = soundParts.length > 2 ? Float.parseFloat(soundParts[2]) : 1.0f;
                        finalPlayer.playSound(finalPlayer.getLocation(), sound, volume, pitch);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid sound: " + soundParts[0]);
                    }
                }
            }
            case "log" -> {
                plugin.getLogger().info("[CPA] " + Color.strip(finalCmd));
            }
            
            // Команды, которые ДОЛЖНЫ выполняться в ОСНОВНОМ потоке
            case "asconsole" -> {
                if (isPrimaryThread) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Color.strip(finalCmd));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Color.strip(finalCmd));
                    });
                }
            }
            case "asplayer" -> {
                if (finalPlayer != null) {
                    if (isPrimaryThread) {
                        finalPlayer.performCommand(Color.strip(finalCmd));
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            finalPlayer.performCommand(Color.strip(finalCmd));
                        });
                    }
                }
            }
            case "broadcast" -> {
                if (isPrimaryThread) {
                    Bukkit.broadcastMessage(Color.colorize(finalCmd));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.broadcastMessage(Color.colorize(finalCmd));
                    });
                }
            }
            
            // Без префикса - по умолчанию выполняем от консоли в основном потоке
            default -> {
                if (finalCmd.startsWith("/")) {
                    String strippedCmd = Color.strip(finalCmd.substring(1));
                    if (isPrimaryThread) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strippedCmd);
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strippedCmd);
                        });
                    }
                }
            }
        }
    }
    
    public static void executeWithDelay(CoreProtectionAssistant plugin, Player player, 
                                         String target, String command, int delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, 
            () -> execute(plugin, player, target, command), delayTicks);
    }
}
