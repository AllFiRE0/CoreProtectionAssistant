package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WarnCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    private final Pattern TIME_PATTERN = Pattern.compile("-t:(\\d+)([dhms])");
    
    public WarnCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "warn";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"warning"};
    }
    
    @Override
    public String getDescription() {
        return "Manage player warnings";
    }
    
    @Override
    public String getUsage() {
        return "/cpa warn <player> [reason] [-t:1d|1h|1m|1s] [-s]";
    }
    
    @Override
    public String getPermission() {
        return "cpa.warn";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "warn_usage");
            sender.sendMessage(Lang.colorize("&cUsage: /cpa warn clear <player> <amount> [-s]"));
            sender.sendMessage(Lang.colorize("&cUsage: /cpa warn list <player> [-s]"));
            return true;
        }
        
        if (args[0].equalsIgnoreCase("clear")) {
            return handleClear(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        
        if (args[0].equalsIgnoreCase("list")) {
            return handleList(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        
        return handleWarn(sender, args);
    }
    
    private boolean handleWarn(CommandSender sender, String[] args) {
        String targetName = args[0];
        
        boolean silent = false;
        long durationTicks = 0;
        List<String> reasonParts = new ArrayList<>();
        
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (arg.startsWith("-t:")) {
                durationTicks = parseTimeToTicks(arg);
            } else {
                reasonParts.add(arg);
            }
        }
        
        String reason = reasonParts.isEmpty() ? "No reason specified" : String.join(" ", reasonParts);
        
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            if (!silent) Lang.send(sender, "player_not_found", "player", targetName);
            return true;
        }
        
        UUID staffUuid = null;
        String staffName = "CONSOLE";
        if (sender instanceof Player player) {
            staffUuid = player.getUniqueId();
            staffName = player.getName();
        }
        
        plugin.getWarnManager().warnPlayer(
            offlineTarget.getUniqueId(),
            offlineTarget.getName(),
            staffUuid,
            staffName,
            reason,
            durationTicks
        );
        
        if (!silent) {
            String durationStr = durationTicks > 0 ? " (" + formatDuration(durationTicks) + ")" : "";
            Lang.send(sender, "warn_success", "player", offlineTarget.getName(), "reason", reason + durationStr);
            plugin.getLogger().info("Warning issued to " + offlineTarget.getName() + 
                " by " + staffName + ": " + reason + (durationTicks > 0 ? " [" + formatDuration(durationTicks) + "]" : ""));
        }
        
        return true;
    }
    
    private boolean handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cpa.warn.clear")) {
            Lang.send(sender, "no_permission");
            return true;
        }
        
        if (args.length < 2) {
            Lang.send(sender, "warn_clear_usage");
            return true;
        }
        
        boolean silent = false;
        String targetName = null;
        Integer amount = null;
        
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (targetName == null) {
                targetName = arg;
            } else if (amount == null) {
                try {
                    amount = Integer.parseInt(arg);
                    if (amount < 1) amount = 1;
                } catch (NumberFormatException e) {
                    // Пропускаем
                }
            }
        }
        
        if (targetName == null || amount == null) {
            if (!silent) Lang.send(sender, "warn_clear_usage");
            return true;
        }
        
        final String finalTargetName = targetName;
        final int finalAmount = amount;
        final boolean finalSilent = silent;
        
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(finalTargetName);
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            if (!finalSilent) Lang.send(sender, "player_not_found", "player", finalTargetName);
            return true;
        }
        
        String clearedBy = sender instanceof Player player ? player.getName() : "CONSOLE";
        plugin.getWarnManager().clearWarnings(offlineTarget.getUniqueId(), finalAmount, clearedBy);
        
        if (!finalSilent) {
            Lang.send(sender, "warn_clear_success", "amount", String.valueOf(finalAmount), "player", offlineTarget.getName());
            plugin.getLogger().info("Cleared " + finalAmount + " warnings from " + offlineTarget.getName() + " by " + clearedBy);
        }
        
        return true;
    }
    
    private boolean handleList(CommandSender sender, String[] args) {
        boolean silent = false;
        String targetName = null;
        
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (targetName == null) {
                targetName = arg;
            }
        }
        
        if (targetName == null) {
            if (!silent) Lang.send(sender, "warn_list_usage");
            return true;
        }
        
        final String finalTargetName = targetName;
        final boolean finalSilent = silent;
        
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(finalTargetName);
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            if (!finalSilent) Lang.send(sender, "player_not_found", "player", finalTargetName);
            return true;
        }
        
        plugin.getWarnManager().getWarnings(offlineTarget.getUniqueId()).thenAccept(warnings -> {
            if (warnings.isEmpty()) {
                if (!finalSilent) Lang.send(sender, "warn_no_warnings", "player", offlineTarget.getName());
                return;
            }
            
            if (!finalSilent) {
                String header = Lang.get("warn_list_header").replace("%player%", offlineTarget.getName());
                sender.sendMessage(Lang.colorize(header));
                
                for (var warn : warnings) {
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(warn.getCreatedAt()));
                    String expires = warn.getExpiresAt() > 0 ? 
                        " &7(до " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(warn.getExpiresAt())) + ")" : "";
                    
                    sender.sendMessage(Lang.colorize(
                        "&7[&f" + date + "&7] &c" + warn.getStaffName() + " &7→ &f" + warn.getReason() + expires
                    ));
                }
            }
        });
        
        return true;
    }
    
    private long parseTimeToTicks(String timeArg) {
        Matcher matcher = TIME_PATTERN.matcher(timeArg);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            
            long seconds = switch (unit) {
                case "d" -> value * 24L * 60 * 60;
                case "h" -> value * 60L * 60;
                case "m" -> value * 60L;
                case "s" -> value;
                default -> 0;
            };
            
            return seconds * 20;
        }
        return 0;
    }
    
    private String formatDuration(long ticks) {
        long seconds = ticks / 20;
        if (seconds >= 86400) return (seconds / 86400) + "d";
        if (seconds >= 3600) return (seconds / 3600) + "h";
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(
                Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList()
            );
            completions.add("clear");
            completions.add("list");
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length >= 2) {
            String firstArg = args[0].toLowerCase();
            
            if (firstArg.equals("clear")) {
                if (args.length == 2) {
                    List<String> players = new ArrayList<>();
                    // Онлайн игроки
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            players.add(p.getName());
                        }
                    }
                    // Оффлайн игроки (до 20)
                    int count = 0;
                    for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
                        if (count >= 20) break;
                        String name = off.getName();
                        if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase())) {
                            if (!players.contains(name)) {
                                players.add(name);
                                count++;
                            }
                        }
                    }
                    return players;
                }
                if (args.length == 3) {
                    return List.of("1", "3", "5", "10", "-s");
                }
                if (args.length == 4) {
                    return List.of("-s");
                }
            } else if (firstArg.equals("list")) {
                if (args.length == 2) {
                    List<String> players = new ArrayList<>();
                    // Онлайн игроки
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            players.add(p.getName());
                        }
                    }
                    // Оффлайн игроки (до 20)
                    int count = 0;
                    for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
                        if (count >= 20) break;
                        String name = off.getName();
                        if (name != null && name.toLowerCase().startsWith(args[1].toLowerCase())) {
                            if (!players.contains(name)) {
                                players.add(name);
                                count++;
                            }
                        }
                    }
                    return players;
                }
                if (args.length == 3) {
                    return List.of("-s");
                }
            } else {
                if (args.length == 2) {
                    return List.of("-t:1h", "-t:1d", "-t:30m", "-s");
                }
                if (args.length >= 3) {
                    return List.of("-t:1h", "-t:1d", "-t:30m", "-s");
                }
            }
        }
        
        return List.of();
    }
}
