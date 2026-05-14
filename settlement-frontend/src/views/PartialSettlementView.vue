<template>
  <div class="partial-view">
    <div class="page-header">
      <h2>Partial Settlement</h2>
      <p class="subtitle">ICMA-recommended shaping — split large instructions into smaller batches to reduce settlement fails</p>
    </div>

    <div class="card shape-card">
      <h3>Shape Instruction</h3>
      <p class="card-desc">Split a large settlement instruction into smaller partial settlements (default batch: 50M nominal).</p>

      <div class="shape-form">
        <div class="form-group">
          <label>Instruction ID</label>
          <input v-model.number="instructionId" type="number" min="1" placeholder="e.g. 1" />
        </div>
        <div class="form-group">
          <label>Batch Size (optional)</label>
          <input v-model.number="batchSize" type="number" min="1" placeholder="Default: 50,000,000" />
        </div>
        <button class="btn btn-shape" @click="shapeInstruction" :disabled="!instructionId || shaping">
          {{ shaping ? 'Shaping...' : 'Shape into Batches' }}
        </button>
      </div>

      <div v-if="shapeResult" class="result-message" :class="shapeResult.type">
        {{ shapeResult.message }}
      </div>
    </div>

    <div class="card splits-card">
      <h3>View Splits</h3>
      <div class="lookup-form">
        <div class="form-group">
          <label>Parent Instruction ID</label>
          <input v-model.number="lookupId" type="number" min="1" placeholder="Enter instruction ID" />
        </div>
        <button class="btn btn-lookup" @click="loadSplits" :disabled="!lookupId">Load Splits</button>
      </div>

      <table v-if="splits.length > 0" class="data-table">
        <thead>
          <tr>
            <th>Split ID</th>
            <th>Sequence</th>
            <th>Trade Ref</th>
            <th>Quantity</th>
            <th>Status</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="split in splits" :key="split.id">
            <td>{{ split.id }}</td>
            <td class="center">{{ split.splitSequence }}</td>
            <td class="mono">{{ split.tradeRef }}</td>
            <td class="number">{{ formatNumber(split.quantity) }}</td>
            <td>
              <span :class="['split-status', split.status?.toLowerCase()]">{{ split.status }}</span>
            </td>
            <td>{{ formatDate(split.createdAt) }}</td>
            <td>
              <button v-if="split.status === 'PENDING' || split.status === 'SENT'"
                      class="btn-action btn-settle" @click="completeSplit(split.id)">
                Mark Settled
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="splits.length > 0" class="splits-summary">
        <div class="summary-item">
          <span class="summary-label">Total Splits</span>
          <span class="summary-value">{{ splits.length }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Settled</span>
          <span class="summary-value success">{{ splits.filter(s => s.status === 'SETTLED').length }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Pending</span>
          <span class="summary-value warning">{{ splits.filter(s => s.status === 'PENDING' || s.status === 'SENT').length }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">Total Quantity</span>
          <span class="summary-value">{{ formatNumber(totalQuantity) }}</span>
        </div>
      </div>

      <div v-if="lookupId && splits.length === 0 && !loading" class="empty-state">
        No partial settlements found for this instruction.
      </div>
    </div>
  </div>
</template>

<script>
import { partialApi } from '../api/settlement'

export default {
  name: 'PartialSettlementView',
  data() {
    return {
      instructionId: null,
      batchSize: null,
      shaping: false,
      shapeResult: null,
      lookupId: null,
      splits: [],
      loading: false
    }
  },
  computed: {
    totalQuantity() {
      return this.splits.reduce((sum, s) => sum + Number(s.quantity || 0), 0)
    }
  },
  methods: {
    async shapeInstruction() {
      this.shaping = true
      this.shapeResult = null
      try {
        const response = await partialApi.shape(this.instructionId, this.batchSize || undefined)
        const count = response.data.length
        if (count === 0) {
          this.shapeResult = { type: 'info', message: 'Instruction quantity is within batch size — no shaping needed.' }
        } else {
          this.shapeResult = { type: 'success', message: `Successfully shaped into ${count} partial settlements.` }
          this.lookupId = this.instructionId
          this.splits = response.data
        }
      } catch (e) {
        this.shapeResult = { type: 'error', message: e.response?.data?.message || 'Shaping failed' }
      } finally {
        this.shaping = false
      }
    },
    async loadSplits() {
      this.loading = true
      try {
        const response = await partialApi.getSplits(this.lookupId)
        this.splits = response.data
      } catch (e) {
        console.error('Failed to load splits', e)
        this.splits = []
      } finally {
        this.loading = false
      }
    },
    async completeSplit(splitId) {
      if (!confirm('Mark this split as settled?')) return
      try {
        await partialApi.completeSplit(splitId)
        this.loadSplits()
      } catch (e) {
        alert('Failed: ' + (e.response?.data?.message || e.message))
      }
    },
    formatNumber(val) {
      if (val == null) return '—'
      return Number(val).toLocaleString()
    },
    formatDate(val) {
      if (!val) return '—'
      return new Date(val).toLocaleString()
    }
  }
}
</script>

<style scoped>
.partial-view { max-width: 100%; }
.page-header { margin-bottom: 1.5rem; }
.page-header h2 { font-size: 1.5rem; color: #1a237e; }
.subtitle { color: #666; margin-top: 0.25rem; font-size: 0.9rem; }

.card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}
.card h3 { font-size: 1.1rem; margin-bottom: 0.5rem; color: #333; }
.card-desc { color: #666; font-size: 0.85rem; margin-bottom: 1rem; }

.shape-form, .lookup-form { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1rem; }
.form-group { flex: 1; min-width: 160px; max-width: 250px; }
.form-group label { display: block; font-size: 0.82rem; font-weight: 600; margin-bottom: 0.25rem; color: #333; }
.form-group input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.9rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-shape { background: #e0f2f1; color: #00695c; }
.btn-shape:hover:not(:disabled) { background: #b2dfdb; }
.btn-lookup { background: #e3f2fd; color: #1565c0; }
.btn-lookup:hover:not(:disabled) { background: #bbdefb; }

.result-message {
  margin-top: 1rem;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  font-size: 0.85rem;
}
.result-message.success { background: #e8f5e9; color: #2e7d32; }
.result-message.info { background: #e3f2fd; color: #1565c0; }
.result-message.error { background: #fbe9e7; color: #c62828; }

.data-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; margin-top: 1rem; }
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
.center { text-align: center; }

.split-status {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  border-radius: 10px;
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
}
.split-status.pending { background: #fff3e0; color: #e65100; }
.split-status.sent { background: #e3f2fd; color: #1565c0; }
.split-status.settled { background: #e8f5e9; color: #2e7d32; }
.split-status.failed { background: #fbe9e7; color: #c62828; }

.btn-action {
  padding: 0.25rem 0.5rem;
  border: none;
  border-radius: 3px;
  font-size: 0.72rem;
  cursor: pointer;
}
.btn-settle { background: #e8f5e9; color: #2e7d32; }
.btn-settle:hover { background: #c8e6c9; }

.splits-summary {
  display: flex;
  gap: 1.5rem;
  margin-top: 1rem;
  padding: 1rem;
  background: #f5f5f5;
  border-radius: 6px;
  flex-wrap: wrap;
}
.summary-item { display: flex; flex-direction: column; align-items: center; }
.summary-label { font-size: 0.75rem; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
.summary-value { font-size: 1.2rem; font-weight: 700; color: #333; }
.summary-value.success { color: #2e7d32; }
.summary-value.warning { color: #e65100; }

.empty-state { text-align: center; color: #999; padding: 2rem; }
</style>
