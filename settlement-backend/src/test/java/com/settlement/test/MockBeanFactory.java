package com.settlement.test;

import com.settlement.jms.SwiftMessageSender;
import org.mockito.Mockito;
import org.springframework.jms.core.JmsTemplate;

public final class MockBeanFactory {

    private MockBeanFactory() {
    }

    public static JmsTemplate jmsTemplate() {
        return Mockito.mock(JmsTemplate.class);
    }

    public static SwiftMessageSender swiftMessageSender() {
        return Mockito.mock(SwiftMessageSender.class);
    }
}
