package com.example.payroll;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
@Configuration
public class KafkaConfig {
    public static final String TOPIC="payroll.retro-approved.v1";
    @Bean NewTopic ruleTopic(){return TopicBuilder.name("soa.s08.t04.events").partitions(3).replicas(1).build();}
    @Bean NewTopic ruleDlt(){return TopicBuilder.name("soa.s08.t04.events.DLT").partitions(3).replicas(1).build();}
    @Bean NewTopic approvedTopic(){return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();}
    @Bean NewTopic deadLetterTopic(){return TopicBuilder.name(TOPIC+".DLT").partitions(3).replicas(1).build();}
    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template) {
        var recoverer=new DeadLetterPublishingRecoverer(template,(record,error) -> new TopicPartition(record.topic()+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        // Headers can contain payload fragments in stack traces; keep the DLT metadata minimal.
        recoverer.setAppendOriginalHeaders(false);
        recoverer.excludeHeader(DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd.EX_STACKTRACE,
            DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd.EX_MSG);
        recoverer.setHeadersFunction(KafkaConfig::errorHeaders);
        var handler=new DefaultErrorHandler(recoverer,new FixedBackOff(1000L,2L));
        handler.addNotRetryableExceptions(InvalidRuleEventException.class);
        handler.setRetryListeners((record,error,attempt) -> org.slf4j.LoggerFactory.getLogger("PAYROLL_AUDIT").warn("kafka_consume_failed attempt={} details=[REDACTED]",attempt));
        return handler;
    }
    static org.apache.kafka.common.header.Headers errorHeaders(org.apache.kafka.clients.consumer.ConsumerRecord<?,?> record,Exception error){
        Throwable cause=error;while(cause!=null && !(cause instanceof InvalidRuleEventException))cause=cause.getCause();
        String code=cause instanceof InvalidRuleEventException invalid?invalid.code():"DELIVERY_RETRIES_EXHAUSTED";
        return new org.apache.kafka.common.header.internals.RecordHeaders()
            .add("payroll-error-code",code.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .add("payroll-failed-at",java.time.Instant.now().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
