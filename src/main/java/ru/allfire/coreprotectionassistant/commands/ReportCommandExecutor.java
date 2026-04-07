package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.Color;

import java.util.Arrays;
import java.util.List;

public class ReportCommandExecutor implements CommandExecutor, TabCompleter {
    
    private final CoreProtectionAssistant plugin;
    
    public ReportCommandExecutor(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Color.colorize("&cThis command can only be used by players"));
            return true;
        }
        
        if (!player.hasPermission("cpa.report")) {
            player.sendMessage(Color.colorize("&cNo permission"));
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage(Color.colorize("&cUsage: /report <player> <reason>"));
            return true;
        }
        
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null || !target.isOnline()) {
            player.sendMessage(Color.colorize("&cPlayer is offline"));
            return true;
        }
        
        if (target.equals(player)) {
            player.sendMessage(Color.colorize("&cYou cannot report yourself"));
            return true;
        }
        
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        var result = plugin.getReportManager().createReport(player, target, reason);
        player.sendMessage(Color.colorize(result.message()));
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> !n.equals(sender.getName()))
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        return List.of();
    }
}
