package com.settlement.controller;

import com.settlement.dao.SwiftMessageDao;
import com.settlement.entity.MessageDirection;
import com.settlement.entity.MessageStandard;
import com.settlement.entity.SwiftMessage;
import com.settlement.exception.GlobalExceptionHandler;
import com.settlement.reconcile.PositionReconciliationService;
import com.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MessagesEndpointTest {

    private MockMvc mockMvc;

    @Mock
    private SettlementService settlementService;

    @Mock
    private PositionReconciliationService positionReconciliationService;

    @Mock
    private SwiftMessageDao swiftMessageDao;

    @InjectMocks
    private SettlementController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMessages_shouldReturn200_withMessages() throws Exception {
        SwiftMessage outbound = new SwiftMessage(
                1L, "TR-TEST001", MessageStandard.MT, "MT541",
                MessageDirection.OUTBOUND, "{1:F01...}");
        outbound.setSequenceNo(1);

        SwiftMessage inbound = new SwiftMessage(
                1L, "TR-TEST001", MessageStandard.MT, "MT548",
                MessageDirection.INBOUND, "{1:F01...response}");
        inbound.setSequenceNo(1);
        inbound.setParsedStatus("MATC");

        when(swiftMessageDao.findByTradeRef("TR-TEST001")).thenReturn(List.of(outbound, inbound));

        mockMvc.perform(get("/api/settlement/TR-TEST001/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].messageType").value("MT541"))
                .andExpect(jsonPath("$[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$[0].messageStandard").value("MT"))
                .andExpect(jsonPath("$[1].messageType").value("MT548"))
                .andExpect(jsonPath("$[1].direction").value("INBOUND"))
                .andExpect(jsonPath("$[1].parsedStatus").value("MATC"));
    }

    @Test
    void getMessages_shouldReturn200_withEmptyList_whenNoMessages() throws Exception {
        when(swiftMessageDao.findByTradeRef("TR-EMPTY")).thenReturn(List.of());

        mockMvc.perform(get("/api/settlement/TR-EMPTY/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getMessages_shouldReturnTranslatedMessages() throws Exception {
        SwiftMessage primary = new SwiftMessage(
                1L, "TR-DUAL001", MessageStandard.MT, "MT541",
                MessageDirection.OUTBOUND, "{1:F01...}");
        primary.setTranslated(false);

        SwiftMessage translated = new SwiftMessage(
                1L, "TR-DUAL001", MessageStandard.MX, "sese.023.001.09",
                MessageDirection.OUTBOUND, "<xml>...</xml>");
        translated.setTranslated(true);

        when(swiftMessageDao.findByTradeRef("TR-DUAL001")).thenReturn(List.of(primary, translated));

        mockMvc.perform(get("/api/settlement/TR-DUAL001/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].translated").value(false))
                .andExpect(jsonPath("$[0].messageStandard").value("MT"))
                .andExpect(jsonPath("$[1].translated").value(true))
                .andExpect(jsonPath("$[1].messageStandard").value("MX"));
    }
}
