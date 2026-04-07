package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.Color;

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
            sender.sendMessage(Color.colorize("&cThis command can only be used by players"));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(Color.colorize("&cUsage: " + getUsage()));
            return true;
        }
        
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        // Проверяем, что цель существует (онлайн или оффлайн)
        if (target == null) {
            // Проверяем, играл ли игрок когда-нибудь на сервере
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            if (!offlineTarget.hasPlayedBefore()) {
                sender.sendMessage(Color.colorize("&cPlayer not found"));
                return true;
            }
        }
        
        // Проверяем, что не жалуется сам на себя
        if (targetName.equalsIgnoreCase(player.getName())) {
            sender.sendMessage(Color.colorize("&cYou cannot report yourself"));
            return true;
        }
        
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // Создаём жалобу (работает и для оффлайн игроков)
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        var result = plugin.getReportManager().createReport(player, offlineTarget, reason);
        player.sendMessage(Color.colorize(result.message()));
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Предлагаем ВСЕХ игроков, которые когда-либо заходили на сервер
            String partialName = args[0].toLowerCase();
            
            List<String> suggestions = new ArrayList<>();
            
            // Сначала добавляем онлайн игроков (они в приоритете)
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (!name.equals(sender.getName()) && name.toLowerCase().startsWith(partialName)) {
                    suggestions.add(name);
                }
            }
            
            // Затем добавляем оффлайн игроков (максимум 20, чтобы не лагать)
            int offlineCount = 0;
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlineCount >= 20) break;
                
                String name = offlinePlayer.getName();
                if (name != null && !name.equals(sender.getName()) && name.toLowerCase().startsWith(partialName)) {
                    // Не добавляем дубликаты (игрок уже в списке онлайн)
                    if (!suggestions.contains(name)) {
                        suggestions.add(name);
                        offlineCount++;
                    }
                }
            }
            
            // Сортируем по алфавиту
            suggestions.sort(String::compareToIgnoreCase);
            
            return suggestions;
        }
        
        if (args.length == 2) {
            // Предлагаем категории жалоб
            String partial = args[1].toLowerCase();
            return List.of("Griefing", "Cheating", "Offensive language", "Spam", "Trolling").stream()
                .filter(cat -> cat.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        
        return List.of();
    }
}
