<template>
  <div class="form-container">
    <div class="form-card">
      <h2>New Settlement Instruction</h2>
      <p class="subtitle">Submit a bond settlement instruction to SWIFT network</p>

      <div v-if="successMsg" class="alert alert-success">
        {{ successMsg }}
      </div>
      <div v-if="errorMsg" class="alert alert-error">
        {{ errorMsg }}
      </div>

      <form @submit.prevent="handleSubmit">
        <div class="form-row">
          <div class="form-group">
            <label for="isin">ISIN</label>
            <input id="isin" v-model="form.isin" type="text"
                   placeholder="e.g. US0378331005" maxlength="12" required />
          </div>
          <div class="form-group">
            <label for="direction">Direction</label>
            <select id="direction" v-model="form.direction" required>
              <option value="">Select...</option>
              <option value="BUY">BUY</option>
              <option value="SELL">SELL</option>
            </select>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="quantity">Quantity</label>
            <input id="quantity" v-model.number="form.quantity" type="number"
                   step="0.01" min="0.01" placeholder="e.g. 1000000" required />
          </div>
          <div class="form-group">
            <label for="settlementDate">Settlement Date</label>
            <input id="settlementDate" v-model="form.settlementDate" type="date" required />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="counterparty">Counterparty</label>
            <input id="counterparty" v-model="form.counterparty" type="text"
                   placeholder="e.g. Goldman Sachs" required />
          </div>
          <div class="form-group">
            <label for="bicCode">BIC / SWIFT Code</label>
            <input id="bicCode" v-model="form.bicCode" type="text"
                   placeholder="e.g. GOLDUS33XXX" maxlength="11" required />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="accountId">Account ID</label>
            <input id="accountId" v-model="form.accountId" type="text"
                   placeholder="e.g. ACC-001" required />
          </div>
        </div>

        <button type="submit" class="btn-submit" :disabled="submitting">
          {{ submitting ? 'Submitting...' : 'Submit Instruction' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script>
import { createSettlement } from '../api/settlement.js'

export default {
  name: 'SettlementForm',
  data() {
    return {
      form: {
        isin: '',
        direction: '',
        quantity: null,
        settlementDate: '',
        counterparty: '',
        bicCode: '',
        accountId: ''
      },
      submitting: false,
      successMsg: '',
      errorMsg: ''
    }
  },
  methods: {
    async handleSubmit() {
      this.submitting = true
      this.successMsg = ''
      this.errorMsg = ''

      try {
        const response = await createSettlement(this.form)
        this.successMsg = `Instruction submitted successfully! Trade Ref: ${response.data.tradeRef}`
        this.resetForm()
      } catch (error) {
        if (error.response && error.response.data) {
          const data = error.response.data
          if (data.errors && data.errors.length > 0) {
            this.errorMsg = data.errors.join('; ')
          } else {
            this.errorMsg = data.message || 'Submission failed'
          }
        } else {
          this.errorMsg = 'Network error. Please try again.'
        }
      } finally {
        this.submitting = false
      }
    },
    resetForm() {
      this.form = {
        isin: '',
        direction: '',
        quantity: null,
        settlementDate: '',
        counterparty: '',
        bicCode: '',
        accountId: ''
      }
    }
  }
}
</script>

<style scoped>
.form-container {
  display: flex;
  justify-content: center;
}

.form-card {
  background: #fff;
  border-radius: 12px;
  padding: 2.5rem;
  width: 100%;
  max-width: 720px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.form-card h2 {
  font-size: 1.5rem;
  margin-bottom: 0.25rem;
  color: #1a237e;
}

.subtitle {
  color: #666;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
}

.alert {
  padding: 0.75rem 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.alert-success {
  background: #e8f5e9;
  color: #2e7d32;
  border: 1px solid #c8e6c9;
}

.alert-error {
  background: #fbe9e7;
  color: #c62828;
  border: 1px solid #ffccbc;
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
  font-size: 0.85rem;
  font-weight: 500;
  color: #444;
  margin-bottom: 0.35rem;
}

.form-group input,
.form-group select {
  padding: 0.6rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.9rem;
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #3f51b5;
  box-shadow: 0 0 0 3px rgba(63, 81, 181, 0.1);
}

.btn-submit {
  width: 100%;
  padding: 0.75rem;
  background: linear-gradient(135deg, #1a237e, #3f51b5);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  margin-top: 0.5rem;
  transition: opacity 0.2s;
}

.btn-submit:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
