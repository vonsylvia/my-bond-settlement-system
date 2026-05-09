package com.settlement.service;

import com.settlement.entity.Direction;
import com.settlement.entity.SettlementInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertWebhookServiceTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockResponse;

    private AlertWebhookService service;
    private SettlementInstruction instruction;

    @BeforeEach
    void setUp() {
        service = new AlertWebhookService(Runnable::run, mockHttpClient);

        instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-ALERT001");
        instruction.setIsin("US1234567890");
        instruction.setDirection(Direction.BUY);
        instruction.setAccountId("ACC-001");
        instruction.setRetryCount(3);
        instruction.setFailureReason("MQ unavailable");
    }

    @Test
    void sendExhaustedAlert_whenWebhookDisabled_shouldSkipHttpCall() {
        ReflectionTestUtils.setField(service, "webhookEnabled", false);
        ReflectionTestUtils.setField(service, "webhookUrl", "http://example.com/webhook");

        service.sendExhaustedAlert(instruction);

        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void sendExhaustedAlert_whenUrlBlank_shouldSkipHttpCall() {
        ReflectionTestUtils.setField(service, "webhookEnabled", true);
        ReflectionTestUtils.setField(service, "webhookUrl", "");

        service.sendExhaustedAlert(instruction);

        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void sendExhaustedAlert_whenUrlNull_shouldSkipHttpCall() {
        ReflectionTestUtils.setField(service, "webhookEnabled", true);
        ReflectionTestUtils.setField(service, "webhookUrl", null);

        service.sendExhaustedAlert(instruction);

        verifyNoInteractions(mockHttpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendExhaustedAlert_whenSuccess_shouldSendHttpPost() throws Exception {
        ReflectionTestUtils.setField(service, "webhookEnabled", true);
        ReflectionTestUtils.setField(service, "webhookUrl", "http://example.com/webhook");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);

        service.sendExhaustedAlert(instruction);

        verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendExhaustedAlert_whenNonSuccessStatus_shouldNotThrow() throws Exception {
        ReflectionTestUtils.setField(service, "webhookEnabled", true);
        ReflectionTestUtils.setField(service, "webhookUrl", "http://example.com/webhook");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");

        service.sendExhaustedAlert(instruction);

        verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendExhaustedAlert_whenIOException_shouldNotPropagateException() throws Exception {
        ReflectionTestUtils.setField(service, "webhookEnabled", true);
        ReflectionTestUtils.setField(service, "webhookUrl", "http://example.com/webhook");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        service.sendExhaustedAlert(instruction);

        verify(mockHttpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
