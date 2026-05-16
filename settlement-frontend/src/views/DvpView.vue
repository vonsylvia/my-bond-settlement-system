<template>
  <div class="dvp-view">
    <div class="page-header">
      <h2>DVP Settlement</h2>
      <p class="subtitle">BIS Model 1 — Delivery vs Payment with CHATS integration</p>
    </div>

    <div class="tabs">
      <button :class="{ active: tab === 'operations' }" @click="tab = 'operations'">DVP Operations</button>
      <button :class="{ active: tab === 'accounts' }" @click="tab = 'accounts'; loadAccounts()">Cash Accounts</button>
    </div>

    <div v-if="tab === 'operations'" class="operations-section">
      <div class="dvp-card">
        <h3>DVP Lock / Complete / Rollback</h3>
        <p class="card-desc">Execute DVP operations on an existing settlement instruction (must be Against Payment type).</p>

        <div class="operation-form">
          <div class="form-group">
            <label>Trade Reference</label>
            <input v-model="tradeRef" placeholder="e.g. TR-20250101-001" />
          </div>
          <div class="btn-group">
            <button class="btn btn-lock" @click="dvpAction('lock')" :disabled="!tradeRef || loading">
              {{ loading === 'lock' ? 'Locking...' : 'Lock for DVP' }}
            </button>
            <button class="btn btn-complete" @click="dvpAction('complete')" :disabled="!tradeRef || loading">
              {{ loading === 'complete' ? 'Completing...' : 'Complete DVP' }}
            </button>
            <button class="btn btn-rollback" @click="dvpAction('rollback')" :disabled="!tradeRef || loading">
              {{ loading === 'rollback' ? 'Rolling back...' : 'Rollback' }}
            </button>
          </div>
        </div>

        <div v-if="operationResult" class="result-message" :class="operationResult.type">
          <strong>{{ operationResult.action }}:</strong> {{ operationResult.message }}
        </div>
      </div>

      <div class="dvp-flow">
        <h3>DVP Settlement Flow</h3>
        <div class="flow-diagram">
          <div class="flow-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <strong>Lock</strong>
              <span>Reserve cash via CHATS</span>
            </div>
          </div>
          <div class="flow-arrow">&rarr;</div>
          <div class="flow-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <strong>Securities Transfer</strong>
              <span>Confirm bond delivery</span>
            </div>
          </div>
          <div class="flow-arrow">&rarr;</div>
          <div class="flow-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <strong>Complete</strong>
              <span>Finalize with settlement finality</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="tab === 'accounts'" class="accounts-section">
      <div class="filter-bar">
        <input v-model="accountFilter" placeholder="Filter by Account ID" @input="filterAccounts" />
        <button class="btn-refresh" @click="loadAccounts">Refresh</button>
      </div>

      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Account ID</th>
            <th>Currency</th>
            <th>Balance</th>
            <th>Last Updated</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="account in filteredAccounts" :key="account.id">
            <td>{{ account.id }}</td>
            <td class="mono">{{ account.accountId }}</td>
            <td><span class="currency-badge">{{ account.currency }}</span></td>
            <td class="number balance">{{ formatCurrency(account.balance, account.currency) }}</td>
            <td>{{ formatDate(account.updatedAt) }}</td>
          </tr>
          <tr v-if="filteredAccounts.length === 0">
            <td colspan="5" class="empty">No cash accounts found</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import { dvpApi } from '../api/settlement'

export default {
  name: 'DvpView',
  data() {
    return {
      tab: 'operations',
      tradeRef: '',
      loading: null,
      operationResult: null,
      accounts: [],
      accountFilter: ''
    }
  },
  computed: {
    filteredAccounts() {
      if (!this.accountFilter) return this.accounts
      const filter = this.accountFilter.toLowerCase()
      return this.accounts.filter(a => a.accountId?.toLowerCase().includes(filter))
    }
  },
  methods: {
    async dvpAction(action) {
      this.loading = action
      this.operationResult = null
      try {
        let response
        if (action === 'lock') response = await dvpApi.lock(this.tradeRef)
        else if (action === 'complete') response = await dvpApi.complete(this.tradeRef)
        else response = await dvpApi.rollback(this.tradeRef)

        this.operationResult = {
          type: 'success',
          action: action.toUpperCase(),
          message: `Status: ${response.data.status} | Trade Ref: ${response.data.tradeRef}`
        }
      } catch (e) {
        this.operationResult = {
          type: 'error',
          action: action.toUpperCase(),
          message: e.response?.data?.message || e.message
        }
      } finally {
        this.loading = null
      }
    },
    async loadAccounts() {
      try {
        const response = await dvpApi.cashAccounts()
        this.accounts = response.data
      } catch (e) {
        console.error('Failed to load cash accounts', e)
      }
    },
    filterAccounts() {},
    formatCurrency(val, currency) {
      if (val == null) return '—'
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: currency || 'HKD',
        minimumFractionDigits: 2
      }).format(val)
    },
    formatDate(val) {
      if (!val) return '—'
      return new Date(val).toLocaleString()
    }
  }
}
</script>

