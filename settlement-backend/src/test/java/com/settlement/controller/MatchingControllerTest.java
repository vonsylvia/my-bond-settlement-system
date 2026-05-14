package com.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.dto.PageResponse;
import com.settlement.entity.Direction;
import com.settlement.entity.MatchingInstruction;
import com.settlement.entity.MatchingStatus;
import com.settlement.service.MatchingEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MatchingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MatchingEngineService matchingService;

    @InjectMocks
    private MatchingController matchingController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(matchingController).build();
        new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void listAll_noStatusParam_shouldReturnPagedResult() throws Exception {
        List<MatchingInstruction> items = List.of(
            buildInstruction(4L, MatchingStatus.UNMATCHED),
            buildInstruction(3L, MatchingStatus.ALLEGED),
            buildInstruction(2L, MatchingStatus.MATCHED),
            buildInstruction(1L, MatchingStatus.CANCELLED)
        );
        when(matchingService.listInstructions(isNull(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(items, 0, 20, 4L));

        mockMvc.perform(get("/api/matching"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void listAll_noStatusParam_shouldIncludeCancelledInstructions() throws Exception {
        List<MatchingInstruction> items = List.of(buildInstruction(1L, MatchingStatus.CANCELLED));
        when(matchingService.listInstructions(isNull(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(items, 0, 20, 1L));

        mockMvc.perform(get("/api/matching"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].matchingStatus").value("CANCELLED"));
    }

    @Test
    void listAll_withStatusFilter_shouldPassStatusToService() throws Exception {
        List<MatchingInstruction> items = List.of(buildInstruction(1L, MatchingStatus.UNMATCHED));
        when(matchingService.listInstructions(eq(MatchingStatus.UNMATCHED), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(items, 0, 20, 1L));

        mockMvc.perform(get("/api/matching").param("status", "UNMATCHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].matchingStatus").value("UNMATCHED"));
    }

    @Test
    void listAll_secondPage_shouldPassCorrectPageToService() throws Exception {
        when(matchingService.listInstructions(isNull(), eq(1), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 1, 20, 25L));

        mockMvc.perform(get("/api/matching").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    private MatchingInstruction buildInstruction(Long id, MatchingStatus status) {
        MatchingInstruction mi = new MatchingInstruction();
        mi.setId(id);
        mi.setTradeRef("TR-" + id);
        mi.setIsin("US0378331005");
        mi.setDirection(Direction.BUY);
        mi.setQuantity(new BigDecimal("1000000.00"));
        mi.setSettlementDate(LocalDate.of(2026, 5, 15));
        mi.setSubmitterBic("HSBCHKHHXXX");
        mi.setCounterpartyBic("GOLDUS33XXX");
        mi.setMatchingStatus(status);
        return mi;
    }
}
