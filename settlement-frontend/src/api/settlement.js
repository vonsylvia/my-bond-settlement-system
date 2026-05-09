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
