package com.settlement.ejb;

import jakarta.ejb.MessageDrivenContext;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwiftReplyMDBTest {

    private SwiftReplyMDB mdb;

    @Mock
    private MessageDrivenContext mdbContext;

    @Mock
    private TextMessage textMessage;

    @BeforeEach
    void setUp() throws Exception {
        mdb = new SwiftReplyMDB();
        Field contextField = SwiftReplyMDB.class.getDeclaredField("mdbContext");
        contextField.setAccessible(true);
        contextField.set(mdb, mdbContext);
    }

    @Test
    void onMessage_withValidTextMessage_rollsBackWhenServiceNotRegistered() throws Exception {
        when(textMessage.getJMSCorrelationID()).thenReturn("TRADE-001");
        when(textMessage.getText()).thenReturn("<MT548 body>");
        when(textMessage.getStringProperty("MessageType")).thenReturn("MT548");

        mdb.onMessage(textMessage);

        // Without ServiceRegistry populated, reconciliationService is null → rollback
        verify(mdbContext).setRollbackOnly();
    }

    @Test
    void onMessage_withNonTextMessage_logsWarningAndReturns() throws Exception {
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("MSG-999");

        mdb.onMessage(bytesMessage);

        verify(mdbContext, never()).setRollbackOnly();
    }

    @Test
    void onMessage_withJmsException_rollsBack() throws Exception {
        when(textMessage.getJMSCorrelationID()).thenThrow(new JMSException("connection lost"));

        mdb.onMessage(textMessage);

        verify(mdbContext).setRollbackOnly();
    }
}
