package ru.allfire.coreprotectionassistant.models;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CommandLog {
    private final UUID playerUuid;
    private final String playerName;
    private final String command;
    private final String[] args;
    private final String fullCommand;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final long timestamp;
    private final boolean isStaff;
}
