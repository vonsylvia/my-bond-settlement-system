<template>
  <div class="matching-view">
    <div class="page-header">
      <h2>Trade Matching <span class="phase-tag pre">Pre-Settlement</span></h2>
      <p class="subtitle">Bilateral instruction matching — both buyer and seller submit instructions independently.
        The engine automatically matches when ISIN, settlement date, direction (BUY↔SELL), counterparty BIC,
        and amount (within tolerance) all align. Matched trades proceed to the Settlement phase.</p>
    </div>

    <div class="tabs">
      <button :class="{ active: tab === 'list' }" @click="tab = 'list'">All Instructions</button>
      <button :class="{ active: tab === 'submit' }" @click="tab = 'submit'">Submit New</button>
    </div>

    <div v-if="tab === 'list'" class="table-section">
      <div class="filter-bar">
        <select v-model="statusFilter" @change="page = 0; loadInstructions()">
          <option value="">All Statuses</option>
          <option value="UNMATCHED">Unmatched</option>
          <option value="ALLEGED">Alleged</option>
          <option value="MATCHED">Matched</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <button class="btn-refresh" @click="loadInstructions">Refresh</button>
      </div>

      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Trade Ref</th>
            <th>ISIN</th>
            <th>Direction</th>
            <th>Qty</th>
            <th>Amount</th>
            <th>CCY</th>
            <th>Submitter</th>
            <th>Counterparty</th>
            <th>Settlement Date</th>
            <th>Status</th>
            <th>Matched With</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in instructions" :key="item.id">
            <td>{{ item.id }}</td>
            <td class="mono">{{ item.tradeRef }}</td>
            <td class="mono">{{ item.isin }}</td>
            <td>
              <span :class="['direction-badge', item.direction?.toLowerCase()]">{{ item.direction }}</span>
            </td>
            <td class="number">{{ formatNumber(item.quantity) }}</td>
            <td class="number">{{ item.amount ? formatNumber(item.amount) : '—' }}</td>
            <td><span class="currency-badge">{{ item.currency }}</span></td>
            <td class="mono">{{ item.submitterBic }}</td>
            <td class="mono">{{ item.counterpartyBic }}</td>
            <td>{{ item.settlementDate }}</td>
            <td>
              <span :class="['matching-status', item.matchingStatus?.toLowerCase()]">
                {{ item.matchingStatus }}
              </span>
            </td>
            <td class="mono">{{ resolveMatchedRef(item.matchedWithId) }}</td>
            <td>
              <button v-if="item.matchingStatus === 'ALLEGED' || item.matchingStatus === 'UNMATCHED'"
                      class="btn-action btn-retry" @click="retry(item.id)">Retry</button>
              <button v-if="item.matchingStatus !== 'CANCELLED' && item.matchingStatus !== 'MATCHED'"
                      class="btn-action btn-cancel" @click="cancel(item.id)">Cancel</button>
            </td>
          </tr>
          <tr v-if="instructions.length === 0">
            <td colspan="13" class="empty">No matching instructions found</td>
          </tr>
        </tbody>
      </table>

      <div v-if="totalPages > 1" class="pagination">
        <button :disabled="page === 0" @click="page--; loadInstructions()">Previous</button>
        <span>Page {{ page + 1 }} of {{ totalPages }}</span>
        <button :disabled="page >= totalPages - 1" @click="page++; loadInstructions()">Next</button>
      </div>
    </div>

    <div v-if="tab === 'submit'" class="form-section">
      <form @submit.prevent="submitInstruction" class="matching-form">
        <div class="form-row">
          <div class="form-group">
            <label>Trade Reference</label>
            <input v-model="form.tradeRef" required placeholder="e.g. MATCH-001" />
          </div>
          <div class="form-group">
            <label>ISIN</label>
            <input v-model="form.isin" required placeholder="e.g. HK0000163607" maxlength="12" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Direction</label>
            <select v-model="form.direction" required>
              <option value="BUY">BUY</option>
              <option value="SELL">SELL</option>
            </select>
          </div>
          <div class="form-group">
            <label>Settlement Date</label>
            <input v-model="form.settlementDate" type="date" required />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Quantity</label>
            <input v-model.number="form.quantity" type="number" step="0.01" min="0" required />
          </div>
          <div class="form-group">
            <label>Amount</label>
            <input v-model.number="form.amount" type="number" step="0.01" min="0" placeholder="Optional" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Currency</label>
            <select v-model="form.currency">
              <option value="HKD">HKD</option>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="CNY">CNY</option>
            </select>
          </div>
          <div class="form-group">
            <label>Submitter BIC</label>
            <input v-model="form.submitterBic" required placeholder="e.g. HABORHKHXXX" maxlength="11" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Counterparty BIC</label>
            <input v-model="form.counterpartyBic" required placeholder="e.g. BABORHKHXXX" maxlength="11" />
          </div>
          <div class="form-group"></div>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn-submit" :disabled="submitting">
            {{ submitting ? 'Submitting...' : 'Submit for Matching' }}
          </button>
        </div>
        <div v-if="submitResult" class="result-message" :class="submitResult.type">
          {{ submitResult.message }}
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { matchingApi } from '../api/settlement'

