<template>
  <div class="holdings-container">
    <div class="holdings-card">
      <div class="holdings-header">
        <h2>Bond Holdings</h2>
        <div class="filter-group">
          <input v-model="accountFilter" type="text" placeholder="Filter by Account ID..."
                 @keyup.enter="loadData" />
          <button class="btn-refresh" @click="loadData">Search</button>
        </div>
      </div>

      <div v-if="loading" class="loading">Loading...</div>

      <table v-else class="data-table">
        <thead>
          <tr>
            <th>Account ID</th>
            <th>ISIN</th>
            <th>Quantity</th>
            <th>Last Updated</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="holding in holdings" :key="`${holding.accountId}-${holding.isin}`">
            <td class="mono">{{ holding.accountId }}</td>
            <td class="mono">{{ holding.isin }}</td>
            <td class="number">{{ formatNumber(holding.quantity) }}</td>
            <td>{{ formatDate(holding.updatedAt) }}</td>
          </tr>
          <tr v-if="holdings.length === 0">
            <td colspan="4" class="empty">No holdings found</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import { getHoldings } from '../api/settlement.js'

export default {
  name: 'HoldingsView',
  data() {
    return {
      holdings: [],
      loading: false,
      accountFilter: ''
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const response = await getHoldings(this.accountFilter || undefined)
        this.holdings = response.data
      } catch (error) {
        console.error('Failed to load holdings', error)
      } finally {
        this.loading = false
      }
    },
    formatNumber(val) {
      return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2 })
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
        month: 'short', day: 'numeric', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      })
    }
  }
}
</script>

<style scoped>
.holdings-container {
  width: 100%;
}

.holdings-card {
  background: #fff;
  border-radius: 12px;
  padding: 2rem;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.holdings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.holdings-header h2 {
  color: #1a237e;
  font-size: 1.4rem;
}

.filter-group {
  display: flex;
  gap: 0.5rem;
}

.filter-group input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.9rem;
  width: 200px;
}

.filter-group input:focus {
  outline: none;
  border-color: #3f51b5;
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
  font-size: 0.9rem;
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
  font-size: 0.85rem;
}

.number {
  text-align: right;
  font-family: 'SF Mono', monospace;
  font-weight: 600;
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
</style>
