import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

const mockReady = import.meta.env.VITE_USE_MOCK === 'true'
  ? import('../mocks/index.js').then(({ installMockApi }) => {
    installMockApi(api)
  })
  : Promise.resolve()

function withMockReady(request) {
  return mockReady.then(request)
}

export function createSettlement(data) {
  return withMockReady(() => api.post('/settlement', data))
}

export function listSettlements(page = 0, size = 20) {
  return withMockReady(() => api.get('/settlement', { params: { page, size } }))
}

export function retrySettlement(tradeRef) {
  return withMockReady(() => api.post(`/settlement/${tradeRef}/retry`))
}

export function getHoldings(accountId) {
  const params = accountId ? { accountId } : {}
  return withMockReady(() => api.get('/holdings', { params }))
}

export function getMessages(tradeRef) {
  return withMockReady(() => api.get(`/settlement/${tradeRef}/messages`))
}

export function getCounterpartyByBic(bicCode) {
  return withMockReady(() => api.get(`/counterparty/${bicCode}`))
}

export function listCounterparties() {
  return withMockReady(() => api.get('/counterparty'))
}

export function createCounterparty(data) {
  return withMockReady(() => api.post('/counterparty', data))
}

export function updateCounterparty(bicCode, data) {
  return withMockReady(() => api.put(`/counterparty/${bicCode}`, data))
}

export function deleteCounterparty(bicCode) {
  return withMockReady(() => api.delete(`/counterparty/${bicCode}`))
}

export const matchingApi = {
  list(params = {}) {
    return withMockReady(() => api.get('/matching', { params }))
  },
  submit(instruction) {
    return withMockReady(() => api.post('/matching', instruction))
  },
  retry(id) {
    return withMockReady(() => api.post(`/matching/${id}/retry`))
  },
  cancel(id) {
    return withMockReady(() => api.post(`/matching/${id}/cancel`))
  },
  alleged() {
    return withMockReady(() => api.get('/matching/alleged'))
  },
  unmatched() {
    return withMockReady(() => api.get('/matching/unmatched'))
  }
}
