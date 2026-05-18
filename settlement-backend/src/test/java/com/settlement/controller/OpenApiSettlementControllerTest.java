package com.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.dto.OpenApiSettlementRequest;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.MessageStandard;
import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.BusinessException;
import com.settlement.exception.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OpenApiSettlementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SettlementService settlementService;

    @InjectMocks
    private OpenApiSettlementController controller;

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
    void submitInstruction_shouldReturn202_withAcceptedInstruction() throws Exception {
        when(settlementService.submitOpenApiInstruction(
                eq("BANKA"), eq("BANKA-20260516-0001"), any(SettlementRequest.class)))
                .thenReturn(createInstruction());

        mockMvc.perform(post("/api/open/settlement-instructions")
                        .header("X-Participant-Id", "BANKA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.instructionId").value("TR-OPEN001"))
                .andExpect(jsonPath("$.participantId").value("BANKA"))
                .andExpect(jsonPath("$.clientReference").value("BANKA-20260516-0001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.settlement.isin").value("US0378331005"));
    }

    @Test
    void submitInstruction_shouldReturn400_withoutParticipantHeader() throws Exception {
        mockMvc.perform(post("/api/open/settlement-instructions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void submitInstruction_shouldReturn400_withInvalidRequest() throws Exception {
        OpenApiSettlementRequest request = createRequest();
        request.setClientReference("");

        mockMvc.perform(post("/api/open/settlement-instructions")
                        .header("X-Participant-Id", "BANKA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void submitInstruction_shouldReturn422_forIdempotencyConflict() throws Exception {
        when(settlementService.submitOpenApiInstruction(
                eq("BANKA"), eq("BANKA-20260516-0001"), any(SettlementRequest.class)))
                .thenThrow(new BusinessException(
                        "Duplicate clientReference with different instruction payload: BANKA-20260516-0001"));

        mockMvc.perform(post("/api/open/settlement-instructions")
                        .header("X-Participant-Id", "BANKA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Duplicate clientReference with different instruction payload: BANKA-20260516-0001"));
    }

    @Test
    void getInstructionByClientReference_shouldReturn200() throws Exception {
        when(settlementService.findOpenApiInstruction("BANKA", "BANKA-20260516-0001"))
                .thenReturn(createInstruction());

        mockMvc.perform(get("/api/open/settlement-instructions")
                        .header("X-Participant-Id", "BANKA")
                        .param("clientReference", "BANKA-20260516-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructionId").value("TR-OPEN001"))
                .andExpect(jsonPath("$.clientReference").value("BANKA-20260516-0001"));
    }

    private OpenApiSettlementRequest createRequest() {
        OpenApiSettlementRequest request = new OpenApiSettlementRequest();
        request.setClientReference("BANKA-20260516-0001");
        request.setIsin("US0378331005");
        request.setSettlementDate(LocalDate.now().plusDays(1));
        request.setQuantity(new BigDecimal("1000000.00"));
        request.setCounterparty("Goldman Sachs");
        request.setBicCode("GOLDUS33XXX");
        request.setDirection("BUY");
        request.setAccountId("ACC-001");
        request.setCurrency("HKD");
        request.setSettlementAmount(new BigDecimal("998750.25"));
        request.setPaymentType("AGAINST_PAYMENT");
        request.setRequestedStandard("MT");
        return request;
    }

    private SettlementInstruction createInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-OPEN001");
        instruction.setParticipantId("BANKA");
        instruction.setClientReference("BANKA-20260516-0001");
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.now().plusDays(1));
        instruction.setQuantity(new BigDecimal("1000000.00"));
        instruction.setCounterparty("Goldman Sachs");
        instruction.setBicCode("GOLDUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setStatus(InstructionStatus.PENDING);
        instruction.setAccountId("ACC-001");
        instruction.setCurrency("HKD");
        instruction.setSettlementAmount(new BigDecimal("998750.25"));
        instruction.setPaymentType("AGAINST_PAYMENT");
        instruction.setRequestedStandard(MessageStandard.MT);
        return instruction;
    }
}
