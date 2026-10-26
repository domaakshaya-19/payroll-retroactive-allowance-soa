package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;
class DiscoverySnapshotClientTest {
    @Test void internalCallsUseServiceNamesAndBearerAuthentication(){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth-service/api/auth/token"))
            .andExpect(jsonPath("$.username").value("retro-service"))
            .andRespond(withSuccess("{\"accessToken\":\"service-jwt\",\"tokenType\":\"Bearer\",\"expiresIn\":900}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://pay-period-service/api/pay-periods/employees/EMP001/snapshots"))
            .andExpect(header("Authorization","Bearer service-jwt"))
            .andRespond(withSuccess("[{\"statementId\":11,\"periodId\":1,\"employeeId\":\"EMP001\",\"startsOn\":\"2026-08-01\",\"endsOn\":\"2026-08-31\",\"allowanceType\":\"HOUSING\",\"ruleVersion\":1,\"originalAmount\":2000.00,\"issuedAt\":\"2026-08-31T00:00:00Z\"}]",MediaType.APPLICATION_JSON));
        var client=new DiscoverySnapshotClient(builder,"test-service-password");
        var history=client.history("EMP001");assertEquals(1,history.size());assertEquals(new java.math.BigDecimal("2000.00"),history.get(0).originalAmount());server.verify();
    }
}
