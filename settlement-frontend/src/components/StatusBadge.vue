<template>
  <span :class="['status-badge', statusClass]">
    {{ status }}
    <span v-if="isFinal" class="finality-mark" title="Settlement finalized — immutable">F</span>
  </span>
</template>

<script>
export default {
  name: 'StatusBadge',
  props: {
    status: {
      type: String,
      required: true
    },
    isFinal: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    statusClass() {
      switch (this.status) {
        case 'PENDING': return 'pending'
        case 'SUBMITTING': return 'submitting'
        case 'SENT': return 'sent'
        case 'MATCHED': return 'matched'
        case 'FAILED': return 'failed'
        case 'RETRYING': return 'retrying'
        case 'CANCELLED': return 'cancelled'
        case 'DVP_LOCKED': return 'dvp-locked'
        default: return 'pending'
      }
    }
  }
}
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.25rem 0.6rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.status-badge.pending {
  background: #fff3e0;
  color: #e65100;
}

.status-badge.submitting {
  background: #e8eaf6;
  color: #283593;
}

.status-badge.sent {
  background: #e3f2fd;
  color: #1565c0;
}

.status-badge.matched {
  background: #e8f5e9;
  color: #2e7d32;
}

.status-badge.failed {
  background: #fbe9e7;
  color: #c62828;
}

.status-badge.retrying {
  background: #fff8e1;
  color: #f57f17;
}

.status-badge.cancelled {
  background: #f5f5f5;
  color: #757575;
}

.status-badge.dvp-locked {
  background: #ede7f6;
  color: #4527a0;
}

.finality-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #2e7d32;
  color: #fff;
  font-size: 0.55rem;
  font-weight: 800;
}
</style>
