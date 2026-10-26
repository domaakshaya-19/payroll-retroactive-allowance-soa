package com.example.payroll;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConsumerEventContractTest {
    @Test void publishedFixtureIsAcceptedAndIncompatibleRequiredFieldIsRejected() throws Exception {
        var mapper=JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        try(var fixture=getClass().getResourceAsStream("/fixtures/allowance-rule-backdated-v1.json");var resource=getClass().getResourceAsStream("/schemas/allowance-rule-backdated-v1.schema.json")){
            JsonNode payload=mapper.readTree(fixture);var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(resource);
            assertThat(schema.validate(payload)).isEmpty();
            var validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
            try {
                var consumer=new EventReceipt(mock(WorkflowRepository.class),mock(AggregateProgressRepository.class),mapper,validator);
                assertThat(consumer.decode(payload.toString()).newAmount()).isEqualByComparingTo("3000.00");
                var incompatible=(com.fasterxml.jackson.databind.node.ObjectNode)payload.deepCopy();incompatible.set("correctedAmount",incompatible.remove("newAmount"));
                assertThat(schema.validate(incompatible)).isNotEmpty();
                assertThatThrownBy(() -> consumer.decode(incompatible.toString())).isInstanceOf(InvalidRuleEventException.class).hasMessageContaining("EVENT_FIELDS_MISMATCH");
            }finally{validator.close();}
        }
    }
}
