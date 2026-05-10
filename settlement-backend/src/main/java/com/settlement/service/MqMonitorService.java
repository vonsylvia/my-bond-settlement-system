package com.settlement.service;

import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries IBM MQ queue status via PCF (Programmable Command Format) commands.
 * Uses a standalone admin connection independent of the JCA resource adapter,
 * so monitoring remains operational even if the RA is unhealthy.
 */
@Service
public class MqMonitorService {

    private static final Logger log = LoggerFactory.getLogger(MqMonitorService.class);

    private static final List<String> MONITORED_QUEUES = List.of(
            "SWIFT.REPLY.QUEUE",
            "SWIFT.SEND.QUEUE",
            "SWIFT.DLQ"
    );

    @Value("${mq.monitor.host:#{systemProperties['MQ_HOST'] ?: 'localhost'}}")
    private String host;

    @Value("${mq.monitor.port:#{systemProperties['MQ_PORT'] ?: 1414}}")
    private int port;

    @Value("${mq.monitor.channel:SETTLEMENT.SVRCONN}")
    private String channel;

    @Value("${mq.monitor.queueManager:SETTLEMENT_QM}")
    private String queueManager;

    @Value("${mq.monitor.user:app}")
    private String user;

    @Value("${mq.monitor.password:passw0rd}")
    private String password;

    /**
     * Queries status for all monitored queues.
     *
     * @return map of queue name to status attributes
     */
    public Map<String, Object> queryAllQueueStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        PCFMessageAgent agent = null;
        try {
            agent = createAgent();
            for (String queueName : MONITORED_QUEUES) {
                result.put(queueName, queryQueue(agent, queueName));
            }
        } catch (Exception e) {
            log.error("Failed to query MQ queue status via PCF", e);
            result.put("error", e.getMessage());
        } finally {
            disconnect(agent);
        }
        return result;
    }

    /**
     * Queries status for a single queue.
     */
    public Map<String, Object> queryQueueStatus(String queueName) {
        PCFMessageAgent agent = null;
        try {
            agent = createAgent();
            return queryQueue(agent, queueName);
        } catch (Exception e) {
            log.error("Failed to query queue status for {}", queueName, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return error;
        } finally {
            disconnect(agent);
        }
    }

    @SuppressWarnings("unchecked")
    private PCFMessageAgent createAgent() throws Exception {
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(CMQC.HOST_NAME_PROPERTY, host);
        props.put(CMQC.PORT_PROPERTY, port);
        props.put(CMQC.CHANNEL_PROPERTY, channel);
        props.put(CMQC.USER_ID_PROPERTY, user);
        props.put(CMQC.PASSWORD_PROPERTY, password);
        props.put(CMQC.TRANSPORT_PROPERTY, CMQC.TRANSPORT_MQSERIES_CLIENT);

        MQQueueManager qm = new MQQueueManager(queueManager, props);
        return new PCFMessageAgent(qm);
    }

    private Map<String, Object> queryQueue(PCFMessageAgent agent, String queueName) throws Exception {
        Map<String, Object> status = new LinkedHashMap<>();

        PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
        request.addParameter(CMQC.MQCA_Q_NAME, queueName);
        request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[]{
                CMQC.MQIA_CURRENT_Q_DEPTH,
                CMQC.MQIA_MAX_Q_DEPTH,
                CMQC.MQIA_OPEN_INPUT_COUNT,
                CMQC.MQIA_OPEN_OUTPUT_COUNT
        });

        PCFMessage[] responses = agent.send(request);
        if (responses != null && responses.length > 0) {
            PCFMessage response = responses[0];
            status.put("currentDepth", response.getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH));
            status.put("maxDepth", response.getIntParameterValue(CMQC.MQIA_MAX_Q_DEPTH));
            status.put("openInputCount", response.getIntParameterValue(CMQC.MQIA_OPEN_INPUT_COUNT));
            status.put("openOutputCount", response.getIntParameterValue(CMQC.MQIA_OPEN_OUTPUT_COUNT));
        }

        try {
            PCFMessage statusRequest = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_STATUS);
            statusRequest.addParameter(CMQC.MQCA_Q_NAME, queueName);
            statusRequest.addParameter(CMQCFC.MQIACF_Q_STATUS_ATTRS, new int[]{
                    CMQCFC.MQCACF_LAST_PUT_DATE,
                    CMQCFC.MQCACF_LAST_PUT_TIME,
                    CMQCFC.MQCACF_LAST_GET_DATE,
                    CMQCFC.MQCACF_LAST_GET_TIME
            });

            PCFMessage[] statusResponses = agent.send(statusRequest);
            if (statusResponses != null && statusResponses.length > 0) {
                PCFMessage sr = statusResponses[0];
                String lastPutDate = sr.getStringParameterValue(CMQCFC.MQCACF_LAST_PUT_DATE);
                String lastPutTime = sr.getStringParameterValue(CMQCFC.MQCACF_LAST_PUT_TIME);
                String lastGetDate = sr.getStringParameterValue(CMQCFC.MQCACF_LAST_GET_DATE);
                String lastGetTime = sr.getStringParameterValue(CMQCFC.MQCACF_LAST_GET_TIME);

                if (lastPutDate != null && !lastPutDate.isBlank()) {
                    status.put("lastPutDateTime", lastPutDate.trim() + " " + lastPutTime.trim());
                }
                if (lastGetDate != null && !lastGetDate.isBlank()) {
                    status.put("lastGetDateTime", lastGetDate.trim() + " " + lastGetTime.trim());
                }
            }
        } catch (Exception e) {
            log.debug("Could not retrieve queue status timestamps for {}: {}", queueName, e.getMessage());
        }

        return status;
    }

    private void disconnect(PCFMessageAgent agent) {
        if (agent != null) {
            try {
                agent.disconnect();
            } catch (Exception e) {
                log.debug("Error disconnecting PCF agent", e);
            }
        }
    }
}
