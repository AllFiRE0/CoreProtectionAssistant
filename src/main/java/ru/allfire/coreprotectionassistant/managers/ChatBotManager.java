package ru.allfire.coreprotectionassistant.managers;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.CommandExecutor;
import ru.allfire.coreprotectionassistant.utils.ConditionParser;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatBotManager implements Listener {
    
    private final CoreProtectionAssistant plugin;
    private final List<BotRule> rules = new ArrayList<>();
    private final Map<UUID, Long> lastTriggerTime = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> ruleCooldowns = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    private boolean enabled;
    private boolean debug;
    private String globalPermission;
    private long globalCooldownTicks;
    private boolean logTriggers;
    private int maxMessageLength;
    private List<String> excludedPermissions;
    private List<String> excludedPlayers;
    
    public ChatBotManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        loadConfig();
        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }
    
    public void loadConfig() {
        rules.clear();
        
        File configFile = new File(plugin.getDataFolder(), "chatbot.yml");
        if (!configFile.exists()) {
            plugin.saveResource("chatbot.yml", false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        enabled = config.getBoolean("enabled", false);
        debug = config.getBoolean("debug", false);
        globalPermission = config.getString("permission_usage", "");
        globalCooldownTicks = config.getLong("global_cooldown_ticks", 120);
        logTriggers = config.getBoolean("log_triggers", false);
        maxMessageLength = config.getInt("max_message_length", 256);
        excludedPermissions = config.getStringList("exclusions.permissions");
        excludedPlayers = config.getStringList("exclusions.players");
        
        if (debug) {
            plugin.getLogger().info("§e[ChatBot] Debug mode ENABLED");
        }
        
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            if (debug) {
                plugin.getLogger().warning("§c[ChatBot] No rules section found in chatbot.yml");
            }
            return;
        }
        
        for (String ruleName : rulesSection.getKeys(false)) {
            ConfigurationSection ruleSection = rulesSection.getConfigurationSection(ruleName);
            if (ruleSection == null) continue;
            
            boolean ruleEnabled = ruleSection.getBoolean("enabled", true);
            if (!ruleEnabled) continue;
            
            int priority = ruleSection.getInt("priority", 50);
            String permission = ruleSection.getString("permission", "");
            String conditions = ruleSection.getString("conditions", "");
            String symbol = ruleSection.getString("symbol", "");
            long cooldownTicks = ruleSection.getLong("cooldown_ticks", 0);
            int chance = ruleSection.getInt("chance", 100);
            long delayTicks = ruleSection.getLong("delay_ticks", 0);
            String regex = ruleSection.getString("regex", ".*");
            List<String> answerCmds = ruleSection.getStringList("answer_cmds");
            List<String> answerCmdsRandom = ruleSection.getStringList("answer_cmds_random");
            
            BotRule rule = new BotRule(
                ruleName, priority, permission, conditions, symbol,
                cooldownTicks, chance, delayTicks,
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                answerCmds, answerCmdsRandom
            );
            
            rules.add(rule);
        }
        
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));
        
        if (debug) {
            plugin.getLogger().info("§a[ChatBot] Loaded " + rules.size() + " rules");
            for (BotRule rule : rules) {
                plugin.getLogger().info("§a[ChatBot]   - " + rule.name + " (symbol: '" + rule.symbol + "', regex: " + rule.pattern + ")");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        
        if (isExcluded(player)) {
            if (debug) plugin.getLogger().info("§c[ChatBot] Player " + player.getName() + " is excluded");
            return;
        }
        
        if (!globalPermission.isEmpty() && !player.hasPermission(globalPermission)) {
            if (debug) plugin.getLogger().info("§c[ChatBot] Player " + player.getName() + " lacks permission: " + globalPermission);
            return;
        }
        
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        if (debug) {
            plugin.getLogger().info("§e[ChatBot] Received from " + player.getName() + ": '" + message + "'");
        }
        
        if (message.length() > maxMessageLength) {
            if (debug) plugin.getLogger().info("§c[ChatBot] Message too long: " + message.length() + " > " + maxMessageLength);
            return;
        }
        
        Long lastGlobal = lastTriggerTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        
        if (lastGlobal != null) {
            long ticksPassed = (now - lastGlobal) / 50;
            if (ticksPassed < globalCooldownTicks) {
                if (debug) plugin.getLogger().info("§c[ChatBot] Global cooldown active: " + ticksPassed + " < " + globalCooldownTicks);
                return;
            }
        }
        
        for (BotRule rule : rules) {
            if (debug) plugin.getLogger().info("§b[ChatBot] Checking rule: " + rule.name);
            
            if (!rule.permission.isEmpty() && !player.hasPermission(rule.permission)) {
                if (debug) plugin.getLogger().info("§c[ChatBot]   -> No permission: " + rule.permission);
                continue;
            }
            
            if (!rule.symbol.isEmpty() && !message.startsWith(rule.symbol)) {
                if (debug) plugin.getLogger().info("§c[ChatBot]   -> Symbol mismatch: expected '" + rule.symbol + "'");
                continue;
            }
            
            String checkMessage = rule.symbol.isEmpty() ? message : message.substring(rule.symbol.length());
            
            if (!rule.pattern.matcher(checkMessage).find()) {
                if (debug) plugin.getLogger().info("§c[ChatBot]   -> Regex mismatch");
                continue;
            }
            
            if (!rule.conditions.isEmpty()) {
                String processedCondition = rule.conditions.replace("{player}", player.getName());
                if (!ConditionParser.evaluate(plugin, player, processedCondition)) {
                    if (debug) plugin.getLogger().info("§c[ChatBot]   -> Condition failed: " + rule.conditions);
                    continue;
                }
            }
            
            Map<String, Long> playerCooldowns = ruleCooldowns.computeIfAbsent(
                player.getUniqueId(), k -> new ConcurrentHashMap<>()
            );
            
            Long lastRule = playerCooldowns.get(rule.name);
            if (lastRule != null && rule.cooldownTicks > 0) {
                long ticksPassed = (now - lastRule) / 50;
                if (ticksPassed < rule.cooldownTicks) {
                    if (debug) plugin.getLogger().info("§c[ChatBot]   -> Rule cooldown active");
                    continue;
                }
            }
            
            if (rule.chance < 100 && random.nextInt(100) >= rule.chance) {
                if (debug) plugin.getLogger().info("§c[ChatBot]   -> Chance failed (" + rule.chance + "%)");
                continue;
            }
            
            if (debug) plugin.getLogger().info("§a[ChatBot]   -> RULE MATCHED! Executing...");
            
            if (logTriggers) {
                plugin.getLogger().info("[ChatBot] Player " + player.getName() + 
                    " triggered rule '" + rule.name + "'");
            }
            
            final List<String> cmdsToExecute;
            if (!rule.answerCmdsRandom.isEmpty() && random.nextBoolean()) {
                cmdsToExecute = new ArrayList<>(rule.answerCmdsRandom);
            } else {
                cmdsToExecute = new ArrayList<>(rule.answerCmds);
            }
            
            if (rule.delayTicks > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    executeCommands(player, message, cmdsToExecute);
                }, rule.delayTicks);
            } else {
                executeCommands(player, message, cmdsToExecute);
            }
            
            lastTriggerTime.put(player.getUniqueId(), now);
            playerCooldowns.put(rule.name, now);
            
            break;
        }
    }
    
    private void executeCommands(Player player, String message, List<String> commands) {
        for (String cmd : commands) {
            String processed = cmd
                .replace("%player_name%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString())
                .replace("%player_world%", player.getWorld().getName())
                .replace("%message%", message);
            
            CommandExecutor.execute(plugin, player, null, processed);
        }
    }
    
    private boolean isExcluded(Player player) {
        for (String perm : excludedPermissions) {
            if (player.hasPermission(perm)) return true;
        }
        return excludedPlayers.contains(player.getName());
    }
    
    public void reload() {
        loadConfig();
        if (!debug) {
            // Тишина при reload если debug выключен
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public int getRulesCount() {
        return rules.size();
    }
    
    private record BotRule(
        String name,
        int priority,
        String permission,
        String conditions,
        String symbol,
        long cooldownTicks,
        int chance,
        long delayTicks,
        Pattern pattern,
        List<String> answerCmds,
        List<String> answerCmdsRandom
    ) {}
}
