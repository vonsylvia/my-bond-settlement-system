package com.settlement.jms;

import com.settlement.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends SWIFT MT messages to IBM MQ send queue for delivery to SWIFT Alliance Gateway.
 */
@Component
public class SwiftMessageSender {

    private static final Logger log = LoggerFactory.getLogger(SwiftMessageSender.class);

    private final JmsTemplate jmsTemplate;

    public SwiftMessageSender(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void sendSwiftMessage(String tradeRef, String mt541Message) {
        try {
            jmsTemplate.send(session -> {
                var textMessage = session.createTextMessage(mt541Message);
                textMessage.setJMSCorrelationID(tradeRef);
                textMessage.setStringProperty("MessageType", "MT541");
                textMessage.setStringProperty("TradeRef", tradeRef);
                return textMessage;
            });
            log.info("MT541 message sent to SWIFT queue: tradeRef={}", tradeRef);
        } catch (Exception e) {
            log.error("Failed to send MT541 message to MQ: tradeRef={}", tradeRef, e);
            throw new BusinessException("Failed to send SWIFT message: " + e.getMessage(), e);
        }
    }
}
