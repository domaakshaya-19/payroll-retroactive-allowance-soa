package com.example.payroll;
import com.example.payroll.contracts.RetroApprovedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
@Component @EnableScheduling
public class Events {
    private final OutboxRepository outbox; private final KafkaTemplate<String,String> kafka; private final ObjectMapper mapper;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger("PAYROLL_AUDIT");
    public Events(OutboxRepository outbox,KafkaTemplate<String,String> kafka,ObjectMapper mapper){this.outbox=outbox;this.kafka=kafka;this.mapper=mapper;}
    @Scheduled(fixedDelay=5000) public void publish() {
        for(var row:outbox.findTop50ByOrderByIdAsc()) {
            try {kafka.send(KafkaConfig.TOPIC,row.id.toString(),row.payload).get(10,java.util.concurrent.TimeUnit.SECONDS); outbox.deleteById(row.id);}
            catch(Exception error){if(error instanceof InterruptedException) Thread.currentThread().interrupt(); log.warn("outbox_publish_failed details=[REDACTED]"); break;}
        }
    }
    @KafkaListener(topics=KafkaConfig.TOPIC) public void consume(String payload) throws Exception {
        try {
            var event=mapper.readValue(payload,RetroApprovedEvent.class);
            if(event==null || event.eventId()==null || event.employeeId()==null || event.adjustmentId()==null || event.difference()==null || event.occurredAt()==null)
                throw new IllegalArgumentException("Invalid event");
        } catch(Exception error) {
            // Do not retain Jackson's source excerpt or sensitive input in exception causes.
            throw new IllegalArgumentException("Event validation failed [REDACTED]");
        }
        log.info("retro_approved_event_received identity=[REDACTED] employee=[REDACTED] amount=[REDACTED]");
    }
}
