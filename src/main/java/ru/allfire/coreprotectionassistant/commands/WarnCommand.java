package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class WarnCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    
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
        return "/cpa warn <player> [reason]";
    }
    
    @Override
    public String getPermission() {
        return "cpa.warn";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "warn_usage");
            sender.sendMessage(Lang.colorize("&cUsage: /cpa warn clear <player> <amount>"));
            sender.sendMessage(Lang.colorize("&cUsage: /cpa warn list <player>"));
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
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            Lang.send(sender, "player_not_found", "player", targetName);
            return true;
        }
        
        String reason = args.length > 1 ? 
            String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : 
            "No reason specified";
        
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
            0
        );
        
        Lang.send(sender, "warn_success", "player", offlineTarget.getName(), "reason", reason);
        
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
        
        String targetName = args[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            Lang.send(sender, "player_not_found", "player", targetName);
            return true;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount < 1) amount = 1;
        } catch (NumberFormatException e) {
            Lang.send(sender, "warn_invalid_amount");
            return true;
        }
        
        String clearedBy = sender instanceof Player player ? player.getName() : "CONSOLE";
        
        plugin.getWarnManager().clearWarnings(offlineTarget.getUniqueId(), amount, clearedBy);
        
        Lang.send(sender, "warn_clear_success", "amount", String.valueOf(amount), "player", offlineTarget.getName());
        
        return true;
    }
    
    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "warn_list_usage");
            return true;
        }
        
        String targetName = args[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            Lang.send(sender, "player_not_found", "player", targetName);
            return true;
        }
        
        plugin.getWarnManager().getWarnings(offlineTarget.getUniqueId()).thenAccept(warnings -> {
            if (warnings.isEmpty()) {
                Lang.send(sender, "warn_no_warnings", "player", offlineTarget.getName());
                return;
            }
            
            String header = Lang.get("warn_list_header").replace("%player%", offlineTarget.getName());
            if (!header.isEmpty()) {
                sender.sendMessage(Lang.colorize(header));
            }
            
            for (var warn : warnings) {
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm")
                    .format(new Date(warn.getCreatedAt()));
                
                sender.sendMessage(Lang.colorize(
                    "&7[&f" + date + "&7] &c" + warn.getStaffName() + " &7→ &f" + warn.getReason()
                ));
            }
        });
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new java.util.ArrayList<>(
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
        
        if (args.length == 2 && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("list"))) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("clear")) {
            return List.of("1", "3", "5", "10");
        }
        
        return List.of();
    }
}