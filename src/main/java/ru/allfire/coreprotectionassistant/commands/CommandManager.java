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

import java.util.*;

public class CommandManager implements CommandExecutor, TabCompleter {
    
    private final CoreProtectionAssistant plugin;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    
    public CommandManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public void registerAll() {
        registerSubCommand(new StatsCommand(plugin));
        registerSubCommand(new StaffCommand(plugin));
        registerSubCommand(new TopCommand(plugin));
        registerSubCommand(new CheckCommand(plugin));
        registerSubCommand(new WarnCommand(plugin));
        registerSubCommand(new ReloadCommand(plugin));
        registerSubCommand(new ReportSubCommand(plugin));
        
        var mainCommand = plugin.getCommand("coreprotectionassistant");
        if (mainCommand != null) {
            mainCommand.setExecutor(this);
            mainCommand.setTabCompleter(this);
        }
        
        var reportCommand = plugin.getCommand("report");
        if (reportCommand != null) {
            reportCommand.setExecutor(new ReportCommandExecutor(plugin));
        }
    }
    
    private void registerSubCommand(SubCommand command) {
        subCommands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            subCommands.put(alias.toLowerCase(), command);
        }
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sender.sendMessage(Color.colorize("&cUnknown command. Use /cpa help"));
            return true;
        }
        
        if (!sender.hasPermission(subCommand.getPermission())) {
            sender.sendMessage(Color.colorize("&cNo permission"));
            return true;
        }
        
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        try {
            return subCommand.execute(sender, subArgs);
        } catch (Exception e) {
            sender.sendMessage(Color.colorize("&cError executing command"));
            plugin.getLogger().severe("Error executing command " + subCommandName + ": " + e.getMessage());
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String cmd : subCommands.keySet()) {
                SubCommand sub = subCommands.get(cmd);
                if (cmd.equals(sub.getName().toLowerCase()) && 
                    sender.hasPermission(sub.getPermission())) {
                    if (cmd.startsWith(args[0].toLowerCase())) {
                        completions.add(cmd);
                    }
                }
            }
            return completions;
        }
        
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand != null && sender.hasPermission(subCommand.getPermission())) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return subCommand.tabComplete(sender, subArgs);
        }
