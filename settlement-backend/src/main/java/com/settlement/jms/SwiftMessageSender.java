package com.settlement.jms;

import com.settlement.entity.MessageStandard;
import com.settlement.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends SWIFT messages (MT or MX) to IBM MQ send queue for delivery
 * to SWIFT Alliance Gateway / Alliance Lite2.
 */
@Component
public class SwiftMessageSender {

    private static final Logger log = LoggerFactory.getLogger(SwiftMessageSender.class);

    private final JmsTemplate jmsTemplate;

    public SwiftMessageSender(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void sendSwiftMessage(String tradeRef, String rawPayload,
                                 String messageType, MessageStandard standard) {
        try {
            jmsTemplate.send(session -> {
                var textMessage = session.createTextMessage(rawPayload);
                textMessage.setJMSCorrelationID(tradeRef);
                textMessage.setStringProperty("MessageType", messageType);
                textMessage.setStringProperty("MessageStandard", standard.name());
                textMessage.setStringProperty("TradeRef", tradeRef);
                return textMessage;
            });
            log.info("{} message sent to SWIFT queue: tradeRef={}, standard={}",
                    messageType, tradeRef, standard);
        } catch (Exception e) {
            log.error("Failed to send {} message to MQ: tradeRef={}", messageType, tradeRef, e);
            throw new BusinessException("Failed to send SWIFT message: " + e.getMessage(), e);
        }
    }
}
