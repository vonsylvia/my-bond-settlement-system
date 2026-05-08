package com.settlement.config;

import jakarta.jms.ConnectionFactory;
import javax.naming.InitialContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;

/**
 * IBM MQ JMS configuration for outbound message sending.
 *
 * <p>Uses a container-managed JNDI ConnectionFactory ({@code jms/SwiftQueueCF})
 * so that JMS operations participate in the same JTA transaction as database
 * operations, ensuring atomic consistency via XA two-phase commit.
 *
 * <p>Inbound message receiving is handled by {@code SwiftReplyMDB} (Message-Driven Bean)
 * in the settlement-ejb module, activated via the container's JCA activation spec.
 */
@Configuration
public class MqClientConfig {

    private static final Logger log = LoggerFactory.getLogger(MqClientConfig.class);

    @Bean(name = "jmsConnectionFactory")
    public ConnectionFactory jmsConnectionFactory() throws Exception {
        ConnectionFactory cf = InitialContext.doLookup("jms/SwiftQueueCF");
        log.info("JNDI lookup successful: jms/SwiftQueueCF → {}", cf.getClass().getName());
        return cf;
    }

    @Bean(name = "jmsTemplate")
    public JmsTemplate jmsTemplate(ConnectionFactory jmsConnectionFactory) {
        JmsTemplate template = new JmsTemplate(jmsConnectionFactory);
        template.setDefaultDestinationName("SWIFT.SEND.QUEUE");
        template.setDeliveryPersistent(true);
        template.setReceiveTimeout(5000);
        // sessionTransacted is intentionally NOT set (defaults to false).
        // The JTA transaction manager coordinates commit/rollback across
        // both Oracle DB and IBM MQ via XA two-phase commit.
        return template;
    }
}
