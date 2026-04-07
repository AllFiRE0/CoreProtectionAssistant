package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.command.CommandSender;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.Color;

import java.util.List;

public class ReloadCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    
    public ReloadCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "reload";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"rl"};
    }
    
    @Override
    public String getDescription() {
        return "Reload configuration";
    }
    
    @Override
    public String getUsage() {
        return "/cpa reload";
    }
    
    @Override
    public String getPermission() {
        return "cpa.reload";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        long startTime = System.currentTimeMillis();
        
        plugin.getConfigManager().reloadAll();
        plugin.getChatRuleManager().loadRules();
        plugin.getReportManager().reload();
        
        long loadTime = System.currentTimeMillis() - startTime;
        
        String successMsg = plugin.getConfigManager().getLangConfig()
            .getString("messages.reload_success",
                "%prefix% &aConfiguration reloaded in &f%time%ms&a.")
            .replace("%time%", String.valueOf(loadTime));
        
        sender.sendMessage(Color.colorize(successMsg));
        
        plugin.getLogger().info("Configuration reloaded by " + sender.getName() + " in " + loadTime + "ms");
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
