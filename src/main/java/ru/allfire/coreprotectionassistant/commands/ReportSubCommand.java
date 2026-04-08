package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
            Lang.send(sender, "report_player_only");
            return true;
        }
        
        if (args.length < 2) {
            Lang.send(sender, "report_usage");
            return true;
        }
        
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            if (!offlineTarget.hasPlayedBefore()) {
                Lang.send(sender, "player_not_found", "player", targetName);
                return true;
            }
        }
        
        if (targetName.equalsIgnoreCase(player.getName())) {
            Lang.send(sender, "report_self");
            return true;
        }
        
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        var result = plugin.getReportManager().createReport(player, offlineTarget, reason);
        player.sendMessage(Lang.colorize(result.message()));
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (!name.equals(sender.getName()) && name.toLowerCase().startsWith(partialName)) {
                    suggestions.add(name);
                }
            }
            
            int offlineCount = 0;
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlineCount >= 20) break;
                
                String name = offlinePlayer.getName();
                if (name != null && !name.equals(sender.getName()) && name.toLowerCase().startsWith(partialName)) {
                    if (!suggestions.contains(name)) {
                        suggestions.add(name);
                        offlineCount++;
                    }
                }
            }
            
            suggestions.sort(String::compareToIgnoreCase);
            return suggestions;
        }
        
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return List.of("Griefing", "Cheating", "Offensive language", "Spam", "Trolling").stream()
                .filter(cat -> cat.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        
        return List.of();
    }
}