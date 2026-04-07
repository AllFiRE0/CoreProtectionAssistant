package ru.allfire.coreprotectionassistant.models;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ChatViolation {
    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final String ruleName;
    private final String punishment;
    private final String message;
    private final long timestamp;
}
