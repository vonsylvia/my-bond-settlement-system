<template>
  <div class="cp-container">
    <div class="cp-card">
      <div class="cp-header">
        <div>
          <h2>Counterparty Capabilities</h2>
          <p class="subtitle">Manage SWIFT MT/MX routing preferences for counterparties</p>
        </div>
        <button class="btn-add" @click="showForm = !showForm">
          {{ showForm ? 'Cancel' : '+ Add Counterparty' }}
        </button>
      </div>

      <div v-if="showForm" class="form-section">
        <form @submit.prevent="handleSubmit">
          <div class="form-row">
            <div class="form-group">
              <label for="bicCode">BIC Code</label>
              <input id="bicCode" v-model="form.bicCode" type="text"
                     placeholder="e.g. GOLDUS33XXX" maxlength="11"
                     :disabled="editMode" required />
            </div>
            <div class="form-group">
              <label for="participantName">Participant Name</label>
              <input id="participantName" v-model="form.participantName" type="text"
                     placeholder="e.g. Goldman Sachs" required />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label for="supportedStandard">Supported Standard</label>
              <select id="supportedStandard" v-model="form.supportedStandard" required>
                <option value="DUAL">DUAL (MT + MX)</option>
                <option value="MT_ONLY">MT_ONLY</option>
                <option value="MX_ONLY">MX_ONLY</option>
              </select>
            </div>
            <div class="form-group">
              <label for="preferredStandard">Preferred Standard</label>
              <select id="preferredStandard" v-model="form.preferredStandard" required>
                <option value="MT">MT</option>
                <option value="MX">MX</option>
              </select>
            </div>
          </div>
          <div class="form-actions">
            <button type="submit" class="btn-submit" :disabled="submitting">
              {{ submitting ? 'Saving...' : (editMode ? 'Update' : 'Create') }}
            </button>
          </div>
        </form>
      </div>

      <div v-if="error" class="alert alert-error">{{ error }}</div>

      <div v-if="loading" class="loading">Loading...</div>

      <table v-else class="data-table">
        <thead>
          <tr>
            <th>BIC Code</th>
            <th>Participant</th>
            <th>Supported</th>
            <th>Preferred</th>
            <th>Resolved Outbound</th>
            <th>Effective Date</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="cp in counterparties" :key="cp.bicCode">
            <td class="mono">{{ cp.bicCode }}</td>
            <td>{{ cp.participantName }}</td>
            <td>
              <span :class="['cap-badge', cp.supportedStandard.toLowerCase().replace('_', '-')]">
                {{ cp.supportedStandard }}
              </span>
            </td>
            <td>
              <span :class="['std-badge', cp.preferredStandard.toLowerCase()]">
                {{ cp.preferredStandard }}
              </span>
            </td>
            <td>
              <span :class="['std-badge', 'resolved', cp.resolvedOutbound.toLowerCase()]">
                {{ cp.resolvedOutbound }}
              </span>
            </td>
            <td>{{ formatDate(cp.effectiveDate) }}</td>
            <td class="actions-cell">
              <button class="btn-edit" @click="startEdit(cp)">Edit</button>
              <button class="btn-delete" @click="handleDelete(cp.bicCode)">Remove</button>
            </td>
          </tr>
          <tr v-if="counterparties.length === 0">
            <td colspan="7" class="empty">No counterparties registered</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import { listCounterparties, createCounterparty, updateCounterparty, deleteCounterparty } from '../api/settlement.js'

