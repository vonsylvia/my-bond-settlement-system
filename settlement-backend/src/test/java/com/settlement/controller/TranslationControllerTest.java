package com.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.PartyInfo;
import com.settlement.canonical.PaymentType;
import com.settlement.canonical.SettlementDirection;
import com.settlement.dto.TranslationRequest;
import com.settlement.entity.MessageStandard;
import com.settlement.exception.GlobalExceptionHandler;
import com.settlement.translation.TranslationResult;
import com.settlement.translation.TranslationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TranslationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private TranslationController controller;

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
    void translate_shouldReturn200_withMtToMxTranslation() throws Exception {
        TranslationResult result = new TranslationResult(
                MessageStandard.MT, MessageStandard.MX,
                "MT541", "sese.023.001.09",
                "<translated-xml/>", createCanonical());

        when(translationService.translate(any(String.class), eq(MessageStandard.MX)))
                .thenReturn(result);

        TranslationRequest request = new TranslationRequest();
        request.setRawPayload("{1:F01...}");
        request.setTargetStandard("MX");

        mockMvc.perform(post("/api/translation/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceStandard").value("MT"))
                .andExpect(jsonPath("$.targetStandard").value("MX"))
                .andExpect(jsonPath("$.sourceMessageType").value("MT541"))
                .andExpect(jsonPath("$.targetMessageType").value("sese.023.001.09"))
                .andExpect(jsonPath("$.translatedPayload").value("<translated-xml/>"))
                .andExpect(jsonPath("$.canonical.transactionId").value("TR-CTRL-001"))
                .andExpect(jsonPath("$.canonical.isin").value("US0378331005"));
    }

    @Test
    void translate_autoDetect_shouldReturn200() throws Exception {
        TranslationResult result = new TranslationResult(
                MessageStandard.MX, MessageStandard.MT,
                "sese.023.001.09", "MT541",
                "{1:F01...translated...}", createCanonical());

        when(translationService.translate(any(String.class))).thenReturn(result);

        TranslationRequest request = new TranslationRequest();
        request.setRawPayload("<xml>...</xml>");

        mockMvc.perform(post("/api/translation/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceStandard").value("MX"))
                .andExpect(jsonPath("$.targetStandard").value("MT"));
    }

    @Test
    void translate_shouldReturn400_withBlankPayload() throws Exception {
        TranslationRequest request = new TranslationRequest();
        request.setRawPayload("");

        mockMvc.perform(post("/api/translation/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detect_shouldReturn200_withDetectedFormat() throws Exception {
        when(translationService.detect(any(String.class)))
                .thenReturn(new TranslationService.DetectionResult(
                        MessageStandard.MT, "MT541", "TR-DETECT-001"));

        TranslationRequest request = new TranslationRequest();
        request.setRawPayload("{1:F01...}");

        mockMvc.perform(post("/api/translation/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standard").value("MT"))
                .andExpect(jsonPath("$.messageType").value("MT541"))
                .andExpect(jsonPath("$.tradeRef").value("TR-DETECT-001"));
    }

    private CanonicalSettlement createCanonical() {
        return new CanonicalSettlement(
                "TR-CTRL-001", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33"),
                "ACC-001", null, null, null);
    }
}
