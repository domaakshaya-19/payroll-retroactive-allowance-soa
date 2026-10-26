package com.example.payroll;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
@Component @EnableScheduling
public class RuleEvents {
    public static final String TOPIC="soa.s08.t04.events";
    private final RuleOutboxRepository outbox;private final KafkaTemplate<String,String> kafka;
    public RuleEvents(RuleOutboxRepository outbox,KafkaTemplate<String,String> kafka){this.outbox=outbox;this.kafka=kafka;}
    @Bean NewTopic ruleTopic(){return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();}
    @Bean NewTopic ruleDlt(){return TopicBuilder.name(TOPIC+".DLT").partitions(3).replicas(1).build();}
    @Scheduled(fixedDelayString="${payroll.outbox.delay:5000}") public void publish(){
        for(var row:outbox.findTop50ByOrderByEventKeyAscAggregateVersionAsc()){
            try{ kafka.send(TOPIC,row.eventKey,row.payload).get(10,java.util.concurrent.TimeUnit.SECONDS);outbox.deleteById(row.id); }
            catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();
                org.slf4j.LoggerFactory.getLogger("PAYROLL_AUDIT").warn("rule_outbox_publication_failed details=[REDACTED]");break;}
        }
    }
}