export default {
  name: 'MatchingView',
  data() {
    return {
      tab: 'list',
      statusFilter: '',
      instructions: [],
      idToRefMap: {},
      page: 0,
      totalPages: 0,
      submitting: false,
      submitResult: null,
      form: {
        tradeRef: '',
        isin: '',
        direction: 'BUY',
        settlementDate: '',
        quantity: null,
        amount: null,
        currency: 'HKD',
        submitterBic: '',
        counterpartyBic: ''
      }
    }
  },
  mounted() {
    this.loadInstructions()
  },
  methods: {
    async loadInstructions() {
      try {
        const params = { page: this.page, size: 20 }
        if (this.statusFilter) params.status = this.statusFilter
        const response = await matchingApi.list(params)
        this.instructions = response.data.content
        this.totalPages = response.data.totalPages
        this.idToRefMap = {}
        for (const item of this.instructions) {
          this.idToRefMap[item.id] = item.tradeRef
        }
      } catch (e) {
        console.error('Failed to load matching instructions', e)
      }
    },
    async submitInstruction() {
      this.submitting = true
      this.submitResult = null
      try {
        const response = await matchingApi.submit(this.form)
        const status = response.data.matchingStatus
        this.page = 0
        await this.loadInstructions()
        const matchedRef = this.resolveMatchedRef(response.data.matchedWithId)
        this.submitResult = {
          type: status === 'MATCHED' ? 'success' : 'info',
          message: status === 'MATCHED'
            ? `Matched! Paired with ${matchedRef}`
            : `Submitted — status: ${status}. Awaiting counterparty instruction.`
        }
      } catch (e) {
        this.submitResult = { type: 'error', message: e.response?.data?.message || 'Submission failed' }
      } finally {
        this.submitting = false
      }
    },
    async retry(id) {
      try {
        const response = await matchingApi.retry(id)
        if (response.data.matched) {
          alert(`Matched with instruction #${response.data.matchedWithId}`)
        } else {
          alert('No match found yet')
        }
        this.loadInstructions()
      } catch (e) {
        alert('Retry failed: ' + (e.response?.data?.message || e.message))
      }
    },
    async cancel(id) {
      if (!confirm('Cancel this matching instruction?')) return
      try {
        await matchingApi.cancel(id)
        this.loadInstructions()
      } catch (e) {
        alert('Cancel failed: ' + (e.response?.data?.message || e.message))
      }
    },
    resolveMatchedRef(matchedWithId) {
      if (!matchedWithId) return '—'
      return this.idToRefMap[matchedWithId] || `#${matchedWithId}`
    },
    formatNumber(val) {
      if (val == null) return '—'
      return Number(val).toLocaleString()
    }
  }
}
</script>

<style scoped>
.matching-view { max-width: 100%; }

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
  transition: all 0.2s;
}
.tabs button.active { background: #e8eaf6; color: #1a237e; font-weight: 600; }

.filter-bar { display: flex; gap: 0.75rem; margin-bottom: 1rem; align-items: center; }
.filter-bar select { padding: 0.4rem 0.8rem; border: 1px solid #ccc; border-radius: 4px; }
.btn-refresh {
  padding: 0.4rem 0.8rem;
  border: 1px solid #1a237e;
  background: #fff;
  color: #1a237e;
  border-radius: 4px;
  cursor: pointer;
}
.btn-refresh:hover { background: #e8eaf6; }

.data-table { width: 100%; border-collapse: collapse; font-size: 0.82rem; }
.data-table th {
  background: #f5f5f5;
  padding: 0.6rem 0.5rem;
  text-align: left;
  font-weight: 600;
  border-bottom: 2px solid #e0e0e0;
  white-space: nowrap;
}
.data-table td {
  padding: 0.5rem;
  border-bottom: 1px solid #eee;
  vertical-align: middle;
}
.data-table tr:hover { background: #fafafa; }

.mono { font-family: 'SF Mono', Menlo, monospace; font-size: 0.78rem; }
.number { text-align: right; font-variant-numeric: tabular-nums; }
.empty { text-align: center; color: #999; padding: 2rem !important; }

.direction-badge {
  padding: 0.15rem 0.5rem;
  border-radius: 3px;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
}
.direction-badge.buy { background: #e8f5e9; color: #2e7d32; }
.direction-badge.sell { background: #fce4ec; color: #c62828; }

.currency-badge {
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  font-size: 0.68rem;
  font-weight: 700;
  background: #f3e5f5;
  color: #6a1b9a;
}

.matching-status {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  border-radius: 10px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
}
.matching-status.unmatched { background: #fff3e0; color: #e65100; }
.matching-status.alleged { background: #fff8e1; color: #f57f17; }
.matching-status.matched { background: #e8f5e9; color: #2e7d32; }
.matching-status.cancelled { background: #f5f5f5; color: #757575; }

.btn-action {
  padding: 0.25rem 0.5rem;
  border: none;
  border-radius: 3px;
  font-size: 0.72rem;
  cursor: pointer;
  margin-right: 0.25rem;
}
.btn-retry { background: #e3f2fd; color: #1565c0; }
.btn-retry:hover { background: #bbdefb; }
.btn-cancel { background: #fbe9e7; color: #c62828; }
.btn-cancel:hover { background: #ffccbc; }

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

.matching-form {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.5rem;
  max-width: 700px;
}
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1rem; }
.form-group label { display: block; font-size: 0.82rem; font-weight: 600; margin-bottom: 0.25rem; color: #333; }
.form-group input, .form-group select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.9rem;
}
.form-actions { margin-top: 1.5rem; }
.btn-submit {
  padding: 0.6rem 1.5rem;
  background: #1a237e;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  cursor: pointer;
}
.btn-submit:hover { background: #283593; }
.btn-submit:disabled { opacity: 0.5; cursor: not-allowed; }

.result-message {
  margin-top: 1rem;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  font-size: 0.85rem;
}
.result-message.success { background: #e8f5e9; color: #2e7d32; }
.result-message.info { background: #e3f2fd; color: #1565c0; }
.result-message.error { background: #fbe9e7; color: #c62828; }

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
