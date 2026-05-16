package com.settlement.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.controller.CounterpartyController;
import com.settlement.controller.SettlementController;
import com.settlement.dao.CounterpartyCapabilityDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.dto.CounterpartyCapabilityRequest;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.CounterpartyCapability;
import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.MessageDirection;
import com.settlement.entity.MessageStandard;
import com.settlement.entity.SettlementInstruction;
import com.settlement.entity.SupportedStandard;
import com.settlement.entity.SwiftMessage;
import com.settlement.exception.GlobalExceptionHandler;
import com.settlement.reconcile.PositionReconciliationService;
import com.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test covering the full lifecycle:
 * 1. Register counterparty
 * 2. Submit settlement instruction
 * 3. Query settlement details
 * 4. Query messages for the settlement
 * 5. List all settlements (paginated)
 */
@ExtendWith(MockitoExtension.class)
class SettlementE2ETest {

    private MockMvc settlementMvc;
    private MockMvc counterpartyMvc;
    private ObjectMapper objectMapper;

    @Mock private SettlementService settlementService;
    @Mock private PositionReconciliationService positionReconciliationService;
    @Mock private SwiftMessageDao swiftMessageDao;
    @Mock private CounterpartyCapabilityDao capabilityDao;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        SettlementController settlementController = new SettlementController(
                settlementService, positionReconciliationService, swiftMessageDao);
        settlementMvc = MockMvcBuilders.standaloneSetup(settlementController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        CounterpartyController counterpartyController = new CounterpartyController(capabilityDao);
        counterpartyMvc = MockMvcBuilders.standaloneSetup(counterpartyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void fullSettlementLifecycle() throws Exception {
        // Step 1: Register counterparty
        CounterpartyCapability cap = new CounterpartyCapability(
                "GOLDUS33XXX", "Goldman Sachs",
                SupportedStandard.DUAL, MessageStandard.MT);
        when(capabilityDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CounterpartyCapabilityRequest cpReq = new CounterpartyCapabilityRequest();
        cpReq.setBicCode("GOLDUS33XXX");
        cpReq.setParticipantName("Goldman Sachs");
        cpReq.setSupportedStandard("DUAL");
        cpReq.setPreferredStandard("MT");

        counterpartyMvc.perform(post("/api/counterparty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cpReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bicCode").value("GOLDUS33XXX"));

        // Step 2: Submit settlement instruction
        SettlementInstruction mockInstruction = new SettlementInstruction();
        mockInstruction.setTradeRef("TR-E2E001");
        mockInstruction.setIsin("US0378331005");
        mockInstruction.setSettlementDate(LocalDate.now().plusDays(1));
        mockInstruction.setQuantity(new BigDecimal("1000000.00"));
        mockInstruction.setCounterparty("Goldman Sachs");
        mockInstruction.setBicCode("GOLDUS33XXX");
        mockInstruction.setDirection(Direction.BUY);
        mockInstruction.setStatus(InstructionStatus.PENDING);
        mockInstruction.setAccountId("ACC-001");
        mockInstruction.setCurrency("HKD");
        mockInstruction.setPaymentType("AGAINST_PAYMENT");
        mockInstruction.setPreferredStandard(MessageStandard.MT);

        when(settlementService.submitInstruction(any(SettlementRequest.class)))
                .thenReturn(mockInstruction);

        SettlementRequest settleReq = new SettlementRequest();
        settleReq.setIsin("US0378331005");
        settleReq.setSettlementDate(LocalDate.now().plusDays(1));
        settleReq.setQuantity(new BigDecimal("1000000.00"));
        settleReq.setCounterparty("Goldman Sachs");
        settleReq.setBicCode("GOLDUS33XXX");
        settleReq.setDirection("BUY");
        settleReq.setAccountId("ACC-001");
        settleReq.setCurrency("HKD");
        settleReq.setPaymentType("AGAINST_PAYMENT");

        settlementMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settleReq)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tradeRef").value("TR-E2E001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("HKD"))
                .andExpect(jsonPath("$.paymentType").value("AGAINST_PAYMENT"))
                .andExpect(jsonPath("$.preferredStandard").value("MT"))
                .andExpect(jsonPath("$.isFinal").value(false));

        // Step 3: Query settlement details
        when(settlementService.findByTradeRef("TR-E2E001")).thenReturn(mockInstruction);

        settlementMvc.perform(get("/api/settlement/TR-E2E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeRef").value("TR-E2E001"))
                .andExpect(jsonPath("$.isin").value("US0378331005"));

        // Step 4: Query messages
        SwiftMessage msg = new SwiftMessage(
                1L, "TR-E2E001", MessageStandard.MT, "MT541",
                MessageDirection.OUTBOUND, "{1:F01...}");
        when(swiftMessageDao.findByTradeRef("TR-E2E001")).thenReturn(List.of(msg));

        settlementMvc.perform(get("/api/settlement/TR-E2E001/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageType").value("MT541"))
                .andExpect(jsonPath("$[0].direction").value("OUTBOUND"));

        // Step 5: List all settlements
        when(settlementService.findAll(0, 20)).thenReturn(List.of(mockInstruction));
        when(settlementService.count()).thenReturn(1L);

        settlementMvc.perform(get("/api/settlement").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tradeRef").value("TR-E2E001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void settlementWithMultiCurrency_shouldAcceptUSD() throws Exception {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-USD001");
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.now().plusDays(1));
        instruction.setQuantity(new BigDecimal("500000.00"));
        instruction.setCounterparty("Citibank");
        instruction.setBicCode("CITIUS33XXX");
        instruction.setDirection(Direction.SELL);
        instruction.setStatus(InstructionStatus.PENDING);
        instruction.setAccountId("ACC-USD");
        instruction.setCurrency("USD");
        instruction.setPaymentType("AGAINST_PAYMENT");
        instruction.setSettlementAmount(new BigDecimal("250000.00"));
        instruction.setPreferredStandard(MessageStandard.MX);

        when(settlementService.submitInstruction(any())).thenReturn(instruction);

        SettlementRequest req = new SettlementRequest();
        req.setIsin("US0378331005");
        req.setSettlementDate(LocalDate.now().plusDays(1));
        req.setQuantity(new BigDecimal("500000.00"));
        req.setCounterparty("Citibank");
        req.setBicCode("CITIUS33XXX");
        req.setDirection("SELL");
        req.setAccountId("ACC-USD");
        req.setCurrency("USD");
        req.setPaymentType("AGAINST_PAYMENT");
        req.setSettlementAmount(new BigDecimal("250000.00"));
        req.setPreferredStandard("MX");

        settlementMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.settlementAmount").value(250000.00))
                .andExpect(jsonPath("$.preferredStandard").value("MX"));
    }

    @Test
    void settlementWithFinalizedInstruction_shouldShowFinalityFields() throws Exception {
        SettlementInstruction finalized = new SettlementInstruction();
        finalized.setTradeRef("TR-FINAL001");
        finalized.setIsin("US0378331005");
        finalized.setSettlementDate(LocalDate.now().plusDays(1));
        finalized.setQuantity(new BigDecimal("1000000.00"));
        finalized.setCounterparty("Goldman Sachs");
        finalized.setBicCode("GOLDUS33XXX");
        finalized.setDirection(Direction.BUY);
        finalized.setStatus(InstructionStatus.MATCHED);
        finalized.setAccountId("ACC-001");
        finalized.setCurrency("HKD");
        finalized.setPaymentType("AGAINST_PAYMENT");
        finalized.setPreferredStandard(MessageStandard.MT);
        finalized.setFinal(true);
        finalized.setFinalityTimestamp(java.time.LocalDateTime.of(2026, 5, 15, 14, 30, 0));

        when(settlementService.findByTradeRef("TR-FINAL001")).thenReturn(finalized);

        settlementMvc.perform(get("/api/settlement/TR-FINAL001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.isFinal").value(true))
                .andExpect(jsonPath("$.finalityTimestamp").exists());
    }
}
