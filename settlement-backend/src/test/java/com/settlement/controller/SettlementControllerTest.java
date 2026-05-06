package com.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.GlobalExceptionHandler;
import com.settlement.exception.ResourceNotFoundException;
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
import java.time.LocalDateTime;
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
    void createSettlement_shouldReturn201_withValidRequest() throws Exception {
        SettlementInstruction instruction = createMockInstruction();
        when(settlementService.submitInstruction(any(SettlementRequest.class))).thenReturn(instruction);

        String requestJson = objectMapper.writeValueAsString(createValidRequest());

        mockMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeRef").value("TR-TEST123456"))
                .andExpect(jsonPath("$.isin").value("US0378331005"))
                .andExpect(jsonPath("$.status").value("SENT"))
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
        SettlementInstruction instruction = createMockInstruction();
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
        List<SettlementInstruction> instructions = List.of(createMockInstruction());
        when(settlementService.findAll(0, 20)).thenReturn(instructions);
        when(settlementService.count()).thenReturn(1L);

        mockMvc.perform(get("/api/settlement").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].tradeRef").value("TR-TEST123456"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
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

    private SettlementInstruction createMockInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-TEST123456");
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        instruction.setQuantity(new BigDecimal("1000000.00"));
        instruction.setCounterparty("Goldman Sachs");
        instruction.setBicCode("GOLDUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setStatus(InstructionStatus.SENT);
        instruction.setAccountId("ACC-001");
        return instruction;
    }
}
