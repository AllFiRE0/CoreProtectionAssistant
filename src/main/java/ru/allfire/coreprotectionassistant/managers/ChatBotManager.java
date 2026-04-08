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
    
    private boolean enabled;
    private String globalPermission;
    private long globalCooldownTicks;
    private boolean ignoreSelf;
    private boolean logTriggers;
    private int maxMessageLength;
    private List<String> excludedPermissions;
    private List<String> excludedPlayers;
    private List<String> excludedGroups;
    
    public ChatBotManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        loadConfig();
        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }
    
    public void loadConfig() {
        rules.clear();
        
        var config = plugin.getConfigManager().getChatBotConfig();
        if (config == null) return;
        
        enabled = config.getBoolean("enabled", false);
        globalPermission = config.getString("permission_usage", "");
        globalCooldownTicks = config.getLong("global_cooldown_ticks", 120);
        ignoreSelf = config.getBoolean("ignore_self", true);
        logTriggers = config.getBoolean("log_triggers", true);
        maxMessageLength = config.getInt("max_message_length", 256);
        
        excludedPermissions = config.getStringList("exclusions.permissions");
        excludedPlayers = config.getStringList("exclusions.players");
        excludedGroups = config.getStringList("exclusions.groups");
        
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) return;
        
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
            String regex = ruleSection.getString("regex", ".*");
            List<String> answerCmds = ruleSection.getStringList("answer_cmds");
            
            BotRule rule = new BotRule(
                ruleName, priority, permission, conditions, symbol,
                cooldownTicks, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                answerCmds
            );
            
            rules.add(rule);
        }
        
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));
        plugin.getLogger().info("Loaded " + rules.size() + " chatbot rules");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        
        // Проверка исключений
        if (isExcluded(player)) return;
        
        // Проверка глобального права
        if (!globalPermission.isEmpty() && !player.hasPermission(globalPermission)) return;
        
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        // Проверка длины сообщения
        if (message.length() > maxMessageLength) return;
        
        // Глобальный кулдаун
        Long lastGlobal = lastTriggerTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        
        if (lastGlobal != null) {
            long ticksPassed = (now - lastGlobal) / 50;
            if (ticksPassed < globalCooldownTicks) return;
        }
        
        // Проверяем правила
        for (BotRule rule : rules) {
            // Проверка права
            if (!rule.permission.isEmpty() && !player.hasPermission(rule.permission)) continue;
            
            // Проверка символа
            if (!rule.symbol.isEmpty() && !message.startsWith(rule.symbol)) continue;
            
            // Убираем символ для проверки regex
            String checkMessage = rule.symbol.isEmpty() ? message : message.substring(rule.symbol.length());
            
            // Проверка regex
            if (!rule.pattern.matcher(checkMessage).find()) continue;
            
            // Проверка условий
            if (!rule.conditions.isEmpty() && !ConditionParser.evaluate(plugin, player, rule.conditions)) continue;
            
            // Проверка кулдауна правила
            Map<String, Long> playerCooldowns = ruleCooldowns.computeIfAbsent(
                player.getUniqueId(), k -> new ConcurrentHashMap<>()
            );
            
            Long lastRule = playerCooldowns.get(rule.name);
            if (lastRule != null && rule.cooldownTicks > 0) {
                long ticksPassed = (now - lastRule) / 50;
                if (ticksPassed < rule.cooldownTicks) continue;
            }
            
            // Срабатывание правила!
            if (logTriggers) {
                plugin.getLogger().info("[ChatBot] Player " + player.getName() + 
                    " triggered rule '" + rule.name + "': " + message);
            }
            
            // Выполняем команды
            for (String cmd : rule.answerCmds) {
                CommandExecutor.execute(plugin, player, null, cmd);
            }
            
            // Обновляем кулдауны
            lastTriggerTime.put(player.getUniqueId(), now);
            playerCooldowns.put(rule.name, now);
            
            break; // Только одно правило за сообщение
        }
    }
    
    private boolean isExcluded(Player player) {
        for (String perm : excludedPermissions) {
            if (player.hasPermission(perm)) return true;
        }
        if (excludedPlayers.contains(player.getName())) return true;
        // TODO: проверка групп LuckPerms
        return false;
    }
    
    public void reload() {
        loadConfig();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    private record BotRule(
        String name,
        int priority,
        String permission,
        String conditions,
        String symbol,
        long cooldownTicks,
        Pattern pattern,
        List<String> answerCmds
    ) {}
}
