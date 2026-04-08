package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;
import ru.allfire.coreprotectionassistant.hooks.CoreProtectHook;
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
            Lang.send(sender, "stats_usage");
            return true;
        }
        
        String targetName = args[0];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            Lang.send(sender, "player_not_found", "player", targetName);
            return true;
        }
        
        UUID targetUuid = offlineTarget.getUniqueId();
        
        Lang.send(sender, "stats_header", "player", offlineTarget.getName());
        
        CoreProtectHook hook = plugin.getCoreProtectHook();
        
        // Блоки из CoreProtect
        hook.getBlocksBroken(targetUuid, 0).thenAccept(count -> {
            Lang.send(sender, "stats_blocks_broken", "value", String.valueOf(count));
        });
        
        hook.getBlocksPlaced(targetUuid, 0).thenAccept(count -> {
            Lang.send(sender, "stats_blocks_placed", "value", String.valueOf(count));
        });
        
        hook.getChestsOpened(targetUuid, 0).thenAccept(count -> {
            Lang.send(sender, "stats_chests_opened", "value", String.valueOf(count));
        });
        
        // Команды из нашей БД
        plugin.getDatabaseManager().getTotalCommandsUsed(targetUuid).thenAccept(count -> {
            Lang.send(sender, "stats_commands", "value", String.valueOf(count));
        });
        
        // Смерти/убийства из CoreProtect
        hook.getDeaths(targetUuid, 0).thenAccept(count -> {
            Lang.send(sender, "stats_deaths", "value", String.valueOf(count));
        });
        
        hook.getKills(targetUuid, 0).thenAccept(count -> {
            Lang.send(sender, "stats_kills", "value", String.valueOf(count));
        });
        
        // Время
        CompletableFuture.allOf(
            hook.getFirstSeen(targetUuid).thenAccept(time -> {
                String formatted = time > 0 ? TimeUtil.formatDateTime(time) : Lang.get("time_never");
                Lang.send(sender, "stats_first_seen", "value", formatted);
            }),
            
            hook.getLastSeen(targetUuid).thenAccept(time -> {
                String formatted = time > 0 ? TimeUtil.formatDateTime(time) : Lang.get("time_never");
                Lang.send(sender, "stats_last_seen", "value", formatted);
            })
        );
        
        // Предупреждения
        plugin.getWarnManager().getActiveWarningsCount(targetUuid).thenAccept(count -> {
            Lang.send(sender, "stats_warnings", "value", String.valueOf(count));
        });
        
        // Нарушения чата
        plugin.getDatabaseManager().getViolationCount(targetUuid).thenAccept(count -> {
            Lang.send(sender, "stats_violations", "value", String.valueOf(count));
        });
        
        // Извинения
        plugin.getDatabaseManager().getApologiesCount(targetUuid).thenAccept(count -> {
            Lang.send(sender, "stats_apologies", "value", String.valueOf(count));
        });
        
        // Соотношение
        plugin.getDatabaseManager().getViolationsApologiesRatio(targetUuid).thenAccept(ratio -> {
            Lang.send(sender, "stats_ratio", "value", ratio);
        });
        
        return true;
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
