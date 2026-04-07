package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.Color;

import java.util.Arrays;
import java.util.List;

public class ReportSubCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    
    public ReportSubCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "report";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"rep"};
    }
    
    @Override
    public String getDescription() {
        return "Report a player";
    }
    
    @Override
    public String getUsage() {
        return "/cpa report <player> <reason>";
    }
    
    @Override
    public String getPermission() {
        return "cpa.report";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Color.colorize("&cThis command can only be used by players"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(Color.colorize("&cUsage: " + getUsage()));
            return true;
        }
        
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Color.colorize("&cPlayer not found or offline"));
            return true;
        }
        
        if (target.equals(player)) {
            sender.sendMessage(Color.colorize("&cYou cannot report yourself"));
            return true;
        }
        
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        var result = plugin.getReportManager().createReport(player, target, reason);
        player.sendMessage(Color.colorize(result.message()));
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
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
