package com.example.payroll;

import com.example.payroll.contracts.AllowanceRuleBackdated;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class EventReceipt {
    private final WorkflowRepository workflows;private final AggregateProgressRepository progress;
    private final ObjectMapper mapper;private final Validator validator;
    @org.springframework.beans.factory.annotation.Value("${payroll.business-date:2026-10-25}")
    private java.time.LocalDate businessDate=java.time.LocalDate.of(2026,10,25);
    private static final Set<String> FIELDS=Set.of("eventId","aggregateId","schemaVersion","occurredAt","correlationId","eventType","employeeId","allowanceType","oldVersion","newVersion","oldAmount","newAmount","effectiveFrom");
    public EventReceipt(WorkflowRepository workflows,AggregateProgressRepository progress,ObjectMapper mapper,Validator validator){this.workflows=workflows;this.progress=progress;this.mapper=mapper;this.validator=validator;}
    public AllowanceRuleBackdated decode(String payload){
        try{
                var reader=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode tree=reader.readTree(payload);
            if(tree==null || !tree.isObject())throw new InvalidRuleEventException("INVALID_JSON_OBJECT");
            var present=new HashSet<String>();tree.fieldNames().forEachRemaining(present::add);
            if(!present.equals(FIELDS))throw new InvalidRuleEventException("EVENT_FIELDS_MISMATCH");
            if(!tree.path("schemaVersion").isIntegralNumber() || tree.path("schemaVersion").intValue()!=1)throw new InvalidRuleEventException("UNSUPPORTED_SCHEMA_VERSION");
            for(String field:FIELDS){
                var node=tree.get(field);if(node==null || node.isNull())throw new InvalidRuleEventException("MISSING_EVENT_VALUE");
                boolean valid=switch(field){
                    case "oldVersion","newVersion","schemaVersion" -> node.isIntegralNumber() && node.canConvertToInt();
                    case "oldAmount","newAmount" -> node.isNumber();
                    default -> node.isTextual();
                };
                if(!valid)throw new InvalidRuleEventException("EVENT_FIELD_TYPE_MISMATCH");
            }
                String uuid="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
                if(!tree.get("eventId").asText().matches(uuid) || !tree.get("correlationId").asText().matches(uuid) ||
                    !tree.get("effectiveFrom").asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") ||
                    !tree.get("occurredAt").asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(Z|[+-][0-9]{2}:[0-9]{2})"))
                    throw new InvalidRuleEventException("EVENT_FORMAT_MISMATCH");
            var event=mapper.treeToValue(tree,AllowanceRuleBackdated.class);
            if(!validator.validate(event).isEmpty() || event.newVersion()!=(long)event.oldVersion()+1 || event.effectiveFrom().getDayOfMonth()!=1 ||
                event.effectiveFrom().isAfter(businessDate) || event.oldAmount().compareTo(event.newAmount())==0 ||
                !event.aggregateId().equals(event.employeeId()+":"+event.allowanceType()))
                throw new InvalidRuleEventException("INVALID_EVENT_VALUES");
                return new AllowanceRuleBackdated(event.eventId(),event.aggregateId(),event.schemaVersion(),event.occurredAt(),event.correlationId(),event.eventType(),
                    event.employeeId(),event.allowanceType(),event.oldVersion(),event.newVersion(),event.oldAmount().setScale(2),event.newAmount().setScale(2),event.effectiveFrom());
        }catch(InvalidRuleEventException error){throw error;}
        catch(Exception ignored){throw new InvalidRuleEventException("MALFORMED_EVENT_JSON");}
    }
    @Transactional public void record(AllowanceRuleBackdated event){
        var existing=workflows.findById(event.eventId());
        if(existing.isPresent()){verifySame(existing.get(),event);return;}
        if(!progress.existsById(event.aggregateId())){var cursor=new AggregateProgress();cursor.aggregateId=event.aggregateId();progress.saveAndFlush(cursor);}
        var row=new RetroWorkflow();row.eventId=event.eventId();row.aggregateId=event.aggregateId();row.correlationId=event.correlationId();
        row.employeeId=event.employeeId();row.allowanceType=event.allowanceType();row.newVersion=event.newVersion();row.status="RECEIVED";row.receivedAt=java.time.Instant.now();
        try{row.payload=mapper.writeValueAsString(event);}catch(Exception ignored){throw new IllegalStateException("Event serialization failed");}
        workflows.saveAndFlush(row);
    }
    @Transactional(readOnly=true) public void verifyDuplicate(AllowanceRuleBackdated event){
        var row=workflows.findById(event.eventId()).orElseThrow(() -> new InvalidRuleEventException("CONFLICTING_AGGREGATE_VERSION"));verifySame(row,event);
    }
    private void verifySame(RetroWorkflow row,AllowanceRuleBackdated event){
        if(!decode(row.payload).equals(event))throw new InvalidRuleEventException("CONFLICTING_EVENT_ID");
    }
}
