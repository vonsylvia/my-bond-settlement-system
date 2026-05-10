package com.settlement.ejb;

import com.settlement.bridge.MdbMetricsHolder;
import com.settlement.bridge.ReconciliationHandler;
import com.settlement.bridge.ServiceNotAvailableException;
import com.settlement.bridge.ServiceRegistry;
import jakarta.annotation.Resource;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.MessageDrivenContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Message-Driven Bean that listens on the SWIFT reply queue for MT548
 * (Settlement Status and Processing Advice) messages from SWIFT Alliance Gateway.
 *
 * <p>Activation configuration (destination, transaction type) is defined in
 * {@code ejb-jar.xml}; runtime JNDI binding is in {@code ibm-ejb-jar-bnd.xml}.
 * This separation allows ops to change deployment targets without code changes.
 *
 * <p>Uses {@link ServiceRegistry} to obtain the Spring-managed
 * {@link ReconciliationHandler} via its shared interface type — no reflection
 * required. The interface is defined in settlement-common (EAR lib/) so both
 * the WAR and EJB modules share the same type at the classloader level.
 */
@MessageDriven(name = "SwiftReplyMDB")
public class SwiftReplyMDB implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SwiftReplyMDB.class);

    @Resource
    private MessageDrivenContext mdbContext;

    @Override
    public void onMessage(Message message) {
        MdbMetricsHolder.recordReceived();
        try {
            if (!(message instanceof TextMessage textMessage)) {
                log.warn("Received non-text message, ignoring: {}", message.getJMSMessageID());
                MdbMetricsHolder.recordFailed();
                return;
            }

            String correlationId = textMessage.getJMSCorrelationID();
            String messageBody = textMessage.getText();
            String messageType = textMessage.getStringProperty("MessageType");

            log.info("Received SWIFT reply: correlationId={}, type={}", correlationId, messageType);

            ReconciliationHandler handler = ServiceRegistry.lookup(ReconciliationHandler.class);
            handler.processSwiftReply(correlationId, messageBody);
            MdbMetricsHolder.recordSuccess();

        } catch (ServiceNotAvailableException e) {
            log.error("ReconciliationHandler not registered — WAR may not have started yet, "
                    + "rolling back for redelivery", e);
            MdbMetricsHolder.recordFailed();
            mdbContext.setRollbackOnly();
        } catch (JMSException e) {
            log.error("Error processing JMS message", e);
            MdbMetricsHolder.recordFailed();
            mdbContext.setRollbackOnly();
        } catch (Exception e) {
            log.error("Unexpected error in MDB processing", e);
            MdbMetricsHolder.recordFailed();
            mdbContext.setRollbackOnly();
        }
    }
}
