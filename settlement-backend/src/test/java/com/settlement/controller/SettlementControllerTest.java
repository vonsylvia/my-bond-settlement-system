package com.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.BusinessException;
import com.settlement.exception.GlobalExceptionHandler;
import com.settlement.exception.ResourceNotFoundException;
import com.settlement.dto.PositionDiscrepancy;
import com.settlement.dto.ReconciliationResult;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.reconcile.PositionReconciliationService;
import com.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SettlementService settlementService;

    @Mock
    private PositionReconciliationService positionReconciliationService;

    @Mock
    private SwiftMessageDao swiftMessageDao;

    @InjectMocks
    private SettlementController controller;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createSettlement_shouldReturn202_withValidRequest() throws Exception {
        SettlementInstruction instruction = createPendingInstruction();
        when(settlementService.submitInstruction(any(SettlementRequest.class))).thenReturn(instruction);

        String requestJson = objectMapper.writeValueAsString(createValidRequest());

        mockMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tradeRef").value("TR-TEST123456"))
                .andExpect(jsonPath("$.isin").value("US0378331005"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.direction").value("BUY"));
    }

    @Test
    void createSettlement_shouldReturn400_withInvalidISIN() throws Exception {
        SettlementRequest request = createValidRequest();
        request.setIsin("INVALID");

        mockMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void createSettlement_shouldReturn400_withMissingFields() throws Exception {
        SettlementRequest request = new SettlementRequest();

        mockMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createSettlement_shouldReturn400_withInvalidBIC() throws Exception {
        SettlementRequest request = createValidRequest();
        request.setBicCode("INVALID123");

        mockMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSettlement_shouldReturn200_whenFound() throws Exception {
        SettlementInstruction instruction = createSentInstruction();
        when(settlementService.findByTradeRef("TR-TEST123456")).thenReturn(instruction);

        mockMvc.perform(get("/api/settlement/TR-TEST123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeRef").value("TR-TEST123456"))
                .andExpect(jsonPath("$.isin").value("US0378331005"));
    }

    @Test
    void getSettlement_shouldReturn404_whenNotFound() throws Exception {
        when(settlementService.findByTradeRef("TR-NONEXIST"))
                .thenThrow(new ResourceNotFoundException("Settlement instruction not found: TR-NONEXIST"));

        mockMvc.perform(get("/api/settlement/TR-NONEXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listSettlements_shouldReturnPagedResults() throws Exception {
        List<SettlementInstruction> instructions = List.of(createSentInstruction());
        when(settlementService.findAll(0, 20)).thenReturn(instructions);
        when(settlementService.count()).thenReturn(1L);

        mockMvc.perform(get("/api/settlement").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].tradeRef").value("TR-TEST123456"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void retrySettlement_shouldReturn202_whenFailed() throws Exception {
        SettlementInstruction retried = createPendingInstruction();
        when(settlementService.manualRetry("TR-FAIL001")).thenReturn(retried);

        mockMvc.perform(post("/api/settlement/TR-FAIL001/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retrySettlement_shouldReturn422_whenNotRetryable() throws Exception {
        when(settlementService.manualRetry("TR-SENT001"))
                .thenThrow(new BusinessException("Cannot retry instruction in status SENT"));

        mockMvc.perform(post("/api/settlement/TR-SENT001/retry"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cannot retry instruction in status SENT"));
    }

    @Test
    void retrySettlement_shouldReturn404_whenNotFound() throws Exception {
        when(settlementService.manualRetry("TR-NONE"))
                .thenThrow(new ResourceNotFoundException("Settlement instruction not found: TR-NONE"));

        mockMvc.perform(post("/api/settlement/TR-NONE/retry"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSettlement_shouldIncludeRetryFields_whenFailed() throws Exception {
        SettlementInstruction instruction = createFailedInstruction();
        when(settlementService.findByTradeRef("TR-FAIL002")).thenReturn(instruction);

        mockMvc.perform(get("/api/settlement/TR-FAIL002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.retryCount").value(3))
                .andExpect(jsonPath("$.failureReason").value("Connection refused"));
    }

    private SettlementRequest createValidRequest() {
        SettlementRequest request = new SettlementRequest();
        request.setIsin("US0378331005");
        request.setSettlementDate(LocalDate.of(2026, 5, 15));
        request.setQuantity(new BigDecimal("1000000.00"));
        request.setCounterparty("Goldman Sachs");
        request.setBicCode("GOLDUS33XXX");
        request.setDirection("BUY");
        request.setAccountId("ACC-001");
        return request;
    }

    private SettlementInstruction createPendingInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-TEST123456");
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        instruction.setQuantity(new BigDecimal("1000000.00"));
        instruction.setCounterparty("Goldman Sachs");
        instruction.setBicCode("GOLDUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setStatus(InstructionStatus.PENDING);
        instruction.setAccountId("ACC-001");
        return instruction;
    }

    private SettlementInstruction createSentInstruction() {
        SettlementInstruction instruction = createPendingInstruction();
        instruction.setStatus(InstructionStatus.SENT);
        return instruction;
    }

    @Test
    void reconcilePositions_shouldReturn200_whenConsistent() throws Exception {
        when(positionReconciliationService.reconcile())
                .thenReturn(new ReconciliationResult(
                        com.settlement.entity.SnapshotType.INCREMENTAL, 5, List.of()));

        mockMvc.perform(post("/api/positions/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(true))
                .andExpect(jsonPath("$.totalPositions").value(5))
                .andExpect(jsonPath("$.discrepancyCount").value(0))
                .andExpect(jsonPath("$.type").value("INCREMENTAL"))
                .andExpect(jsonPath("$.discrepancies").isEmpty());
    }

    @Test
    void reconcilePositions_shouldReturn200_withDiscrepancies() throws Exception {
        List<PositionDiscrepancy> discrepancies = List.of(
                new PositionDiscrepancy("ACC-001", "US0378331005",
                        new BigDecimal("1000000.00"), new BigDecimal("900000.00"))
        );
        when(positionReconciliationService.reconcile())
                .thenReturn(new ReconciliationResult(
                        com.settlement.entity.SnapshotType.INCREMENTAL, 5, discrepancies));

        mockMvc.perform(post("/api/positions/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(false))
                .andExpect(jsonPath("$.discrepancyCount").value(1))
                .andExpect(jsonPath("$.discrepancies[0].accountId").value("ACC-001"))
                .andExpect(jsonPath("$.discrepancies[0].difference").value(100000.00));
    }

    @Test
    void dailyClose_shouldReturn200_withSnapshot() throws Exception {
        when(positionReconciliationService.dailyClose())
                .thenReturn(new ReconciliationResult(
                        com.settlement.entity.SnapshotType.DAILY_CLOSE, 10, List.of()));

        mockMvc.perform(post("/api/positions/daily-close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(true))
                .andExpect(jsonPath("$.totalPositions").value(10))
                .andExpect(jsonPath("$.type").value("DAILY_CLOSE"));
    }

    private SettlementInstruction createFailedInstruction() {
        SettlementInstruction instruction = createPendingInstruction();
        instruction.setTradeRef("TR-FAIL002");
        instruction.setStatus(InstructionStatus.FAILED);
        instruction.setRetryCount(3);
        instruction.setFailureReason("Connection refused");
        return instruction;
    }
}