export default {
  name: 'CounterpartyView',
  data() {
    return {
      counterparties: [],
      loading: false,
      showForm: false,
      editMode: false,
      submitting: false,
      error: '',
      form: {
        bicCode: '',
        participantName: '',
        supportedStandard: 'DUAL',
        preferredStandard: 'MT'
      }
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const response = await listCounterparties()
        this.counterparties = response.data
      } catch (err) {
        console.error('Failed to load counterparties', err)
      } finally {
        this.loading = false
      }
    },
    async handleSubmit() {
      this.submitting = true
      this.error = ''
      try {
        if (this.editMode) {
          await updateCounterparty(this.form.bicCode, this.form)
        } else {
          await createCounterparty(this.form)
        }
        this.showForm = false
        this.editMode = false
        this.resetForm()
        await this.loadData()
      } catch (err) {
        const data = err.response?.data
        if (data?.errors?.length > 0) {
          this.error = data.errors.join('; ')
        } else {
          this.error = data?.message || 'Operation failed'
        }
      } finally {
        this.submitting = false
      }
    },
    startEdit(cp) {
      this.form = {
        bicCode: cp.bicCode,
        participantName: cp.participantName,
        supportedStandard: cp.supportedStandard,
        preferredStandard: cp.preferredStandard
      }
      this.editMode = true
      this.showForm = true
    },
    async handleDelete(bicCode) {
      if (!confirm(`Deactivate counterparty ${bicCode}?`)) return
      try {
        await deleteCounterparty(bicCode)
        await this.loadData()
      } catch (err) {
        alert('Failed to deactivate: ' + (err.response?.data?.message || err.message))
      }
    },
    resetForm() {
      this.form = {
        bicCode: '',
        participantName: '',
        supportedStandard: 'DUAL',
        preferredStandard: 'MT'
      }
    },
    formatDate(val) {
      if (!val) return ''
      if (Array.isArray(val)) {
        const [y, m, d] = val
        return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
      }
      return val
    }
  }
}
</script>

<style scoped>
.cp-container {
  width: 100%;
}

.cp-card {
  background: #fff;
  border-radius: 12px;
  padding: 2rem;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.cp-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
}

.cp-header h2 {
  color: #1a237e;
  font-size: 1.4rem;
  margin-bottom: 0.25rem;
}

.subtitle {
  color: #666;
  font-size: 0.9rem;
}

.btn-add {
  padding: 0.5rem 1rem;
  background: linear-gradient(135deg, #1a237e, #3f51b5);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.85rem;
  transition: opacity 0.2s;
}

.btn-add:hover {
  opacity: 0.9;
}

.form-section {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  border: 1px solid #e0e0e0;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-bottom: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group label {
  font-size: 0.82rem;
  font-weight: 500;
  color: #444;
  margin-bottom: 0.3rem;
}

.form-group input,
.form-group select {
  padding: 0.5rem 0.7rem;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.85rem;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #3f51b5;
  box-shadow: 0 0 0 3px rgba(63, 81, 181, 0.1);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.btn-submit {
  padding: 0.5rem 1.5rem;
  background: #3f51b5;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.alert {
  padding: 0.75rem 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
  font-size: 0.85rem;
}

.alert-error {
  background: #fbe9e7;
  color: #c62828;
  border: 1px solid #ffccbc;
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

.cap-badge {
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.72rem;
  font-weight: 600;
}

.cap-badge.dual {
  background: #e8f5e9;
  color: #2e7d32;
}

.cap-badge.mt-only {
  background: #fff3e0;
  color: #e65100;
}

.cap-badge.mx-only {
  background: #e3f2fd;
  color: #1565c0;
}

.std-badge {
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  font-size: 0.72rem;
  font-weight: 600;
}

.std-badge.mt {
  background: #fff3e0;
  color: #e65100;
}

.std-badge.mx {
  background: #e3f2fd;
  color: #1565c0;
}

.std-badge.resolved {
  font-weight: 700;
}

.actions-cell {
  display: flex;
  gap: 0.4rem;
}

.btn-edit {
  padding: 0.3rem 0.6rem;
  background: #e8eaf6;
  color: #3f51b5;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 600;
}

.btn-edit:hover {
  background: #c5cae9;
}

.btn-delete {
  padding: 0.3rem 0.6rem;
  background: #fbe9e7;
  color: #c62828;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 600;
}

.btn-delete:hover {
  background: #ffccbc;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.empty {
  text-align: center;
  color: #999;
  padding: 2rem !important;
}
</style>
