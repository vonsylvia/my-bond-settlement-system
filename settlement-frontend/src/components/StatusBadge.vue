<template>
  <span :class="['status-badge', statusClass]">{{ status }}</span>
</template>

<script>
export default {
  name: 'StatusBadge',
  props: {
    status: {
      type: String,
      required: true
    }
  },
  computed: {
    statusClass() {
      switch (this.status) {
        case 'PENDING': return 'pending'
        case 'SENT': return 'sent'
        case 'MATCHED': return 'matched'
        case 'FAILED': return 'failed'
        case 'CANCELLED': return 'cancelled'
        default: return 'pending'
      }
    }
  }
}
</script>

<style scoped>
.status-badge {
  display: inline-block;
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

.status-badge.cancelled {
  background: #f5f5f5;
  color: #757575;
}
</style>
