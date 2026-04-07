package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.hooks.CoreProtectHook;
import ru.allfire.coreprotectionassistant.utils.Color;
import ru.allfire.coreprotectionassistant.utils.TimeUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StatsCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    
    public StatsCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "stats";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"stat", "info"};
    }
    
    @Override
    public String getDescription() {
        return "View player statistics";
    }
    
    @Override
    public String getUsage() {
        return "/cpa stats <player>";
    }
    
    @Override
    public String getPermission() {
        return "cpa.moder";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Color.colorize("&cUsage: " + getUsage()));
            return true;
        }
        
        String targetName = args[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            sender.sendMessage(Color.colorize(
                plugin.getConfigManager().getLangConfig().getString("messages.player_not_found",
                    "%prefix% &cPlayer not found")
                    .replace("%player%", targetName)
            ));
            return true;
        }
        
        UUID targetUuid = offlineTarget.getUniqueId();
        Player onlineTarget = offlineTarget.getPlayer();
        
        sender.sendMessage(Color.colorize(
            plugin.getConfigManager().getLangConfig().getString("messages.stats_header",
                "&8&m-----&r &cStats: &f%player% &8&m-----")
                .replace("%player%", offlineTarget.getName())
        ));
        
        CoreProtectHook hook = plugin.getCoreProtectHook();
        
        // Блоки
        hook.getBlocksBroken(targetUuid, 0).thenAccept(count -> {
            sendMessage(sender, "stats_blocks_broken", count);
        });
        
        hook.getBlocksPlaced(targetUuid, 0).thenAccept(count -> {
            sendMessage(sender, "stats_blocks_placed", count);
        });
        
        hook.getChestsOpened(targetUuid, 0).thenAccept(count -> {
            sendMessage(sender, "stats_chests_opened", count);
        });
        
        // Команды
        hook.getCommandsUsed(targetUuid, 0).thenAccept(count -> {
            sendMessage(sender, "stats_commands", count);
        });
        
        // Смерти/убийства
        hook.getDeaths(targetUuid, 0).thenAccept(count -> {
            sendMessage(sender, "stats_deaths", count);
        });
        
        hook.getKills(targetUuid, 0).thenAccept(count -> {
            sendMessage(sender, "stats_kills", count);
        });
        
        // Время
        CompletableFuture.allOf(
            hook.getFirstSeen(targetUuid).thenAccept(time -> {
                String formatted = time > 0 ? TimeUtil.formatDateTime(time) : 
                    plugin.getConfigManager().getLangConfig().getString("time_never", "Never");
                sendMessage(sender, "stats_first_seen", formatted);
            }),
            
            hook.getLastSeen(targetUuid).thenAccept(time -> {
                String formatted = time > 0 ? TimeUtil.formatDateTime(time) : 
                    plugin.getConfigManager().getLangConfig().getString("time_never", "Never");
                sendMessage(sender, "stats_last_seen", formatted);
            })
        );
        
        // Предупреждения
        plugin.getWarnManager().getActiveWarningsCount(targetUuid).thenAccept(count -> {
            sendMessage(sender, "stats_warnings", count);
        });
        
        // Нарушения чата
        plugin.getDatabaseManager().getViolationCount(targetUuid).thenAccept(count -> {
            sendMessage(sender, "stats_violations", count);
        });
        
        return true;
    }
    
    private void sendMessage(CommandSender sender, String key, Object value) {
        String message = plugin.getConfigManager().getLangConfig()
            .getString("messages." + key, "&7" + key + ": &f%value%")
            .replace("%value%", String.valueOf(value));
        sender.sendMessage(Color.colorize(message));
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
