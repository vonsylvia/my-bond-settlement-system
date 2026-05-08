package com.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.dto.SettlementRequest;
import com.settlement.exception.GlobalExceptionHandler;
import com.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
    "classpath:spring/test-applicationContext.xml",
    "classpath:spring/spring-mvc.xml"
})
@WebAppConfiguration
@Transactional
class SettlementControllerIT {

    private MockMvc mockMvc;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementController controller;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void fullFlow_createAndRetrieve() throws Exception {
        SettlementRequest request = new SettlementRequest();
        request.setIsin("US0378331005");
        request.setSettlementDate(LocalDate.of(2026, 6, 15));
        request.setQuantity(new BigDecimal("500000.00"));
        request.setCounterparty("JP Morgan");
        request.setBicCode("JPMOUS33XXX");
        request.setDirection("BUY");
        request.setAccountId("ACC-INT-001");

        String responseBody = mockMvc.perform(post("/api/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeRef").isNotEmpty())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn().getResponse().getContentAsString();

        String tradeRef = objectMapper.readTree(responseBody).get("tradeRef").asText();

        mockMvc.perform(get("/api/settlement/" + tradeRef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeRef").value(tradeRef))
                .andExpect(jsonPath("$.isin").value("US0378331005"))
                .andExpect(jsonPath("$.counterparty").value("JP Morgan"));
    }

    @Test
    void listSettlements_shouldReturnEmpty_initially() throws Exception {
        mockMvc.perform(get("/api/settlement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getHoldings_shouldReturnEmpty_initially() throws Exception {
        mockMvc.perform(get("/api/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
