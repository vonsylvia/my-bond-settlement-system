package com.settlement.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Records the result of each reconciliation run (daily close or incremental).
 * The latest snapshot's timestamp is used as the baseline for incremental
 * reconciliation — only positions with movements after that point need checking.
 */
@Entity
@Table(name = "RECONCILIATION_SNAPSHOT")
public class ReconciliationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "SNAPSHOT_TYPE", nullable = false, length = 20)
    private SnapshotType snapshotType;

    @Column(name = "POSITIONS_CHECKED", nullable = false)
    private int positionsChecked;

    @Column(name = "DISCREPANCY_COUNT", nullable = false)
    private int discrepancyCount;

    @Column(name = "IS_CONSISTENT", nullable = false)
    private boolean consistent;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public ReconciliationSnapshot() {
    }

    public ReconciliationSnapshot(SnapshotType snapshotType, int positionsChecked,
                                  int discrepancyCount) {
        this.snapshotType = snapshotType;
        this.positionsChecked = positionsChecked;
        this.discrepancyCount = discrepancyCount;
        this.consistent = (discrepancyCount == 0);
    }

    public Long getId() { return id; }
    public SnapshotType getSnapshotType() { return snapshotType; }
    public int getPositionsChecked() { return positionsChecked; }
    public int getDiscrepancyCount() { return discrepancyCount; }
    public boolean isConsistent() { return consistent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
