package ru.allfire.coreprotectionassistant.commands;

import org.bukkit.command.CommandSender;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.config.Lang;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TopCommand implements CommandManager.SubCommand {
    
    private final CoreProtectionAssistant plugin;
    
    private static final List<String> VALID_TYPES = List.of(
        "blocks_broken", "blocks_placed", "playtime", 
        "deaths", "kills", "reports", "warnings"
    );
    
    public TopCommand(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "top";
    }
    
    @Override
    public String[] getAliases() {
        return new String[]{"leaderboard", "lb"};
    }
    
    @Override
    public String getDescription() {
        return "View top players by category";
    }
    
    @Override
    public String getUsage() {
        return "/cpa top <type> [page]";
    }
    
    @Override
    public String getPermission() {
        return "cpa.moder";
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            Lang.send(sender, "top_usage");
            sender.sendMessage(Lang.colorize("&7Types: " + String.join(", ", VALID_TYPES)));
            return true;
        }
        
        String type = args[0].toLowerCase();
        
        if (!VALID_TYPES.contains(type)) {
            Lang.send(sender, "top_invalid_type", "types", String.join(", ", VALID_TYPES));
            return true;
        }
        
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                Lang.send(sender, "top_invalid_page");
                return true;
            }
        }
        
        int finalPage = page;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TopEntry> topList = getTopList(type, finalPage);
            
            String typeName = plugin.getConfigManager().getLangConfig()
                .getString("messages.top_types." + type, type);
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Lang.send(sender, "top_header", "type", typeName, "page", String.valueOf(finalPage));
                
                if (topList.isEmpty()) {
                    Lang.send(sender, "top_no_data");
                    return;
                }
                
                int position = (finalPage - 1) * 10 + 1;
                for (TopEntry entry : topList) {
                    String message = Lang.get("top_format")
                        .replace("%position%", String.valueOf(position++))
                        .replace("%player%", entry.playerName)
                        .replace("%value%", formatValue(type, entry.value));
                    if (!message.isEmpty()) {
                        sender.sendMessage(Lang.colorize(message));
                    }
                }
            });
        });
        
        return true;
    }
    
    private List<TopEntry> getTopList(String type, int page) {
        List<TopEntry> list = new ArrayList<>();
        int offset = (page - 1) * 10;
        
        String sql = switch (type) {
            case "warnings" -> """
                SELECT player_name, COUNT(*) as value 
                FROM cpa_warnings 
                WHERE active = 1 
                GROUP BY player_uuid, player_name 
                ORDER BY value DESC 
                LIMIT 10 OFFSET ?
                """;
            case "reports" -> """
                SELECT target_name as player_name, COUNT(*) as value 
                FROM cpa_reports 
                GROUP BY target_uuid, target_name 
                ORDER BY value DESC 
                LIMIT 10 OFFSET ?
                """;
            default -> """
                SELECT player_name, 0 as value 
                FROM cpa_player_sessions 
                GROUP BY player_name 
                ORDER BY player_name
                LIMIT 10 OFFSET ?
                """;
        };
        
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, offset);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String playerName = rs.getString("player_name");
                    int value = rs.getInt("value");
                    list.add(new TopEntry(playerName, value));
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get top list: " + e.getMessage());
        }
        
        return list;
    }
    
    private String formatValue(String type, int value) {
        return switch (type) {
            case "playtime" -> formatPlaytime(value);
            default -> String.valueOf(value);
        };
    }
    
    private String formatPlaytime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return VALID_TYPES.stream()
                .filter(t -> t.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2) {
            return List.of("1", "2", "3", "4", "5");
        }
        return List.of();
    }
    
    private record TopEntry(String playerName, int value) {}
}