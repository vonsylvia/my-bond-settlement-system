import AxiosMockAdapter from 'axios-mock-adapter'
import {
  counterparties as initialCounterparties,
  holdings as initialHoldings,
  matchingInstructions as initialMatchingInstructions,
  settlements as initialSettlements
} from './fixtures.js'

const state = {
  counterparties: structuredClone(initialCounterparties),
  holdings: structuredClone(initialHoldings),
  matchingInstructions: structuredClone(initialMatchingInstructions),
  settlements: structuredClone(initialSettlements),
  messages: {}
}

for (const settlement of state.settlements) {
  state.messages[settlement.tradeRef] = buildMessages(settlement)
}

export function installMockApi(api) {
  const mock = new AxiosMockAdapter(api, { delayResponse: 250 })

  mock.onGet('/settlement').reply(config => {
    const page = Number(config.params?.page ?? 0)
    const size = Number(config.params?.size ?? 20)
    return ok(pageResult(state.settlements, page, size))
  })

  mock.onPost('/settlement').reply(config => {
    const payload = parseBody(config.data)
    const tradeRef = nextTradeRef()
    const counterparty = findCounterparty(payload.bicCode)
    const resolvedStandard = counterparty?.resolvedOutbound || payload.preferredStandard || 'MT'
    const settlement = {
      ...payload,
      tradeRef,
      status: 'SENT',
      resolvedStandard,
      requestedStandard: payload.preferredStandard || 'MT',
      isFinal: false,
      createdAt: new Date().toISOString()
    }

    state.settlements.unshift(settlement)
    state.messages[tradeRef] = buildMessages(settlement)
    return [202, settlement]
  })

  mock.onGet(/\/settlement\/[^/]+\/messages$/).reply(config => {
    const tradeRef = decodeURIComponent(config.url.split('/')[2])
    return ok(state.messages[tradeRef] || [])
  })

  mock.onGet(/\/settlement\/[^/]+$/).reply(config => {
    const tradeRef = decodeURIComponent(config.url.split('/')[2])
    const settlement = state.settlements.find(item => item.tradeRef === tradeRef)
    return settlement ? ok(settlement) : notFound(`Settlement ${tradeRef} not found`)
  })

  mock.onPost(/\/settlement\/[^/]+\/retry$/).reply(config => {
    const tradeRef = decodeURIComponent(config.url.split('/')[2])
    const settlement = state.settlements.find(item => item.tradeRef === tradeRef)
    if (!settlement) return notFound(`Settlement ${tradeRef} not found`)
    settlement.status = 'SENT'
    settlement.failureReason = null
    settlement.updatedAt = new Date().toISOString()
    return ok(settlement)
  })

  mock.onGet('/holdings').reply(config => {
    const accountId = config.params?.accountId
    const content = accountId
      ? state.holdings.filter(item => item.accountId === accountId)
      : state.holdings
    return ok(content)
  })

  mock.onGet('/counterparty').reply(() => {
    return ok(activeCounterparties())
  })

  mock.onGet(/\/counterparty\/[^/]+$/).reply(config => {
    const bicCode = decodeURIComponent(config.url.split('/')[2])
    const counterparty = findCounterparty(bicCode)
    return counterparty ? ok(counterparty) : notFound(`Counterparty ${bicCode} not registered`)
  })

  mock.onPost('/counterparty').reply(config => {
    const payload = normalizeCounterparty(parseBody(config.data))
    if (findCounterparty(payload.bicCode)) {
      return badRequest(`Counterparty ${payload.bicCode} already exists`)
    }
    state.counterparties.unshift(payload)
    return [201, payload]
  })

  mock.onPut(/\/counterparty\/[^/]+$/).reply(config => {
    const bicCode = decodeURIComponent(config.url.split('/')[2]).toUpperCase()
    const index = state.counterparties.findIndex(item => item.bicCode === bicCode)
    if (index === -1) return notFound(`Counterparty ${bicCode} not found`)
    state.counterparties[index] = normalizeCounterparty({
      ...state.counterparties[index],
      ...parseBody(config.data),
      bicCode
    })
    return ok(state.counterparties[index])
  })

  mock.onDelete(/\/counterparty\/[^/]+$/).reply(config => {
    const bicCode = decodeURIComponent(config.url.split('/')[2]).toUpperCase()
    const counterparty = state.counterparties.find(item => item.bicCode === bicCode)
    if (!counterparty) return notFound(`Counterparty ${bicCode} not found`)
    counterparty.active = false
    return [204]
  })

  mock.onGet('/matching').reply(config => {
    const page = Number(config.params?.page ?? 0)
    const size = Number(config.params?.size ?? 20)
    const status = config.params?.status
    const rows = status
      ? state.matchingInstructions.filter(item => item.matchingStatus === status)
      : state.matchingInstructions
    return ok(pageResult(rows, page, size))
  })

  mock.onPost('/matching').reply(config => {
    const instruction = {
      ...parseBody(config.data),
      id: nextMatchingId(),
      matchingStatus: 'ALLEGED',
      matchedWithId: null,
      createdAt: new Date().toISOString()
    }
    const match = findMatchingPair(instruction)
    if (match) {
      instruction.matchingStatus = 'MATCHED'
      instruction.matchedWithId = match.id
      match.matchingStatus = 'MATCHED'
      match.matchedWithId = instruction.id
    }
    state.matchingInstructions.unshift(instruction)
    return [201, instruction]
  })

  mock.onPost(/\/matching\/\d+\/retry$/).reply(config => {
    const id = Number(config.url.split('/')[2])
    const instruction = state.matchingInstructions.find(item => item.id === id)
    if (!instruction) return notFound(`Matching instruction ${id} not found`)
    const match = findMatchingPair(instruction)
    if (match) {
      instruction.matchingStatus = 'MATCHED'
      instruction.matchedWithId = match.id
      match.matchingStatus = 'MATCHED'
      match.matchedWithId = instruction.id
      return ok({ matched: true, matchedWithId: match.id })
    }
    instruction.matchingStatus = 'UNMATCHED'
    return ok({ matched: false })
  })

  mock.onPost(/\/matching\/\d+\/cancel$/).reply(config => {
    const id = Number(config.url.split('/')[2])
    const instruction = state.matchingInstructions.find(item => item.id === id)
    if (!instruction) return notFound(`Matching instruction ${id} not found`)
    instruction.matchingStatus = 'CANCELLED'
    return ok(instruction)
  })

  mock.onGet('/matching/alleged').reply(() => ok(state.matchingInstructions.filter(item => item.matchingStatus === 'ALLEGED')))
  mock.onGet('/matching/unmatched').reply(() => ok(state.matchingInstructions.filter(item => item.matchingStatus === 'UNMATCHED')))

  mock.onAny().reply(config => {
    console.warn(`[mock-api] Unhandled ${config.method?.toUpperCase()} ${config.url}`)
    return [404, { message: `No mock handler for ${config.method?.toUpperCase()} ${config.url}` }]
  })
}

