package ru.allfire.coreprotectionassistant.models;

import lombok.Builder;
import lombok.Data;
import ru.allfire.coreprotectionassistant.enums.ReportStatus;

import java.util.UUID;

@Data
@Builder
public class PlayerReport {
    private final long id;
    private final UUID reporterUuid;
    private final String reporterName;
    private final UUID targetUuid;
    private final String targetName;
    private final String category;
    private final String reason;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final ReportStatus status;
    private final long timestamp;
    private final long resolvedAt;
    private final String resolvedBy;
}
