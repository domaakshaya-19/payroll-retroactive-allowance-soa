package com.example.payroll.contracts;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HistoricalSnapshotContractTest {
    @Test void payPeriodResponseAndRetroConsumerAgreeOnRequiredRestFields() throws Exception {
        var mapper=JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        try(var fixture=getClass().getResourceAsStream("/fixtures/historical-snapshot-v1.json");var resource=getClass().getResourceAsStream("/schemas/historical-snapshot-v1.schema.json")){
            JsonNode payload=mapper.readTree(fixture);var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(resource);
            assertThat(schema.validate(payload)).isEmpty();var dto=mapper.readerFor(HistoricalSnapshot.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).<HistoricalSnapshot>readValue(payload);
            assertThat(dto.employeeId()).isEqualTo("EMP001");assertThat(dto.originalAmount()).isEqualByComparingTo("2000.00");
            assertThat(schema.validate(mapper.valueToTree(dto))).isEmpty();
            var incompatible=(com.fasterxml.jackson.databind.node.ObjectNode)payload.deepCopy();incompatible.remove("originalAmount");assertThat(schema.validate(incompatible)).isNotEmpty();
        }
    }
}
