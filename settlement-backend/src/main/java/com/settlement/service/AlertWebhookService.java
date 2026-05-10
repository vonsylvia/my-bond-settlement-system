package com.settlement.service;

import com.settlement.entity.AlertEvent;
import com.settlement.entity.SettlementInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;

@Service
public class AlertWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhookService.class);

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Value("${settlement.alert.webhook.url:}")
    private String webhookUrl;

    @Value("${settlement.alert.webhook.enabled:false}")
    private boolean webhookEnabled;

    private final Executor alertExecutor;
    private final HttpClient httpClient;

    public AlertWebhookService(@Qualifier("alertExecutor") Executor alertExecutor) {
        this.alertExecutor = alertExecutor;
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    }

    public void sendExhaustedAlert(SettlementInstruction instruction) {
        alertExecutor.execute(() -> doSendAlert(AlertEvent.SETTLEMENT_EXHAUSTED,
                buildExhaustedPayload(instruction)));
    }

    public void sendUnknownStatusAlert(String tradeRef, String isin) {
        alertExecutor.execute(() -> doSendAlert(AlertEvent.SETTLEMENT_STATUS_UNKNOWN,
                buildUnknownStatusPayload(tradeRef, isin)));
    }

    private void doSendAlert(AlertEvent alertEvent, String payload) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Webhook alerting disabled, skipping {} alert", alertEvent.getEvent());
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Webhook alert sent: event={}, severity={}, httpStatus={}",
                        alertEvent.getEvent(), alertEvent.getSeverity(), response.statusCode());
            } else {
                log.warn("Webhook alert non-success: event={}, httpStatus={}, body={}",
                        alertEvent.getEvent(), response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send webhook alert: event={}", alertEvent.getEvent(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String buildExhaustedPayload(SettlementInstruction instruction) {
        return String.format("""
                {
                  "event": "%s",
                  "severity": "%s",
                  "timestamp": "%s",
                  "tradeRef": "%s",
                  "isin": "%s",
                  "direction": "%s",
                  "accountId": "%s",
                  "retryCount": %d,
                  "lastFailureReason": "%s",
                  "message": "Settlement instruction has exhausted all retry attempts and requires manual intervention."
                }""",
                AlertEvent.SETTLEMENT_EXHAUSTED.getEvent(),
                AlertEvent.SETTLEMENT_EXHAUSTED.getSeverity(),
                LocalDateTime.now(),
                instruction.getTradeRef(),
                instruction.getIsin(),
                instruction.getDirection(),
                instruction.getAccountId(),
                instruction.getRetryCount(),
                escapeJson(instruction.getFailureReason()));
    }

    private String buildUnknownStatusPayload(String tradeRef, String isin) {
        return String.format("""
                {
                  "event": "%s",
                  "severity": "%s",
                  "timestamp": "%s",
                  "tradeRef": "%s",
                  "isin": "%s",
                  "message": "MT548 reply could not be parsed — settlement status unknown, requires manual review."
                }""",
                AlertEvent.SETTLEMENT_STATUS_UNKNOWN.getEvent(),
                AlertEvent.SETTLEMENT_STATUS_UNKNOWN.getSeverity(),
                LocalDateTime.now(),
                tradeRef,
                isin != null ? isin : "");
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
