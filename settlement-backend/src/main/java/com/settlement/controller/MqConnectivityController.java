package com.settlement.controller;

import com.settlement.bridge.MdbMetricsHolder;
import com.settlement.reconcile.ReconciliationMetrics;
import com.settlement.service.MqMonitorService;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoints for verifying IBM MQ connectivity, MDB message delivery,
 * and runtime queue/MDB monitoring.
 */
@RestController
@RequestMapping("/api/mq")
public class MqConnectivityController {

    private static final Logger log = LoggerFactory.getLogger(MqConnectivityController.class);

    private final ObjectProvider<ConnectionFactory> connectionFactoryProvider;
    private final ObjectProvider<JmsTemplate> jmsTemplateProvider;
    private final ObjectProvider<MqMonitorService> mqMonitorServiceProvider;
    private final ReconciliationMetrics reconciliationMetrics;

    public MqConnectivityController(ObjectProvider<ConnectionFactory> connectionFactoryProvider,
                                    ObjectProvider<JmsTemplate> jmsTemplateProvider,
                                    ObjectProvider<MqMonitorService> mqMonitorServiceProvider,
                                    ReconciliationMetrics reconciliationMetrics) {
        this.connectionFactoryProvider = connectionFactoryProvider;
        this.jmsTemplateProvider = jmsTemplateProvider;
        this.mqMonitorServiceProvider = mqMonitorServiceProvider;
        this.reconciliationMetrics = reconciliationMetrics;
    }

    /**
     * Tests basic MQ connection by creating and closing a JMS session.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());

        ConnectionFactory cf = connectionFactoryProvider.getIfAvailable();
        if (cf == null) {
            result.put("status", "ERROR");
            result.put("message", "ConnectionFactory not available");
            return result;
        }

        try (Connection conn = cf.createConnection();
             Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            conn.start();
            result.put("status", "OK");
            result.put("message", "MQ connection successful");
            result.put("connectionClass", conn.getClass().getSimpleName());
            result.put("jmsProviderName", conn.getMetaData().getJMSProviderName());
            result.put("providerVersion", conn.getMetaData().getProviderVersion());
        } catch (Exception e) {
            log.error("MQ health check failed", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Sends a test MT548 reply message to SWIFT.REPLY.QUEUE.
     * The SwiftReplyMDB should pick it up and process it through ReconciliationService.
     * Uses a test-prefixed correlation ID to distinguish from real messages.
     */
    @PostMapping("/test-mdb")
    public Map<String, Object> testMdb(@RequestParam(defaultValue = "TEST-MDB-001") String correlationId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("correlationId", correlationId);

        JmsTemplate jmsTemplate = jmsTemplateProvider.getIfAvailable();
        if (jmsTemplate == null) {
            result.put("status", "ERROR");
            result.put("message", "JmsTemplate not available");
            return result;
        }

        String testMt548 = buildTestMt548(correlationId);

        try {
            jmsTemplate.send("SWIFT.REPLY.QUEUE", session -> {
                var textMessage = session.createTextMessage(testMt548);
                textMessage.setJMSCorrelationID(correlationId);
                textMessage.setStringProperty("MessageType", "MT548");
                textMessage.setStringProperty("TradeRef", correlationId);
                textMessage.setStringProperty("TestMessage", "true");
                return textMessage;
            });
            result.put("status", "SENT");
            result.put("message", "Test MT548 message sent to SWIFT.REPLY.QUEUE — check MDB logs for processing");
            result.put("destination", "SWIFT.REPLY.QUEUE");
        } catch (Exception e) {
            log.error("Failed to send test MDB message", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Returns MDB processing metrics and MQ queue status for all monitored queues.
     * MDB counters are in-memory (reset on restart); queue stats are live from MQ.
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("mdb", MdbMetricsHolder.snapshot());
        result.put("reconciliation", reconciliationMetrics.snapshot());

        MqMonitorService monitorService = mqMonitorServiceProvider.getIfAvailable();
        if (monitorService != null) {
            result.put("queues", monitorService.queryAllQueueStatus());
        } else {
            Map<String, String> unavailable = new LinkedHashMap<>();
            unavailable.put("error", "MqMonitorService not available");
            result.put("queues", unavailable);
        }

        return result;
    }

    private String buildTestMt548(String tradeRef) {
        return "{1:F01TESTBIC0AXXX0000000000}" +
               "{2:O5481200000000TESTBIC0AXXX00000000000000000000N}" +
               "{4:\n" +
               ":16R:GENL\n" +
               ":20C::SEME//" + tradeRef + "\n" +
               ":23G:INST\n" +
               ":25D::MTCH//MATC\n" +
               ":16S:GENL\n" +
               "-}";
    }
}
