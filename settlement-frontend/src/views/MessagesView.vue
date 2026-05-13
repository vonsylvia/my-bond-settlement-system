<template>
  <div class="messages-container">
    <div class="messages-card">
      <div class="messages-header">
        <div>
          <h2>SWIFT Messages</h2>
          <p class="subtitle">Trade Ref: <span class="mono">{{ tradeRef }}</span></p>
        </div>
        <router-link to="/settlement/list" class="btn-back">Back to List</router-link>
      </div>

      <div v-if="loading" class="loading">Loading messages...</div>

      <div v-else-if="messages.length === 0" class="empty">
        No SWIFT messages found for this instruction.
      </div>

      <div v-else class="messages-grid">
        <div class="format-section" v-for="group in groupedMessages" :key="group.label">
          <h3 class="section-title">
            <span class="standard-badge" :class="group.standard.toLowerCase()">
              {{ group.standard }}
            </span>
            {{ group.label }}
          </h3>
          <div class="message-card" v-for="msg in group.messages" :key="msg.id"
               :class="{ translated: msg.translated }">
            <div class="message-meta">
              <span class="meta-item">
                <span class="meta-label">Type:</span>
                <span class="mono">{{ msg.messageType }}</span>
              </span>
              <span class="meta-item">
                <span class="meta-label">Direction:</span>
                <span :class="['direction-tag', msg.direction.toLowerCase()]">
                  {{ msg.direction }}
                </span>
              </span>
              <span v-if="msg.translated" class="translated-badge">Translated Copy</span>
              <span v-if="msg.parsedStatus" class="meta-item">
                <span class="meta-label">Status:</span>
                <span class="status-value">{{ msg.parsedStatus }}</span>
              </span>
              <span class="meta-item meta-time">
                {{ formatDate(msg.createdAt) }}
              </span>
            </div>
            <div class="payload-wrapper">
              <button class="btn-toggle" @click="togglePayload(msg.id)">
                {{ expandedIds.has(msg.id) ? 'Collapse' : 'Expand Payload' }}
              </button>
              <pre v-if="expandedIds.has(msg.id)" class="payload">{{ msg.rawPayload }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getMessages } from '../api/settlement.js'

export default {
  name: 'MessagesView',
  data() {
    return {
      tradeRef: '',
      messages: [],
      loading: false,
      expandedIds: new Set()
    }
  },
  computed: {
    groupedMessages() {
      const groups = []
      const mtMessages = this.messages.filter(m => m.messageStandard === 'MT')
      const mxMessages = this.messages.filter(m => m.messageStandard === 'MX')

      if (mtMessages.length > 0) {
        groups.push({
          label: 'SWIFT FIN (Traditional)',
          standard: 'MT',
          messages: mtMessages
        })
      }
      if (mxMessages.length > 0) {
        groups.push({
          label: 'ISO 20022 (XML)',
          standard: 'MX',
          messages: mxMessages
        })
      }
      return groups
    }
  },
  mounted() {
    this.tradeRef = this.$route.params.tradeRef
    this.loadMessages()
  },
  methods: {
    async loadMessages() {
      this.loading = true
      try {
        const response = await getMessages(this.tradeRef)
        this.messages = response.data
      } catch (error) {
        console.error('Failed to load messages', error)
      } finally {
        this.loading = false
      }
    },
    togglePayload(id) {
      const next = new Set(this.expandedIds)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      this.expandedIds = next
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
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
      })
    }
  }
}
</script>

<style scoped>
.messages-container {
  width: 100%;
}

.messages-card {
  background: #fff;
  border-radius: 12px;
  padding: 2rem;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
}

.messages-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
}

.messages-header h2 {
  color: #1a237e;
  font-size: 1.4rem;
  margin-bottom: 0.25rem;
}

.subtitle {
  color: #666;
  font-size: 0.9rem;
}

.mono {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 0.85rem;
}

.btn-back {
  padding: 0.5rem 1rem;
  background: #e8eaf6;
  color: #3f51b5;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  text-decoration: none;
  font-size: 0.85rem;
  transition: background 0.2s;
}

.btn-back:hover {
  background: #c5cae9;
}

.messages-grid {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.section-title {
  font-size: 1.05rem;
  color: #333;
  margin-bottom: 0.75rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.standard-badge {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.standard-badge.mt {
  background: #fff3e0;
  color: #e65100;
}

.standard-badge.mx {
  background: #e8f5e9;
  color: #2e7d32;
}

.message-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 0.75rem;
  transition: border-color 0.2s;
}

.message-card:hover {
  border-color: #90caf9;
}

.message-card.translated {
  border-left: 3px solid #7c4dff;
  background: #fafafa;
}

.message-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  margin-bottom: 0.5rem;
}

.meta-item {
  font-size: 0.82rem;
  color: #555;
}

.meta-label {
  color: #888;
  margin-right: 0.25rem;
}

.meta-time {
  margin-left: auto;
  color: #999;
}

.direction-tag {
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  font-size: 0.72rem;
  font-weight: 600;
}

.direction-tag.outbound {
  background: #e3f2fd;
  color: #1565c0;
}

.direction-tag.inbound {
  background: #f3e5f5;
  color: #7b1fa2;
}

.translated-badge {
  padding: 0.15rem 0.5rem;
  background: #ede7f6;
  color: #7c4dff;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
}

.status-value {
  font-weight: 600;
  color: #333;
}

.payload-wrapper {
  margin-top: 0.5rem;
}

.btn-toggle {
  padding: 0.3rem 0.6rem;
  background: #f5f5f5;
  border: 1px solid #e0e0e0;
  border-radius: 5px;
  cursor: pointer;
  font-size: 0.75rem;
  color: #555;
  transition: background 0.2s;
}

.btn-toggle:hover {
  background: #eeeeee;
}

.payload {
  margin-top: 0.5rem;
  padding: 1rem;
  background: #263238;
  color: #e0e0e0;
  border-radius: 6px;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 0.75rem;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.empty {
  text-align: center;
  color: #999;
  padding: 2rem;
}
</style>