function parseBody(data) {
  if (!data) return {}
  return typeof data === 'string' ? JSON.parse(data) : data
}

function ok(data) {
  return [200, data]
}

function badRequest(message) {
  return [400, { message, errors: [message] }]
}

function notFound(message) {
  return [404, { message }]
}

function pageResult(rows, page, size) {
  const start = page * size
  const content = rows.slice(start, start + size)
  return {
    content,
    page,
    size,
    totalElements: rows.length,
    totalPages: Math.max(1, Math.ceil(rows.length / size))
  }
}

function activeCounterparties() {
  return state.counterparties.filter(item => item.active !== false)
}

function findCounterparty(bicCode) {
  return activeCounterparties().find(item => item.bicCode === bicCode?.toUpperCase())
}

function normalizeCounterparty(data) {
  const supportedStandard = data.supportedStandard || 'DUAL'
  const preferredStandard = data.preferredStandard || 'MT'
  return {
    bicCode: data.bicCode.toUpperCase(),
    participantName: data.participantName,
    supportedStandard,
    preferredStandard,
    resolvedOutbound: supportedStandard === 'MX_ONLY' ? 'MX' : supportedStandard === 'MT_ONLY' ? 'MT' : preferredStandard,
    effectiveDate: data.effectiveDate || new Date().toISOString().slice(0, 10),
    active: data.active !== false
  }
}

function nextTradeRef() {
  const suffix = String(state.settlements.length + 1).padStart(3, '0')
  return `TR-MOCK-${suffix}`
}

function nextMatchingId() {
  return Math.max(0, ...state.matchingInstructions.map(item => item.id)) + 1
}

function findMatchingPair(instruction) {
  return state.matchingInstructions.find(item => {
    const opposite = instruction.direction === 'BUY' ? 'SELL' : 'BUY'
    const amountDelta = Math.abs(Number(item.amount || 0) - Number(instruction.amount || 0))
    return item.matchingStatus !== 'CANCELLED' &&
      item.matchingStatus !== 'MATCHED' &&
      item.direction === opposite &&
      item.isin === instruction.isin &&
      item.settlementDate === instruction.settlementDate &&
      Number(item.quantity) === Number(instruction.quantity) &&
      item.currency === instruction.currency &&
      item.submitterBic === instruction.counterpartyBic &&
      item.counterpartyBic === instruction.submitterBic &&
      amountDelta <= 1
  })
}

