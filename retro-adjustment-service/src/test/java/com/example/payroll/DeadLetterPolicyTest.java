package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.listener.MessageListenerContainer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeadLetterPolicyTest {
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void invalidEventGoesDirectlyToDltWithOriginalKeyValueAndSafeContext(){
        KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);ProducerFactory<String,String> factory=mock(ProducerFactory.class);
        when(kafka.getProducerFactory()).thenReturn(factory);when(factory.getConfigurationProperties()).thenReturn(Map.of("delivery.timeout.ms",1000));
        var result=mock(SendResult.class);when(result.getRecordMetadata()).thenReturn(mock(RecordMetadata.class));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(result));
        Consumer<String,String> consumer=mock(Consumer.class);
        when(consumer.partitionsFor("soa.s08.t04.events.DLT")).thenReturn(List.of(new PartitionInfo("soa.s08.t04.events.DLT",0,null,null,null)));
        when(consumer.partitionsFor(eq("soa.s08.t04.events.DLT"),any(java.time.Duration.class))).thenReturn(List.of(new PartitionInfo("soa.s08.t04.events.DLT",0,null,null,null)));
        var record=new ConsumerRecord<String,String>("soa.s08.t04.events",0,42L,"EMP001:HOUSING","invalid original payload");
        var handler=new KafkaConfig().kafkaErrorHandler(kafka);
        assertTrue(handler.handleOne(new InvalidRuleEventException("UNSUPPORTED_SCHEMA_VERSION"),record,consumer,mock(MessageListenerContainer.class)));
        ArgumentCaptor<ProducerRecord<String,String>> sent=ArgumentCaptor.forClass(ProducerRecord.class);verify(kafka,times(1)).send(sent.capture());
        assertEquals("soa.s08.t04.events.DLT",sent.getValue().topic());assertEquals(0,sent.getValue().partition());
        assertEquals(record.key(),sent.getValue().key());assertEquals(record.value(),sent.getValue().value());
        var code=sent.getValue().headers().lastHeader("payroll-error-code");assertNotNull(code);
        assertEquals("UNSUPPORTED_SCHEMA_VERSION",new String(code.value(),java.nio.charset.StandardCharsets.UTF_8));
        assertNotNull(sent.getValue().headers().lastHeader(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET));
    }
}
