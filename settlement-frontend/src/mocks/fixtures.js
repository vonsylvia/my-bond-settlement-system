const now = new Date('2026-05-20T09:30:00+08:00').toISOString()

export const counterparties = [
  {
    bicCode: 'HSBCHKHKXXX',
    participantName: 'HSBC Hong Kong',
    supportedStandard: 'DUAL',
    preferredStandard: 'MX',
    resolvedOutbound: 'MX',
    effectiveDate: '2026-01-01',
    active: true
  },
  {
    bicCode: 'BABORHKHXXX',
    participantName: 'Bank of East Asia',
    supportedStandard: 'MT_ONLY',
    preferredStandard: 'MT',
    resolvedOutbound: 'MT',
    effectiveDate: '2026-01-01',
    active: true
  },
  {
    bicCode: 'GOLDUS33XXX',
    participantName: 'Goldman Sachs',
    supportedStandard: 'DUAL',
    preferredStandard: 'MT',
    resolvedOutbound: 'MT',
    effectiveDate: '2026-01-01',
    active: true
  }
]

export const holdings = [
  {
    accountId: 'ACC-001',
    isin: 'HK0000163607',
    quantity: 2500000,
    updatedAt: now
  },
  {
    accountId: 'ACC-001',
    isin: 'US0378331005',
    quantity: 1000000,
    updatedAt: now
  },
  {
    accountId: 'ACC-002',
    isin: 'HK0000123456',
    quantity: 750000,
    updatedAt: now
  }
]

export const settlements = [
  {
    tradeRef: 'TR-20260520-001',
    isin: 'HK0000163607',
    settlementDate: '2026-06-01',
    quantity: 100000,
    counterparty: 'HSBC Hong Kong',
    bicCode: 'HSBCHKHKXXX',
    direction: 'BUY',
    status: 'SENT',
    accountId: 'ACC-001',
    requestedStandard: 'MX',
    resolvedStandard: 'MX',
    currency: 'HKD',
    settlementAmount: 980000,
    paymentType: 'AGAINST_PAYMENT',
    isFinal: false,
    createdAt: now
  },
  {
    tradeRef: 'TR-20260520-002',
    isin: 'US0378331005',
    settlementDate: '2026-06-03',
    quantity: 50000,
    counterparty: 'Goldman Sachs',
    bicCode: 'GOLDUS33XXX',
    direction: 'SELL',
    status: 'MATCHED',
    accountId: 'ACC-001',
    requestedStandard: 'MT',
    resolvedStandard: 'MT',
    currency: 'USD',
    settlementAmount: 998750.25,
    paymentType: 'AGAINST_PAYMENT',
    isFinal: true,
    finalityTimestamp: now,
    createdAt: now
  },
  {
    tradeRef: 'TR-20260520-003',
    isin: 'HK0000123456',
    settlementDate: '2026-06-05',
    quantity: 200000,
    counterparty: 'Bank of East Asia',
    bicCode: 'BABORHKHXXX',
    direction: 'BUY',
    status: 'FAILED',
    accountId: 'ACC-002',
    requestedStandard: 'MT',
    resolvedStandard: 'MT',
    currency: 'HKD',
    settlementAmount: 1999000,
    paymentType: 'AGAINST_PAYMENT',
    failureReason: 'Mock MQ send timeout',
    isFinal: false,
    createdAt: now
  }
]

export const matchingInstructions = [
  {
    id: 1,
    tradeRef: 'M-BUY-001',
    isin: 'HK0000163607',
    settlementDate: '2026-06-01',
    quantity: 100000,
    amount: 98000,
    currency: 'HKD',
    submitterBic: 'HSBCHKHKXXX',
    counterpartyBic: 'BABORHKHXXX',
    direction: 'BUY',
    matchingStatus: 'MATCHED',
    matchedWithId: 2,
    createdAt: now
  },
  {
    id: 2,
    tradeRef: 'M-SELL-001',
    isin: 'HK0000163607',
    settlementDate: '2026-06-01',
    quantity: 100000,
    amount: 98000,
    currency: 'HKD',
    submitterBic: 'BABORHKHXXX',
    counterpartyBic: 'HSBCHKHKXXX',
    direction: 'SELL',
    matchingStatus: 'MATCHED',
    matchedWithId: 1,
    createdAt: now
  },
  {
    id: 3,
    tradeRef: 'M-BUY-002',
    isin: 'US0378331005',
    settlementDate: '2026-06-03',
    quantity: 50000,
    amount: 998750.25,
    currency: 'USD',
    submitterBic: 'HSBCHKHKXXX',
    counterpartyBic: 'GOLDUS33XXX',
    direction: 'BUY',
    matchingStatus: 'ALLEGED',
    matchedWithId: null,
    createdAt: now
  }
]