function buildMessages(settlement) {
  const resolvedStandard = settlement.resolvedStandard || settlement.requestedStandard || 'MT'
  const mtInstructionType = settlement.direction === 'SELL'
    ? settlement.paymentType === 'FREE_OF_PAYMENT' ? 'MT542' : 'MT543'
    : settlement.paymentType === 'FREE_OF_PAYMENT' ? 'MT540' : 'MT541'
  const translatedFromMx = resolvedStandard === 'MX'
  const translatedFromMt = resolvedStandard === 'MT'

  return [
    {
      id: `${settlement.tradeRef}-mt-out`,
      tradeRef: settlement.tradeRef,
      messageStandard: 'MT',
      messageType: mtInstructionType,
      direction: 'OUTBOUND',
      rawPayload: buildMtInstruction(settlement, mtInstructionType),
      translated: translatedFromMx,
      createdAt: settlement.createdAt
    },
    {
      id: `${settlement.tradeRef}-mt-in`,
      tradeRef: settlement.tradeRef,
      messageStandard: 'MT',
      messageType: 'MT548',
      direction: 'INBOUND',
      rawPayload: buildMtStatusAdvice(settlement),
      parsedStatus: settlement.status,
      translated: translatedFromMx,
      createdAt: settlement.createdAt
    },
    {
      id: `${settlement.tradeRef}-mx-out`,
      tradeRef: settlement.tradeRef,
      messageStandard: 'MX',
      messageType: 'sese.023.001.09',
      direction: 'OUTBOUND',
      rawPayload: buildMxInstruction(settlement),
      translated: translatedFromMt,
      createdAt: settlement.createdAt
    },
    {
      id: `${settlement.tradeRef}-mx-in`,
      tradeRef: settlement.tradeRef,
      messageStandard: 'MX',
      messageType: 'sese.024.001.10',
      direction: 'INBOUND',
      rawPayload: buildMxStatusAdvice(settlement),
      parsedStatus: settlement.status,
      translated: translatedFromMt,
      createdAt: settlement.createdAt
    }
  ]
}

function buildMtInstruction(settlement, messageType) {
  const settleDate = compactDate(settlement.settlementDate)
  const tradeDate = compactDate(settlement.createdAt)
  const indicator = settlement.direction === 'SELL' ? 'DELI' : 'RECE'
  const payment = settlement.paymentType === 'FREE_OF_PAYMENT' ? 'FREE' : 'APMT'
  const amount = formatSwiftAmount(settlement.settlementAmount || 0)
  return `{1:F01BANKHKH0AXXX0000000000}{2:I${messageType.slice(2)}${settlement.bicCode}N}{4:
:16R:GENL
:20C::SEME//${settlement.tradeRef}
:23G:NEWM
:98A::PREP//${tradeDate}
:16S:GENL
:16R:TRADDET
:98A::TRAD//${tradeDate}
:98A::SETT//${settleDate}
:35B:ISIN ${settlement.isin}
/HKGB/GOVERNMENT BOND MOCK ISSUE
:16S:TRADDET
:16R:FIAC
:36B::SETT//UNIT/${Number(settlement.quantity).toFixed(2).replace('.', ',')}
:97A::SAFE//${settlement.accountId}
:16S:FIAC
:16R:SETDET
:22F::SETR//TRAD
:22F::STCO//${payment}
:22H::REDE//${indicator}
:16R:SETPRTY
:95P::PSET//XHKCHKH1
:16S:SETPRTY
:16R:SETPRTY
:95P::DEAG//${settlement.bicCode}
:16S:SETPRTY
:16R:AMT
:19A::SETT//${settlement.currency}${amount}
:16S:AMT
:16S:SETDET
-}`
}

function buildMtStatusAdvice(settlement) {
  const statusCode = mtStatusCode(settlement.status)
  const reason = settlement.failureReason || `Mock lifecycle status ${settlement.status}`
  return `{1:F01${settlement.bicCode}0000000000}{2:O5480930${compactDate(settlement.createdAt)}BANKHKH0AXXX0000000000${compactDate(settlement.createdAt)}0930N}{4:
:16R:GENL
:20C::SEME//STAT-${settlement.tradeRef}
:20C::RELA//${settlement.tradeRef}
:23G:INST
:98A::PREP//${compactDate(settlement.createdAt)}
:16S:GENL
:16R:STAT
:25D::SETT//${statusCode}
:16R:REAS
:24B::NAFI//${reason.slice(0, 70)}
:16S:REAS
:16S:STAT
-}`
}

