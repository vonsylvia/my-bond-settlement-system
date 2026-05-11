package com.settlement.dto;

import com.settlement.entity.SnapshotType;

import java.time.LocalDateTime;
import java.util.List;

public class ReconciliationResult {

    private LocalDateTime reconciledAt;
    private SnapshotType type;
    private int totalPositions;
    private int discrepancyCount;
    private boolean consistent;
    private List<PositionDiscrepancy> discrepancies;

    public ReconciliationResult() {
    }

    public ReconciliationResult(int totalPositions, List<PositionDiscrepancy> discrepancies) {
        this(null, totalPositions, discrepancies);
    }

    public ReconciliationResult(SnapshotType type, int totalPositions,
                                List<PositionDiscrepancy> discrepancies) {
        this.reconciledAt = LocalDateTime.now();
        this.type = type;
        this.totalPositions = totalPositions;
        this.discrepancies = discrepancies;
        this.discrepancyCount = discrepancies.size();
        this.consistent = discrepancies.isEmpty();
    }

    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }

    public SnapshotType getType() { return type; }
    public void setType(SnapshotType type) { this.type = type; }

    public int getTotalPositions() { return totalPositions; }
    public void setTotalPositions(int totalPositions) { this.totalPositions = totalPositions; }

    public int getDiscrepancyCount() { return discrepancyCount; }
    public void setDiscrepancyCount(int discrepancyCount) { this.discrepancyCount = discrepancyCount; }

    public boolean isConsistent() { return consistent; }
    public void setConsistent(boolean consistent) { this.consistent = consistent; }

    public List<PositionDiscrepancy> getDiscrepancies() { return discrepancies; }
    public void setDiscrepancies(List<PositionDiscrepancy> discrepancies) { this.discrepancies = discrepancies; }
}