<style scoped>
.dvp-view { max-width: 100%; }
.page-header { margin-bottom: 1.5rem; }
.page-header h2 { font-size: 1.5rem; color: #1a237e; }
.subtitle { color: #666; margin-top: 0.25rem; font-size: 0.9rem; }

.tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
  border-bottom: 2px solid #e0e0e0;
  padding-bottom: 0.5rem;
}
.tabs button {
  padding: 0.5rem 1.2rem;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.9rem;
  border-radius: 4px 4px 0 0;
  color: #666;
}
.tabs button.active { background: #e8eaf6; color: #1a237e; font-weight: 600; }

.dvp-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}
.dvp-card h3 { font-size: 1.1rem; margin-bottom: 0.5rem; color: #333; }
.card-desc { color: #666; font-size: 0.85rem; margin-bottom: 1rem; }

.operation-form { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; }
.operation-form .form-group { flex: 1; min-width: 200px; }
.operation-form .form-group label { display: block; font-size: 0.82rem; font-weight: 600; margin-bottom: 0.25rem; }
.operation-form .form-group input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}
.btn-group { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-lock { background: #ede7f6; color: #4527a0; }
.btn-lock:hover:not(:disabled) { background: #d1c4e9; }
.btn-complete { background: #e8f5e9; color: #2e7d32; }
.btn-complete:hover:not(:disabled) { background: #c8e6c9; }
.btn-rollback { background: #fbe9e7; color: #c62828; }
.btn-rollback:hover:not(:disabled) { background: #ffccbc; }

.result-message {
  margin-top: 1rem;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  font-size: 0.85rem;
}
.result-message.success { background: #e8f5e9; color: #2e7d32; }
.result-message.error { background: #fbe9e7; color: #c62828; }

.dvp-flow {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.5rem;
}
.dvp-flow h3 { margin-bottom: 1rem; font-size: 1rem; color: #333; }
.flow-diagram { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
.flow-step {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: #f5f5f5;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
}
.step-number {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #1a237e;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.8rem;
}
.step-content { display: flex; flex-direction: column; }
.step-content strong { font-size: 0.85rem; }
.step-content span { font-size: 0.75rem; color: #666; }
.flow-arrow { font-size: 1.5rem; color: #999; }

.filter-bar { display: flex; gap: 0.75rem; margin-bottom: 1rem; }
.filter-bar input { padding: 0.4rem 0.8rem; border: 1px solid #ccc; border-radius: 4px; flex: 1; max-width: 300px; }
.btn-refresh {
  padding: 0.4rem 0.8rem;
  border: 1px solid #1a237e;
  background: #fff;
  color: #1a237e;
  border-radius: 4px;
  cursor: pointer;
}

.data-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.data-table th {
  background: #f5f5f5;
  padding: 0.6rem 0.75rem;
  text-align: left;
  font-weight: 600;
  border-bottom: 2px solid #e0e0e0;
}
.data-table td { padding: 0.6rem 0.75rem; border-bottom: 1px solid #eee; }
.data-table tr:hover { background: #fafafa; }
.mono { font-family: 'SF Mono', Menlo, monospace; font-size: 0.82rem; }
.number { text-align: right; font-variant-numeric: tabular-nums; }
.balance { font-weight: 600; }
.empty { text-align: center; color: #999; padding: 2rem !important; }
.currency-badge {
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.7rem;
  font-weight: 700;
  background: #f3e5f5;
  color: #6a1b9a;
}
</style>
