package com.example.payroll;

import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.*;
@Component
public class RuleEventConsumer {
    private final EventReceipt receipt;
    public RuleEventConsumer(EventReceipt receipt){this.receipt=receipt;}
        @KafkaListener(id="rule-backdate-consumer",clientIdPrefix="retro-backdate-${server.port}",topics="soa.s08.t04.events",groupId="${payroll.kafka.group-id:retro-backdate-v1}",autoStartup="${payroll.kafka.consumer-enabled:true}")
    public void consume(@Header(name=KafkaHeaders.RECEIVED_KEY,required=false) String key,@Payload(required=false) String payload){
        var event=receipt.decode(payload);
        if(!event.aggregateId().equals(key))throw new InvalidRuleEventException("AGGREGATE_KEY_MISMATCH");
        try{receipt.record(event);}catch(org.springframework.dao.DataIntegrityViolationException duplicate){receipt.verifyDuplicate(event);}
    }
}
