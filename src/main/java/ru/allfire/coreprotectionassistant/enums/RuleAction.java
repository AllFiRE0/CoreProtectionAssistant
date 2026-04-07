package ru.allfire.coreprotectionassistant.enums;

public enum RuleAction {
    PUNISH,     // Выдать наказание
    APOLOGY,    // Снять предупреждения (извинение)
    NOTIFY,     // Только уведомление
    COMMAND     // Выполнить команды без блокировки чата
}