function buildMxInstruction(settlement) {
  const settleDate = isoDate(settlement.settlementDate)
  const tradeDate = isoDate(settlement.createdAt)
  const movement = settlement.direction === 'SELL' ? 'DELI' : 'RECE'
  const payment = settlement.paymentType === 'FREE_OF_PAYMENT' ? 'FREE' : 'APMT'
  return `<Document xmlns="urn:iso:std:iso:20022:tech:xsd:sese.023.001.09">
  <SctiesSttlmTxInstr>
    <TxId>
      <AcctOwnrTxId>${escapeXml(settlement.tradeRef)}</AcctOwnrTxId>
    </TxId>
    <TradDt>
      <Dt>${tradeDate}</Dt>
    </TradDt>
    <SttlmDt>
      <Dt>${settleDate}</Dt>
    </SttlmDt>
    <SctiesMvmntTp>${movement}</SctiesMvmntTp>
    <Pmt>${payment}</Pmt>
    <FinInstrmId>
      <ISIN>${escapeXml(settlement.isin)}</ISIN>
      <Desc>Government bond mock issue</Desc>
    </FinInstrmId>
    <QtyAndAcctDtls>
      <SttlmQty>
        <Unit>${Number(settlement.quantity).toFixed(2)}</Unit>
      </SttlmQty>
      <SfkpgAcct>
        <Id>${escapeXml(settlement.accountId)}</Id>
      </SfkpgAcct>
    </QtyAndAcctDtls>
    <SttlmParams>
      <SctiesTxTp>TRAD</SctiesTxTp>
      <PrtlSttlmInd>false</PrtlSttlmInd>
    </SttlmParams>
    <DlvrgSttlmPties>
      <Dpstry>
        <Id>
          <AnyBIC>XHKCHKH1</AnyBIC>
        </Id>
      </Dpstry>
    </DlvrgSttlmPties>
    <RcvgSttlmPties>
      <Pty1>
        <Id>
          <AnyBIC>${escapeXml(settlement.bicCode)}</AnyBIC>
        </Id>
      </Pty1>
    </RcvgSttlmPties>
    <SttlmAmt Ccy="${escapeXml(settlement.currency)}">${Number(settlement.settlementAmount || 0).toFixed(2)}</SttlmAmt>
  </SctiesSttlmTxInstr>
</Document>`
}

function buildMxStatusAdvice(settlement) {
  return `<Document xmlns="urn:iso:std:iso:20022:tech:xsd:sese.024.001.10">
  <SctiesSttlmTxStsAdvc>
    <TxId>
      <AcctOwnrTxId>${escapeXml(settlement.tradeRef)}</AcctOwnrTxId>
    </TxId>
    <PrcgSts>
      <AckdAccptd>
        <NoSpcfdRsn>NORE</NoSpcfdRsn>
      </AckdAccptd>
    </PrcgSts>
    <SttlmSts>
      <Sts>
        <Cd>${mxStatusCode(settlement.status)}</Cd>
      </Sts>
      <Rsn>
        <Cd>${settlement.status === 'FAILED' ? 'LACK' : 'NARR'}</Cd>
      </Rsn>
    </SttlmSts>
    <SplmtryData>
      <PlcAndNm>mock-status-narrative</PlcAndNm>
      <Envlp>
        <Narrative>${escapeXml(settlement.failureReason || `Mock lifecycle status: ${settlement.status}`)}</Narrative>
      </Envlp>
    </SplmtryData>
  </SctiesSttlmTxStsAdvc>
</Document>`
}

function compactDate(value) {
  return isoDate(value).replaceAll('-', '')
}

function isoDate(value) {
  if (!value) return new Date().toISOString().slice(0, 10)
  return String(value).slice(0, 10)
}

function formatSwiftAmount(value) {
  return Number(value).toFixed(2).replace('.', ',')
}

function mtStatusCode(status) {
  const codes = {
    PENDING: 'PEND',
    SUBMITTING: 'PEND',
    SENT: 'PENF',
    MATCHED: 'MACH',
    FAILED: 'REJT',
    RETRYING: 'PEND',
    CANCELLED: 'CAND',
    DVP_LOCKED: 'PEND'
  }
  return codes[status] || 'PEND'
}

function mxStatusCode(status) {
  const codes = {
    PENDING: 'PEND',
    SUBMITTING: 'PEND',
    SENT: 'PENF',
    MATCHED: 'SETT',
    FAILED: 'REJT',
    RETRYING: 'PEND',
    CANCELLED: 'CAND',
    DVP_LOCKED: 'PEND'
  }
  return codes[status] || 'PEND'
}

function escapeXml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;')
}
