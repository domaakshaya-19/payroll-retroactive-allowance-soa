package com.example.payroll;

import com.example.payroll.contracts.*;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.time.Duration;
@Component
public class DiscoverySnapshotClient implements SnapshotClient {
    private final RestClient client;private final String password;
    public DiscoverySnapshotClient(@Qualifier("discoveredRestClient") RestClient.Builder builder,@Value("${payroll.service.password}") String password){this.client=builder.build();this.password=password;}
    private String token(){
        var response=client.post().uri("https://auth-service/api/auth/token").body(new TokenRequest("retro-service",password)).retrieve().body(TokenResponse.class);
        if(response==null)throw new IllegalStateException("Service authentication unavailable");
        return response.accessToken();
    }
    public java.util.List<HistoricalSnapshot> history(String employeeId){
        var result=client.get().uri("https://pay-period-service/api/pay-periods/employees/{employeeId}/snapshots",employeeId)
            .headers(h -> h.setBearerAuth(token())).retrieve().body(HistoricalSnapshot[].class);
        if(result==null)throw new IllegalStateException("Historical snapshots unavailable");
        return java.util.List.of(result);
    }
    public PayPeriodResponse openPeriod(String employeeId){
        var result=client.get().uri("https://pay-period-service/api/pay-periods/employees/{employeeId}/open",employeeId)
            .headers(h -> h.setBearerAuth(token())).retrieve().body(PayPeriodResponse.class);
        if(result==null || !employeeId.equals(result.employeeId()) || !"OPEN".equals(result.status()))throw new IllegalStateException("Open period unavailable");
        return result;
    }
    @Configuration static class ClientConfiguration {
        @Bean @LoadBalanced RestClient.Builder discoveredRestClient(){
            var http=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var factory=new JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(10));
            return RestClient.builder().requestFactory(factory);
        }
    }
}
