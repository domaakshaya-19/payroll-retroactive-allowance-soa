package com.example.payroll;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class WorkflowWorker {
    private final WorkflowRepository workflows;private final RetroWorkflowService service;
    public WorkflowWorker(WorkflowRepository workflows,RetroWorkflowService service){this.workflows=workflows;this.service=service;}
    @Scheduled(fixedDelayString="${payroll.workflow.delay:5000}") public void process(){
        for(var row:workflows.findTop50ByStatusInOrderByReceivedAtAsc(java.util.List.of("RECEIVED","RETRY_PENDING","WAITING_PREDECESSOR"))){
            try{service.process(row.eventId);}catch(Exception ignored){service.failed(row.eventId);
                org.slf4j.LoggerFactory.getLogger("PAYROLL_AUDIT").warn("retro_workflow_failed details=[REDACTED]");}
        }
    }
}
