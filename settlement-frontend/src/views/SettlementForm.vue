<template>
  <div class="form-container">
    <div class="form-card">
      <h2>SWIFT Direct <span class="phase-tag exec">Execution</span></h2>
      <p class="subtitle">Submit a settlement instruction directly to the SWIFT network — bypasses internal matching.
        Use for cross-CSD instructions, FOP transfers, corporate actions, or manual overrides.
        For standard bilateral settlement, use <router-link to="/matching">Trade Matching</router-link> first.</p>

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
            <input id="settlementDate" v-model="form.settlementDate" type="date" lang="en" required />
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
          <div class="form-group">
            <label for="currency">Currency</label>
            <select id="currency" v-model="form.currency">
              <option value="HKD">HKD</option>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="CNY">CNY</option>
            </select>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="settlementAmount">Settlement Amount</label>
            <input id="settlementAmount" v-model.number="form.settlementAmount" type="number"
                   step="0.01" min="0" placeholder="Cash amount (optional for FOP)" />
            <span class="field-hint">Required for Against Payment (DVP) settlements</span>
          </div>
          <div class="form-group">
            <label for="paymentType">Payment Type</label>
            <select id="paymentType" v-model="form.paymentType">
              <option value="AGAINST_PAYMENT">Against Payment (DVP)</option>
              <option value="FREE_OF_PAYMENT">Free of Payment (FOP)</option>
            </select>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="preferredStandard">Preferred Standard</label>
            <select id="preferredStandard" v-model="form.preferredStandard">
              <option value="MT">MT (FIN)</option>
              <option value="MX">MX (ISO 20022)</option>
            </select>
            <span class="field-hint">Fallback when counterparty capability is unknown</span>
          </div>
          <div class="form-group"></div>
        </div>

        <div v-if="counterpartyInfo" class="counterparty-badge">
          <span class="badge-label">Counterparty Capability</span>
          <div class="badge-content">
            <span class="badge-name">{{ counterpartyInfo.participantName }}</span>
            <span class="badge-tag" :class="'tag-' + counterpartyInfo.supportedStandard.toLowerCase()">
              {{ counterpartyInfo.supportedStandard }}
            </span>
            <span class="badge-detail">
              Resolved outbound: <strong>{{ counterpartyInfo.resolvedOutbound }}</strong>
            </span>
          </div>
        </div>
        <div v-if="counterpartyNotFound" class="counterparty-badge badge-unknown">
          <span class="badge-label">Counterparty Capability</span>
          <div class="badge-content">
            <span class="badge-name">Not registered</span>
            <span class="badge-detail">
              Will use preferred standard (<strong>{{ form.preferredStandard }}</strong>) as fallback
            </span>
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
import { createSettlement, getCounterpartyByBic } from '../api/settlement.js'

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
        accountId: '',
        preferredStandard: 'MT',
        currency: 'HKD',
        settlementAmount: null,
        paymentType: 'AGAINST_PAYMENT'
      },
      submitting: false,
      successMsg: '',
      errorMsg: '',
      counterpartyInfo: null,
      counterpartyNotFound: false,
      bicLookupTimer: null
    }
  },
  watch: {
    'form.bicCode'(newVal) {
      this.counterpartyInfo = null
      this.counterpartyNotFound = false
      if (this.bicLookupTimer) clearTimeout(this.bicLookupTimer)

      const bic = (newVal || '').trim().toUpperCase()
      if (bic.length >= 8) {
        this.bicLookupTimer = setTimeout(() => this.lookupCounterparty(bic), 400)
      }
    }
  },
  methods: {
    async lookupCounterparty(bic) {
      try {
        const response = await getCounterpartyByBic(bic)
        this.counterpartyInfo = response.data
        this.counterpartyNotFound = false
      } catch {
        this.counterpartyInfo = null
        this.counterpartyNotFound = true
      }
    },
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
        accountId: '',
        preferredStandard: 'MT',
        currency: 'HKD',
        settlementAmount: null,
        paymentType: 'AGAINST_PAYMENT'
      }
      this.counterpartyInfo = null
      this.counterpartyNotFound = false
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

.field-hint {
  font-size: 0.75rem;
  color: #888;
  margin-top: 0.25rem;
}

.counterparty-badge {
  background: #e8f5e9;
  border: 1px solid #c8e6c9;
  border-radius: 8px;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.counterparty-badge.badge-unknown {
  background: #fff3e0;
  border-color: #ffe0b2;
}

.badge-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.badge-content {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.35rem;
  flex-wrap: wrap;
}

.badge-name {
  font-weight: 500;
  color: #333;
}

.badge-tag {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  color: #fff;
}

.tag-dual {
  background: #3f51b5;
}

.tag-mt_only {
  background: #ff9800;
}

.tag-mx_only {
  background: #4caf50;
}

.badge-detail {
  font-size: 0.85rem;
  color: #555;
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
