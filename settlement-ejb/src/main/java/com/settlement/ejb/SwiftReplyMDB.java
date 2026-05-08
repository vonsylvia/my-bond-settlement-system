package com.settlement.ejb;

import com.settlement.bridge.ServiceRegistry;
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

import java.lang.reflect.Method;

/**
 * Message-Driven Bean that listens on the SWIFT reply queue for MT548
 * (Settlement Status and Processing Advice) messages from SWIFT Alliance Gateway.
 *
 * <p>Uses {@link ServiceRegistry} to obtain Spring-managed ReconciliationService
 * without direct Spring classloader dependency, enabling clean EAR module isolation.
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
            propertyValue = "SWIFT.REPLY.QUEUE"
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

            Object reconciliationService = ServiceRegistry.lookup("reconciliationService", Object.class);
            if (reconciliationService == null) {
                log.error("ReconciliationService not available via ServiceRegistry, rolling back message");
                mdbContext.setRollbackOnly();
                return;
            }

            invokeProcessSwiftReply(reconciliationService, correlationId, messageBody);

        } catch (JMSException e) {
            log.error("Error processing JMS message", e);
            mdbContext.setRollbackOnly();
        } catch (Exception e) {
            log.error("Unexpected error in MDB processing", e);
            mdbContext.setRollbackOnly();
        }
    }

    private void invokeProcessSwiftReply(Object reconciliationService, String correlationId, String messageBody) throws Exception {
        Method method = reconciliationService.getClass().getMethod("processSwiftReply", String.class, String.class);
        method.invoke(reconciliationService, correlationId, messageBody);
    }
}
