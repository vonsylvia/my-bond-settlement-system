<template>
  <div class="list-container">
    <div class="list-card">
      <div class="list-header">
        <div>
          <h2>Settlement Monitor <span class="phase-tag exec">Execution</span></h2>
          <p class="header-subtitle">Track settlement instructions through the SWIFT network lifecycle (PENDING → SENT → MATCHED).
            Instructions here represent actual settlement execution — securities and cash movement via DVP or FOP.</p>
        </div>
        <button class="btn-refresh" @click="loadData">Refresh</button>
      </div>

      <div v-if="loading" class="loading">Loading...</div>

      <table v-else class="data-table">
        <thead>
          <tr>
            <th>Trade Ref</th>
            <th>ISIN</th>
            <th>Direction</th>
            <th>Quantity</th>
            <th>CCY</th>
            <th>Counterparty</th>
            <th>Settlement Date</th>
            <th>Status</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in instructions" :key="item.tradeRef">
            <td class="mono">{{ item.tradeRef }}</td>
            <td class="mono">{{ item.isin }}</td>
            <td>
              <span :class="['direction-badge', item.direction.toLowerCase()]">
                {{ item.direction }}
              </span>
            </td>
            <td class="number">{{ formatNumber(item.quantity) }}</td>
            <td>
              <span class="currency-badge">{{ item.currency || 'HKD' }}</span>
            </td>
            <td>{{ item.counterparty }}</td>
            <td>{{ formatSettlementDate(item.settlementDate) }}</td>
            <td>
              <StatusBadge :status="item.status" :is-final="item.isFinal" />
            </td>
            <td>{{ formatDate(item.createdAt) }}</td>
            <td class="actions-cell">
              <router-link
                :to="{ name: 'Messages', params: { tradeRef: item.tradeRef } }"
                class="btn-messages"
              >
                Messages
              </router-link>
              <button
                v-if="item.status === 'FAILED'"
                class="btn-retry"
                :disabled="retryingRef === item.tradeRef"
                @click="retryInstruction(item.tradeRef)"
              >
                {{ retryingRef === item.tradeRef ? 'Retrying...' : 'Retry' }}
              </button>
            </td>
          </tr>
          <tr v-if="instructions.length === 0">
            <td colspan="10" class="empty">No settlement instructions found</td>
          </tr>
        </tbody>
      </table>

      <div v-if="totalPages > 1" class="pagination">
        <button :disabled="page === 0" @click="page--; loadData()">Previous</button>
        <span>Page {{ page + 1 }} of {{ totalPages }}</span>
        <button :disabled="page >= totalPages - 1" @click="page++; loadData()">Next</button>
      </div>
    </div>
  </div>
</template>

<script>
import { listSettlements, retrySettlement } from '../api/settlement.js'
import StatusBadge from '../components/StatusBadge.vue'

export default {
  name: 'SettlementList',
  components: { StatusBadge },
  data() {
    return {
      instructions: [],
      loading: false,
      retryingRef: null,
      page: 0,
      size: 20,
      totalPages: 0
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const response = await listSettlements(this.page, this.size)
        this.instructions = response.data.content
        this.totalPages = response.data.totalPages
      } catch (error) {
        console.error('Failed to load settlements', error)
      } finally {
        this.loading = false
      }
    },
    async retryInstruction(tradeRef) {
      this.retryingRef = tradeRef
      try {
        await retrySettlement(tradeRef)
        await this.loadData()
      } catch (error) {
        console.error('Retry failed', error)
        alert('Retry failed: ' + (error.response?.data?.message || error.message))
      } finally {
        this.retryingRef = null
      }
    },
    formatNumber(val) {
      return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2 })
    },
    formatSettlementDate(val) {
      if (!val) return ''
      if (Array.isArray(val)) {
        const [y, m, d] = val
        return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
      }
      return val
    },
    formatDate(val) {
      if (!val) return ''
      let date
      if (Array.isArray(val)) {
        const [y, m, d, h = 0, min = 0, s = 0] = val
        date = new Date(y, m - 1, d, h, min, s)
      } else {
        date = new Date(val)
      }
      if (isNaN(date.getTime())) return ''
      return date.toLocaleDateString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
      })
    }
  }
}
</script>

<style scoped>
.list-container {
  width: 100%;
}

.list-card {
  background: #fff;
  border-radius: 12px;
  padding: 2rem;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.list-header h2 {
  color: #1a237e;
  font-size: 1.4rem;
}

.btn-refresh {
  padding: 0.5rem 1rem;
  background: #e8eaf6;
  color: #3f51b5;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-refresh:hover {
  background: #c5cae9;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.data-table th {
  background: #f5f5f5;
  padding: 0.75rem;
  text-align: left;
  font-weight: 600;
  color: #555;
  border-bottom: 2px solid #e0e0e0;
}

.data-table td {
  padding: 0.75rem;
  border-bottom: 1px solid #f0f0f0;
}

.data-table tbody tr:hover {
  background: #fafafa;
}

.mono {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 0.8rem;
}

.number {
  text-align: right;
  font-family: 'SF Mono', monospace;
}

.direction-badge {
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}

.direction-badge.buy {
  background: #e3f2fd;
  color: #1565c0;
}

.direction-badge.sell {
  background: #fce4ec;
  color: #c62828;
}

.currency-badge {
  display: inline-block;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  font-size: 0.7rem;
  font-weight: 700;
  background: #f3e5f5;
  color: #6a1b9a;
  letter-spacing: 0.3px;
}

.empty {
  text-align: center;
  color: #999;
  padding: 2rem !important;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 1.5rem;
}

.pagination button {
  padding: 0.4rem 0.8rem;
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-retry {
  padding: 0.3rem 0.7rem;
  background: #ff5722;
  color: #fff;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 600;
  transition: background 0.2s;
}

.btn-retry:hover:not(:disabled) {
  background: #e64a19;
}

.btn-retry:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.no-action {
  color: #bbb;
}

.actions-cell {
  display: flex;
  gap: 0.4rem;
  align-items: center;
}

.btn-messages {
  padding: 0.3rem 0.7rem;
  background: #e8eaf6;
  color: #3f51b5;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 600;
  text-decoration: none;
  transition: background 0.2s;
}

.btn-messages:hover {
  background: #c5cae9;
}

.header-subtitle {
  font-size: 0.82rem;
  color: #666;
  margin-top: 0.3rem;
  line-height: 1.4;
}

.phase-tag {
  font-size: 0.6rem;
  padding: 0.15rem 0.5rem;
  border-radius: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 700;
  vertical-align: middle;
  margin-left: 0.5rem;
}
.phase-tag.pre { background: #e8eaf6; color: #3949ab; }
.phase-tag.exec { background: #e0f2f1; color: #00695c; }
</style>
