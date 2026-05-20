import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

export function createSettlement(data) {
  return api.post('/settlement', data)
}

export function getSettlement(tradeRef) {
  return api.get(`/settlement/${tradeRef}`)
}

export function listSettlements(page = 0, size = 20) {
  return api.get('/settlement', { params: { page, size } })
}

export function retrySettlement(tradeRef) {
  return api.post(`/settlement/${tradeRef}/retry`)
}

export function getHoldings(accountId) {
  const params = accountId ? { accountId } : {}
  return api.get('/holdings', { params })
}

export function getMessages(tradeRef) {
  return api.get(`/settlement/${tradeRef}/messages`)
}

export function getCounterpartyByBic(bicCode) {
  return api.get(`/counterparty/${bicCode}`)
}

export function listCounterparties() {
  return api.get('/counterparty')
}

export function createCounterparty(data) {
  return api.post('/counterparty', data)
}

export function updateCounterparty(bicCode, data) {
  return api.put(`/counterparty/${bicCode}`, data)
}

export function deleteCounterparty(bicCode) {
  return api.delete(`/counterparty/${bicCode}`)
}

export function translateMessage(rawPayload, targetStandard) {
  const data = { rawPayload }
  if (targetStandard) data.targetStandard = targetStandard
  return api.post('/translation/translate', data)
}

export function detectMessage(rawPayload) {
  return api.post('/translation/detect', { rawPayload })
}

export function reconcilePositions() {
  return api.post('/positions/reconcile')
}

export function dailyClose() {
  return api.post('/positions/daily-close')
}

export function getMqHealth() {
  return api.get('/mq/health')
}

export const matchingApi = {
  list(params = {}) {
    return api.get('/matching', { params })
  },
  submit(instruction) {
    return api.post('/matching', instruction)
  },
  retry(id) {
    return api.post(`/matching/${id}/retry`)
  },
  cancel(id) {
    return api.post(`/matching/${id}/cancel`)
  },
  alleged() {
    return api.get('/matching/alleged')
  },
  unmatched() {
    return api.get('/matching/unmatched')
  }
}
