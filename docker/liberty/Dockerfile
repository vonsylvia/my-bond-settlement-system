# Multi-stage build: extract IBM MQ Resource Adapter from MQ image
FROM icr.io/ibm-messaging/mq:latest AS mq-source

FROM icr.io/appcafe/websphere-liberty:24.0.0.12-full-java21-openj9-ubi-minimal

# Copy IBM MQ JCA Resource Adapter from the MQ installation
COPY --from=mq-source /opt/mqm/java/lib/jca/wmq.jakarta.jmsra.rar \
     /opt/ibm/wlp/usr/servers/defaultServer/resources/wmq/wmq.jakarta.jmsra.rar

# Oracle JDBC driver placeholder (mounted via docker-compose volume)
RUN mkdir -p /opt/ibm/wlp/usr/shared/resources/jdbc
