package ru.allfire.coreprotectionassistant.models;

import lombok.Data;
import ru.allfire.coreprotectionassistant.enums.PunishmentType;
import ru.allfire.coreprotectionassistant.enums.RuleAction;

import java.util.List;
import java.util.regex.Pattern;

@Data
public class ChatRule {
    private final String name;
    private final boolean enabled;
    private final int priority;
    private final Pattern pattern;
    private final RuleAction action;
    private final PunishmentType punishment;
    private final long durationTicks;
    private final PunishmentType additionalPunishment;
    private final long additionalDurationTicks;
    private final int warningsClear;
    private final boolean canBeApologized;
    private final boolean requireTarget;
    private final List<String> commands;
    
    public ChatRule(String name, boolean enabled, int priority, String regex, 
                    RuleAction action, PunishmentType punishment, long durationTicks,
                    PunishmentType additionalPunishment, long additionalDurationTicks,
                    int warningsClear, boolean canBeApologized, boolean requireTarget,
                    List<String> commands) {
        this.name = name;
        this.enabled = enabled;
        this.priority = priority;
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        this.action = action;
        this.punishment = punishment;
        this.durationTicks = durationTicks;
        this.additionalPunishment = additionalPunishment;
        this.additionalDurationTicks = additionalDurationTicks;
        this.warningsClear = warningsClear;
        this.canBeApologized = canBeApologized;
        this.requireTarget = requireTarget;
        this.commands = commands;
    }
    
    public boolean matches(String text) {
        return pattern.matcher(text).find();
    }
}
