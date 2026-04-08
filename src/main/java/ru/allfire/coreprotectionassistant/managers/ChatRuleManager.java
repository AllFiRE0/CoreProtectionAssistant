package ru.allfire.coreprotectionassistant.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.enums.PunishmentType;
import ru.allfire.coreprotectionassistant.enums.RuleAction;
import ru.allfire.coreprotectionassistant.models.ChatRule;
import ru.allfire.coreprotectionassistant.utils.Color;
import ru.allfire.coreprotectionassistant.utils.CommandExecutor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRuleManager {
    
    private final CoreProtectionAssistant plugin;
    private final List<ChatRule> rules = new ArrayList<>();
    private final Map<UUID, Long> lastViolationTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violationCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastApologyTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dailyApologies = new ConcurrentHashMap<>();
    
    private boolean enabled;
    private String mode;
    private String replacement;
    private long globalCooldownTicks;
    private long apologyCooldownTicks;
    private int maxApologiesPerDay;
    private boolean recidivismEnabled;
    private long recidivismWindowTicks;
    private List<String> excludedPermissions;
    private List<String> excludedGroups;
    
    public ChatRuleManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
        loadRules();
    }
    
    public void loadRules() {
        rules.clear();
        var config = plugin.getConfigManager().getChatRulesConfig();
        
        enabled = config.getBoolean("global.enabled", true);
        mode = config.getString("global.mode", "strict");
        replacement = config.getString("global.replacement", "***");
        globalCooldownTicks = config.getLong("global.cooldown_ticks", 60);
        
        apologyCooldownTicks = config.getLong("apology.global_cooldown_ticks", 48000);
        maxApologiesPerDay = config.getInt("apology.max_warnings_clear_per_day", 5);
        
        recidivismEnabled = config.getBoolean("punishment.recidivism.enabled", true);
        recidivismWindowTicks = config.getLong("punishment.recidivism.time_window_ticks", 72000);
        
        excludedPermissions = config.getStringList("exclusions.permissions");
        excludedGroups = config.getStringList("exclusions.groups");
        
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection == null) return;
        
        for (String ruleName : rulesSection.getKeys(false)) {
            ConfigurationSection ruleSection = rulesSection.getConfigurationSection(ruleName);
            if (ruleSection == null) continue;
            
            boolean ruleEnabled = ruleSection.getBoolean("enabled", true);
            if (!ruleEnabled) continue;
            
            int priority = ruleSection.getInt("priority", 50);
            String regex = ruleSection.getString("regex", "");
            
            String actionStr = ruleSection.getString("action", "notify").toUpperCase();
            RuleAction action = RuleAction.valueOf(actionStr);
            
            String punishmentStr = ruleSection.getString("punishment", "none").toUpperCase();
            PunishmentType punishment = PunishmentType.valueOf(punishmentStr);
            
            long durationTicks = ruleSection.getLong("duration_ticks", 0);
            
            String additionalStr = ruleSection.getString("additional_punishment", "none").toUpperCase();
            PunishmentType additionalPunishment = PunishmentType.valueOf(additionalStr);
            
            long additionalDurationTicks = ruleSection.getLong("additional_duration_ticks", 0);
            int warningsClear = ruleSection.getInt("warnings_clear", 0);
            boolean canBeApologized = ruleSection.getBoolean("can_be_apologized", true);
            boolean requireTarget = ruleSection.getBoolean("require_target", false);
            List<String> commands = ruleSection.getStringList("commands");
            
            ChatRule rule = new ChatRule(
                ruleName, true, priority, regex, action,
                punishment, durationTicks, additionalPunishment, additionalDurationTicks,
                warningsClear, canBeApologized, requireTarget, commands
            );
            
            rules.add(rule);
        }
        
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        plugin.getLogger().info("Loaded " + rules.size() + " chat rules");
    }
    
    public boolean isExempt(Player player) {
        for (String perm : excludedPermissions) {
            if (player.hasPermission(perm)) return true;
        }
        return false;
    }
    
    public ChatRuleProcessResult processChat(Player player, String message) {
        if (!enabled || isExempt(player)) {
            return new ChatRuleProcessResult(false, false, null);
        }
        
        for (ChatRule rule : rules) {
            if (rule.matches(message)) {
                return processRule(player, message, rule);
            }
        }
        
        return new ChatRuleProcessResult(false, false, null);
    }
    
    private ChatRuleProcessResult processRule(Player player, String message, ChatRule rule) {
        boolean shouldCancel = false;
        String processedMessage = message;
        
        switch (rule.getAction()) {
            case PUNISH -> {
                if (canPunish(player)) {
                    applyPunishment(player, rule);
                    recordViolation(player);
                    shouldCancel = true;
                }
            }
            case APOLOGY -> {
                if (canApologize(player)) {
                    applyApology(player, rule);
                    recordApology(player);
                }
            }
            case NOTIFY -> {
                executeCommands(player, null, rule);
            }
            case COMMAND -> {
                executeCommands(player, null, rule);
            }
        }
        
        if (shouldCancel && mode.equals("replace")) {
            processedMessage = replacement;
        }
        
        return new ChatRuleProcessResult(shouldCancel, shouldCancel, processedMessage);
    }
    
    private boolean canPunish(Player player) {
        Long lastTime = lastViolationTime.get(player.getUniqueId());
        if (lastTime == null) return true;
        long ticksPassed = (System.currentTimeMillis() - lastTime) / 50;
        return ticksPassed >= globalCooldownTicks;
    }
    
    private boolean canApologize(Player player) {
        Long lastTime = lastApologyTime.get(player.getUniqueId());
        if (lastTime != null) {
            long ticksPassed = (System.currentTimeMillis() - lastTime) / 50;
            if (ticksPassed < apologyCooldownTicks) return false;
        }
        int todayApologies = dailyApologies.getOrDefault(player.getUniqueId(), 0);
        return todayApologies < maxApologiesPerDay;
    }
    
    private void applyPunishment(Player player, ChatRule rule) {
        int recidivismLevel = getRecidivismLevel(player);
        
        executeCommands(player, null, rule);
        
        if (recidivismEnabled && recidivismLevel > 1) {
            var recidivismConfig = plugin.getConfigManager().getChatRulesConfig()
                .getConfigurationSection("punishment.recidivism.levels." + recidivismLevel);
            
            if (recidivismConfig != null) {
                for (String cmd : recidivismConfig.getStringList("commands")) {
                    CommandExecutor.execute(plugin, player, null, cmd);
                }
            }
        }
        
        plugin.getDatabaseManager().logViolation(player, rule.getName(), 
            rule.getPunishment().name(), System.currentTimeMillis());
    }
    
    private void applyApology(Player player, ChatRule rule) {
        String target = null;
        
        executeCommands(player, target, rule);
        
        if (rule.getWarningsClear() > 0) {
            plugin.getWarnManager().clearWarnings(player.getUniqueId(), 
                rule.getWarningsClear(), "Apology: " + rule.getName());
        }
        
        // Сохраняем извинение в БД
        plugin.getDatabaseManager().logApology(player, rule.getName(), rule.getWarningsClear());
    }
    
    private int getRecidivismLevel(Player player) {
        if (!recidivismEnabled) return 1;
        violationCount.put(player.getUniqueId(), 
            violationCount.getOrDefault(player.getUniqueId(), 0) + 1);
        return violationCount.get(player.getUniqueId());
    }
    
    private void recordViolation(Player player) {
        lastViolationTime.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            Integer count = violationCount.get(player.getUniqueId());
            if (count != null && count > 0) {
                violationCount.put(player.getUniqueId(), count - 1);
            }
        }, recidivismWindowTicks);
    }
    
    private void recordApology(Player player) {
        lastApologyTime.put(player.getUniqueId(), System.currentTimeMillis());
        dailyApologies.merge(player.getUniqueId(), 1, Integer::sum);
    }
    
    private void executeCommands(Player player, String target, ChatRule rule) {
        for (String cmd : rule.getCommands()) {
            CommandExecutor.execute(plugin, player, target, cmd);
        }
    }
    
    public void resetDailyApologies() {
        dailyApologies.clear();
    }
    
    public record ChatRuleProcessResult(boolean matched, boolean cancelled, String processedMessage) {}
}
