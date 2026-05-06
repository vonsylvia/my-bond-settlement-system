package com.settlement.ejb;

import com.settlement.reconcile.ReconciliationService;
import jakarta.annotation.Resource;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.MessageDrivenContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;

/**
 * Message-Driven Bean that listens on the SWIFT reply queue for MT548
 * (Settlement Status and Processing Advice) messages from SWIFT Alliance Gateway.
 */
@MessageDriven(
    name = "SwiftReplyMDB",
    activationConfig = {
        @ActivationConfigProperty(
            propertyName = "destinationType",
            propertyValue = "jakarta.jms.Queue"
        ),
        @ActivationConfigProperty(
            propertyName = "destination",
            propertyValue = "jms/SwiftReplyQueue"
        ),
        @ActivationConfigProperty(
            propertyName = "acknowledgeMode",
            propertyValue = "Auto-acknowledge"
        )
    }
)
public class SwiftReplyMDB implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SwiftReplyMDB.class);

    @Resource
    private MessageDrivenContext mdbContext;

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

            ReconciliationService reconciliationService = getReconciliationService();
            if (reconciliationService == null) {
                log.error("ReconciliationService not available, rolling back message");
                mdbContext.setRollbackOnly();
                return;
            }

            reconciliationService.processSwiftReply(correlationId, messageBody);

        } catch (JMSException e) {
            log.error("Error processing JMS message", e);
            mdbContext.setRollbackOnly();
        } catch (Exception e) {
            log.error("Unexpected error in MDB processing", e);
            mdbContext.setRollbackOnly();
        }
    }

    private ReconciliationService getReconciliationService() {
        WebApplicationContext ctx = ContextLoader.getCurrentWebApplicationContext();
        if (ctx == null) {
            return null;
        }
        return ctx.getBean(ReconciliationService.class);
    }
}
