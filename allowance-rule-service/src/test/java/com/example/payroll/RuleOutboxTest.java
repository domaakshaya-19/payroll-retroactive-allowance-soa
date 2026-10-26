package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
class RuleOutboxTest {
    @Test void brokerAckDeletesOutboxButFailureRetainsIt(){
        var repository=mock(RuleOutboxRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
        var row=new RuleOutbox();row.id=UUID.randomUUID();row.eventKey="EMP001:HOUSING";row.payload="{}";
        when(repository.findTop50ByOrderByEventKeyAscAggregateVersionAsc()).thenReturn(List.of(row));
        when(kafka.send(RuleEvents.TOPIC,row.eventKey,row.payload)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));
        var publisher=new RuleEvents(repository,kafka);publisher.publish();verify(repository,never()).deleteById(any());
        when(kafka.send(RuleEvents.TOPIC,row.eventKey,row.payload)).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publish();verify(repository).deleteById(row.id);
    }
}
