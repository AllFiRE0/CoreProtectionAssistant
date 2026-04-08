package ru.allfire.coreprotectionassistant.managers;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.utils.CommandExecutor;
import ru.allfire.coreprotectionassistant.utils.ConditionParser;

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
            plugin.getLogger().info("§a[ChatBot] Registered listener!");
        } else {
            plugin.getLogger().warning("§c[ChatBot] NOT enabled - listener not registered!");
        }
    }
    
    public void loadConfig() {
        rules.clear();
        
        var config = plugin.getConfigManager().getChatBotConfig();
        if (config == null) {
            plugin.getLogger().warning("§c[ChatBot] Config is NULL!");
            return;
        }
        
        enabled = config.getBoolean("enabled", false);
        globalPermission = config.getString("permission_usage", "");
        globalCooldownTicks = config.getLong("global_cooldown_ticks", 120);
        logTriggers = config.getBoolean("log_triggers", true);
        maxMessageLength = config.getInt("max_message_length", 256);
        
        excludedPermissions = config.getStringList("exclusions.permissions");
        excludedPlayers = config.getStringList("exclusions.players");
        
        plugin.getLogger().info("§a[ChatBot] enabled = " + enabled);
        plugin.getLogger().info("§a[ChatBot] globalPermission = '" + globalPermission + "'");
        plugin.getLogger().info("§a[ChatBot] excludedPermissions = " + excludedPermissions);
        
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) {
            plugin.getLogger().warning("§c[ChatBot] Rules section is NULL!");
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
            plugin.getLogger().info("§a[ChatBot] Loaded rule: " + ruleName + " (regex: " + regex + ")");
        }
        
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));
        plugin.getLogger().info("§a[ChatBot] Total " + rules.size() + " rules loaded");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        // DEBUG: Всегда логируем полученное сообщение
        plugin.getLogger().info("§e[ChatBot DEBUG] Received message from " + player.getName() + ": '" + message + "'");
        
        if (!enabled) {
            plugin.getLogger().warning("§c[ChatBot DEBUG] Bot is DISABLED");
            return;
        }
        
        // Проверка исключений
        if (isExcluded(player)) {
            plugin.getLogger().info("§c[ChatBot DEBUG] Player " + player.getName() + " is EXCLUDED");
            return;
        }
        
        // Проверка глобального права
        if (!globalPermission.isEmpty() && !player.hasPermission(globalPermission)) {
            plugin.getLogger().info("§c[ChatBot DEBUG] Player " + player.getName() + " lacks global permission: " + globalPermission);
            return;
        }
        
        // Проверка длины
        if (message.length() > maxMessageLength) {
            plugin.getLogger().info("§c[ChatBot DEBUG] Message too long: " + message.length() + " > " + maxMessageLength);
            return;
        }
        
        plugin.getLogger().info("§a[ChatBot DEBUG] Message passed basic checks! Checking " + rules.size() + " rules...");
        
        Long lastGlobal = lastTriggerTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        
        if (lastGlobal != null) {
            long ticksPassed = (now - lastGlobal) / 50;
            if (ticksPassed < globalCooldownTicks) {
                plugin.getLogger().info("§c[ChatBot DEBUG] Global cooldown active: " + ticksPassed + " < " + globalCooldownTicks);
                return;
            }
        }
        
        for (BotRule rule : rules) {
            plugin.getLogger().info("§b[ChatBot DEBUG] Checking rule: " + rule.name);
            
            if (!rule.permission.isEmpty() && !player.hasPermission(rule.permission)) {
                plugin.getLogger().info("§c[ChatBot DEBUG]   -> No permission: " + rule.permission);
                continue;
            }
            
            if (!rule.symbol.isEmpty() && !message.startsWith(rule.symbol)) {
                plugin.getLogger().info("§c[ChatBot DEBUG]   -> Symbol mismatch: expected '" + rule.symbol + "', got '" + message + "'");
                continue;
            }
            
            String checkMessage = rule.symbol.isEmpty() ? message : message.substring(rule.symbol.length());
            
            if (!rule.pattern.matcher(checkMessage).find()) {
                plugin.getLogger().info("§c[ChatBot DEBUG]   -> Regex mismatch: " + rule.pattern);
                continue;
            }
            
            plugin.getLogger().info("§a[ChatBot DEBUG]   -> RULE MATCHED! Executing...");
            
            // Выполняем команды
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
            
            Map<String, Long> playerCooldowns = ruleCooldowns.computeIfAbsent(
                player.getUniqueId(), k -> new ConcurrentHashMap<>()
            );
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
