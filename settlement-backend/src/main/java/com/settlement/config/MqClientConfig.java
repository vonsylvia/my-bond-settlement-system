package com.settlement.config;

import com.ibm.mq.jakarta.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.common.CommonConstants;
import com.settlement.jms.SwiftReplyListener;
import com.settlement.reconcile.ReconciliationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import jakarta.jms.ConnectionFactory;

@Configuration
public class MqClientConfig {

    @Bean(name = "jmsConnectionFactory")
    public ConnectionFactory jmsConnectionFactory() throws Exception {
        MQQueueConnectionFactory target = new MQQueueConnectionFactory();
        target.setHostName(env("MQ_HOST", "localhost"));
        target.setPort(Integer.parseInt(env("MQ_PORT", "1414")));
        target.setQueueManager(env("MQ_QUEUE_MANAGER", "SETTLEMENT_QM"));
        target.setChannel(env("MQ_CHANNEL", "SETTLEMENT.SVRCONN"));
        target.setTransportType(CommonConstants.WMQ_CM_CLIENT);

        UserCredentialsConnectionFactoryAdapter adapter = new UserCredentialsConnectionFactoryAdapter();
        adapter.setTargetConnectionFactory(target);
        adapter.setUsername(env("MQ_USER", "app"));
        adapter.setPassword(env("MQ_PASSWORD", "passw0rd"));
        return adapter;
    }

    @Bean(name = "jmsTemplate")
    public JmsTemplate jmsTemplate(ConnectionFactory jmsConnectionFactory) {
        JmsTemplate template = new JmsTemplate(jmsConnectionFactory);
        template.setDefaultDestinationName("SWIFT.SEND.QUEUE");
        template.setDeliveryPersistent(true);
        template.setSessionTransacted(true);
        template.setReceiveTimeout(5000);
        return template;
    }

    @Bean
    public SwiftReplyListener swiftReplyListener(ReconciliationService reconciliationService) {
        return new SwiftReplyListener(reconciliationService);
    }

    @Bean(name = "replyListenerContainer")
    public DefaultMessageListenerContainer replyListenerContainer(
            ConnectionFactory jmsConnectionFactory,
            SwiftReplyListener swiftReplyListener) {
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(jmsConnectionFactory);
        container.setDestinationName("SWIFT.REPLY.QUEUE");
        container.setMessageListener(swiftReplyListener);
        container.setSessionTransacted(true);
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(3);
        container.setReceiveTimeout(5000);
        return container;
    }

    private String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
