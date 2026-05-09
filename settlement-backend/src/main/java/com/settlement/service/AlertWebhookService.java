package com.settlement.service;

import com.settlement.entity.SettlementInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AlertWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhookService.class);

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Value("${settlement.alert.webhook.url:}")
    private String webhookUrl;

    @Value("${settlement.alert.webhook.enabled:false}")
    private boolean webhookEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    @Async("settlementExecutor")
    public void sendExhaustedAlert(SettlementInstruction instruction) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Webhook alerting disabled or URL not configured, skipping alert for tradeRef={}",
                    instruction.getTradeRef());
            return;
        }

        String payload = buildPayload(instruction);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Webhook alert sent successfully: tradeRef={}, status={}",
                        instruction.getTradeRef(), response.statusCode());
            } else {
                log.warn("Webhook alert returned non-success status: tradeRef={}, status={}, body={}",
                        instruction.getTradeRef(), response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send webhook alert: tradeRef={}", instruction.getTradeRef(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String buildPayload(SettlementInstruction instruction) {
        return String.format("""
                {
                  "event": "SETTLEMENT_EXHAUSTED",
                  "severity": "CRITICAL",
                  "timestamp": "%s",
                  "tradeRef": "%s",
                  "isin": "%s",
                  "direction": "%s",
                  "accountId": "%s",
                  "retryCount": %d,
                  "lastFailureReason": "%s",
                  "message": "Settlement instruction has exhausted all retry attempts and requires manual intervention."
                }""",
                LocalDateTime.now(),
                instruction.getTradeRef(),
                instruction.getIsin(),
                instruction.getDirection(),
                instruction.getAccountId(),
                instruction.getRetryCount(),
                escapeJson(instruction.getFailureReason()));
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
