package com.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.settlement.dao.CounterpartyCapabilityDao;
import com.settlement.dto.CounterpartyCapabilityRequest;
import com.settlement.entity.CounterpartyCapability;
import com.settlement.entity.MessageStandard;
import com.settlement.entity.SupportedStandard;
import com.settlement.exception.GlobalExceptionHandler;
import com.settlement.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CounterpartyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CounterpartyCapabilityDao capabilityDao;

    @InjectMocks
    private CounterpartyController controller;

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
    void listAll_shouldReturn200_withCounterparties() throws Exception {
        CounterpartyCapability cap = new CounterpartyCapability(
                "GOLDUS33XXX", "Goldman Sachs",
                SupportedStandard.DUAL, MessageStandard.MT);
        when(capabilityDao.findAll()).thenReturn(List.of(cap));

        mockMvc.perform(get("/api/counterparty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bicCode").value("GOLDUS33XXX"))
                .andExpect(jsonPath("$[0].participantName").value("Goldman Sachs"))
                .andExpect(jsonPath("$[0].supportedStandard").value("DUAL"))
                .andExpect(jsonPath("$[0].preferredStandard").value("MT"))
                .andExpect(jsonPath("$[0].resolvedOutbound").value("MT"));
    }

    @Test
    void listAll_shouldReturn200_withEmptyList() throws Exception {
        when(capabilityDao.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/counterparty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getByBic_shouldReturn200_whenFound() throws Exception {
        CounterpartyCapability cap = new CounterpartyCapability(
                "HSBCHKHH", "HSBC HK",
                SupportedStandard.MX_ONLY, MessageStandard.MX);
        when(capabilityDao.findByBic("HSBCHKHH")).thenReturn(Optional.of(cap));

        mockMvc.perform(get("/api/counterparty/HSBCHKHH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bicCode").value("HSBCHKHH"))
                .andExpect(jsonPath("$.resolvedOutbound").value("MX"));
    }

    @Test
    void getByBic_shouldFallbackToFuzzy_whenExactNotFound() throws Exception {
        CounterpartyCapability cap = new CounterpartyCapability(
                "GOLDUS33XXX", "Goldman Sachs",
                SupportedStandard.DUAL, MessageStandard.MX);
        when(capabilityDao.findByBic("GOLDUS33")).thenReturn(Optional.empty());
        when(capabilityDao.findByBicFuzzy("GOLDUS33")).thenReturn(Optional.of(cap));

        mockMvc.perform(get("/api/counterparty/GOLDUS33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bicCode").value("GOLDUS33XXX"));
    }

    @Test
    void getByBic_shouldReturn404_whenNotFound() throws Exception {
        when(capabilityDao.findByBic("UNKNOWN")).thenReturn(Optional.empty());
        when(capabilityDao.findByBicFuzzy("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/counterparty/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn201_withValidRequest() throws Exception {
        CounterpartyCapabilityRequest request = new CounterpartyCapabilityRequest();
        request.setBicCode("DEUTDEFF");
        request.setParticipantName("Deutsche Bank");
        request.setSupportedStandard("DUAL");
        request.setPreferredStandard("MX");

        when(capabilityDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/counterparty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bicCode").value("DEUTDEFF"))
                .andExpect(jsonPath("$.participantName").value("Deutsche Bank"));

        ArgumentCaptor<CounterpartyCapability> capabilityCaptor =
                ArgumentCaptor.forClass(CounterpartyCapability.class);
        verify(capabilityDao).save(capabilityCaptor.capture());
        CounterpartyCapability saved = capabilityCaptor.getValue();
        assertThat(saved.getBicCode()).isEqualTo("DEUTDEFF");
        assertThat(saved.getParticipantName()).isEqualTo("Deutsche Bank");
        assertThat(saved.getSupportedStandard()).isEqualTo(SupportedStandard.DUAL);
        assertThat(saved.getPreferredStandard()).isEqualTo(MessageStandard.MX);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void update_shouldReturn200_whenExists() throws Exception {
        CounterpartyCapability existing = new CounterpartyCapability(
                "GOLDUS33XXX", "Goldman Sachs",
                SupportedStandard.MT_ONLY, MessageStandard.MT);
        when(capabilityDao.findByBic("GOLDUS33XXX")).thenReturn(Optional.of(existing));
        when(capabilityDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CounterpartyCapabilityRequest request = new CounterpartyCapabilityRequest();
        request.setBicCode("GOLDUS33XXX");
        request.setParticipantName("Goldman Sachs International");
        request.setSupportedStandard("DUAL");
        request.setPreferredStandard("MX");

        mockMvc.perform(put("/api/counterparty/GOLDUS33XXX")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantName").value("Goldman Sachs International"))
                .andExpect(jsonPath("$.supportedStandard").value("DUAL"));

        ArgumentCaptor<CounterpartyCapability> capabilityCaptor =
                ArgumentCaptor.forClass(CounterpartyCapability.class);
        verify(capabilityDao).save(capabilityCaptor.capture());
        CounterpartyCapability saved = capabilityCaptor.getValue();
        assertThat(saved.getBicCode()).isEqualTo("GOLDUS33XXX");
        assertThat(saved.getParticipantName()).isEqualTo("Goldman Sachs International");
        assertThat(saved.getSupportedStandard()).isEqualTo(SupportedStandard.DUAL);
        assertThat(saved.getPreferredStandard()).isEqualTo(MessageStandard.MX);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void update_shouldReturn404_whenNotFound() throws Exception {
        when(capabilityDao.findByBic("UNKNOWNX")).thenReturn(Optional.empty());

        CounterpartyCapabilityRequest request = new CounterpartyCapabilityRequest();
        request.setBicCode("UNKNOWNX");
        request.setParticipantName("Unknown Bank");
        request.setSupportedStandard("MT_ONLY");
        request.setPreferredStandard("MT");

        mockMvc.perform(put("/api/counterparty/UNKNOWNX")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivate_shouldReturn204_whenExists() throws Exception {
        CounterpartyCapability existing = new CounterpartyCapability(
                "GOLDUS33XXX", "Goldman Sachs",
                SupportedStandard.DUAL, MessageStandard.MT);
        when(capabilityDao.findByBic("GOLDUS33XXX")).thenReturn(Optional.of(existing));
        when(capabilityDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(delete("/api/counterparty/GOLDUS33XXX"))
                .andExpect(status().isNoContent());

        verify(capabilityDao).save(argThat(c -> !c.isActive()));
    }

    @Test
    void deactivate_shouldReturn404_whenNotFound() throws Exception {
        when(capabilityDao.findByBic("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/counterparty/UNKNOWN"))
                .andExpect(status().isNotFound());
    }
}
