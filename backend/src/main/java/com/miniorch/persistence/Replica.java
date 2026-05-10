package com.miniorch.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "replicas",
        uniqueConstraints = @UniqueConstraint(name = "uk_replica_deployment_index", columnNames = {"deployment_id", "replica_index"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Replica {

    public enum Status {
        PENDING,
        RUNNING,
        EXITED,
        FAILED,
        REMOVED,
        CRASHLOOP_BACKOFF
    }

    public enum ProbeResult {
        NOT_PROBED,
        PASSING,
        FAILING
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(name = "replica_index", nullable = false)
    private int replicaIndex;

    @Column(name = "container_id", length = 64)
    private String containerId;

    @Column(name = "container_name", nullable = false, length = 80)
    private String containerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "restart_count", columnDefinition = "integer not null default 0")
    @Builder.Default
    private int restartCount = 0;

    @Column(name = "last_restart_at")
    private Instant lastRestartAt;

    @Column(name = "last_inspected_at")
    private Instant lastInspectedAt;

    @Column(name = "last_probe_at")
    private Instant lastProbeAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_probe_result", length = 16)
    @Builder.Default
    private ProbeResult lastProbeResult = ProbeResult.NOT_PROBED;

    @Column(name = "consecutive_failures", columnDefinition = "integer not null default 0")
    @Builder.Default
    private int consecutiveFailures = 0;

    @Column(name = "probe_details", length = 1024)
    private String probeDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_window", columnDefinition = "jsonb")
    @Builder.Default
    private List<Instant> failureWindow = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (lastProbeResult == null) {
            lastProbeResult = ProbeResult.NOT_PROBED;
        }
        if (failureWindow == null) {
            failureWindow = new ArrayList<>();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
