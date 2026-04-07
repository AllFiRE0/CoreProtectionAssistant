package ru.allfire.coreprotectionassistant.models;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StaffWarning {
    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final UUID staffUuid;
    private final String staffName;
    private final String reason;
    private final boolean active;
    private final long createdAt;
    private final long expiresAt;
    private final long clearedAt;
    private final String clearedBy;
}
