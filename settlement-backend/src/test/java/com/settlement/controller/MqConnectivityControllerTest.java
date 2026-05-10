package com.settlement.controller;

import com.settlement.bridge.MdbMetricsHolder;
import com.settlement.reconcile.ReconciliationMetrics;
import com.settlement.service.MqMonitorService;
import jakarta.jms.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MqConnectivityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ObjectProvider<ConnectionFactory> connectionFactoryProvider;

    @Mock
    private ObjectProvider<JmsTemplate> jmsTemplateProvider;

    @Mock
    private ObjectProvider<MqMonitorService> mqMonitorServiceProvider;

    @Mock
    private MqMonitorService mqMonitorService;

    @BeforeEach
    void setUp() throws Exception {
        MqConnectivityController controller = new MqConnectivityController(
                connectionFactoryProvider, jmsTemplateProvider, mqMonitorServiceProvider,
                new ReconciliationMetrics());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        resetMetricsField("totalReceived");
        resetMetricsField("totalSuccess");
        resetMetricsField("totalFailed");
        resetMetricsField("lastReceivedEpochMs");
        resetMetricsField("lastSuccessEpochMs");
        resetMetricsField("lastFailedEpochMs");
    }

    @Test
    void stats_returnsMdbMetricsAndQueueStatus() throws Exception {
        MdbMetricsHolder.recordReceived();
        MdbMetricsHolder.recordReceived();
        MdbMetricsHolder.recordSuccess();
        MdbMetricsHolder.recordFailed();

        Map<String, Object> queueStatus = new LinkedHashMap<>();
        Map<String, Object> replyQueue = new LinkedHashMap<>();
        replyQueue.put("currentDepth", 5);
        replyQueue.put("maxDepth", 10000);
        replyQueue.put("openInputCount", 3);
        replyQueue.put("openOutputCount", 1);
        queueStatus.put("SWIFT.REPLY.QUEUE", replyQueue);

        when(mqMonitorServiceProvider.getIfAvailable()).thenReturn(mqMonitorService);
        when(mqMonitorService.queryAllQueueStatus()).thenReturn(queueStatus);

        mockMvc.perform(get("/api/mq/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.mdb.totalReceived").value(2))
                .andExpect(jsonPath("$.mdb.totalSuccess").value(1))
                .andExpect(jsonPath("$.mdb.totalFailed").value(1))
                .andExpect(jsonPath("$.mdb.lastReceivedAt").isNotEmpty())
                .andExpect(jsonPath("$.queues['SWIFT.REPLY.QUEUE'].currentDepth").value(5))
                .andExpect(jsonPath("$.queues['SWIFT.REPLY.QUEUE'].maxDepth").value(10000));
    }

    @Test
    void stats_returnsError_whenMqMonitorServiceUnavailable() throws Exception {
        when(mqMonitorServiceProvider.getIfAvailable()).thenReturn(null);

        mockMvc.perform(get("/api/mq/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mdb.totalReceived").value(0))
                .andExpect(jsonPath("$.queues.error").value("MqMonitorService not available"));
    }

    @Test
    void stats_returnsMdbMetrics_withZeroCounts_initially() throws Exception {
        when(mqMonitorServiceProvider.getIfAvailable()).thenReturn(null);

        mockMvc.perform(get("/api/mq/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mdb.totalReceived").value(0))
                .andExpect(jsonPath("$.mdb.totalSuccess").value(0))
                .andExpect(jsonPath("$.mdb.totalFailed").value(0))
                .andExpect(jsonPath("$.mdb.lastReceivedAt").doesNotExist())
                .andExpect(jsonPath("$.mdb.lastSuccessAt").doesNotExist())
                .andExpect(jsonPath("$.mdb.lastFailedAt").doesNotExist());
    }

    private void resetMetricsField(String fieldName) throws Exception {
        Field field = MdbMetricsHolder.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicLong) field.get(null)).set(0);
    }
}
