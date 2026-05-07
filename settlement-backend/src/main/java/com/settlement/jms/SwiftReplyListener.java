package com.settlement.jms;

import com.settlement.reconcile.ReconciliationService;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring-managed JMS listener for SWIFT MT548 replies from IBM MQ.
 * Replaces the MDB approach due to Liberty's internal messaging engine
 * intercepting external RA-based MDB activations.
 */
public class SwiftReplyListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SwiftReplyListener.class);

    private final ReconciliationService reconciliationService;

    public SwiftReplyListener(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (!(message instanceof TextMessage textMessage)) {
                log.warn("Received non-text message, ignoring: {}", message.getJMSMessageID());
                return;
            }

            String correlationId = textMessage.getJMSCorrelationID();
            String messageBody = textMessage.getText();
            String messageType = textMessage.getStringProperty("MessageType");

            log.info("Received SWIFT reply: correlationId={}, type={}", correlationId, messageType);

            reconciliationService.processSwiftReply(correlationId, messageBody);

        } catch (JMSException e) {
            log.error("Error reading JMS message", e);
            throw new RuntimeException("JMS message processing failed", e);
        } catch (Exception e) {
            log.error("Unexpected error processing SWIFT reply", e);
            throw new RuntimeException("SWIFT reply processing failed", e);
        }
    }
}
