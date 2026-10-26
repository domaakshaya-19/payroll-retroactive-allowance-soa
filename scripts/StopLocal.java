import com.sun.tools.attach.VirtualMachine;
import javax.management.ObjectName;
import javax.management.remote.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Graceful Spring shutdown via the same-user local JMX agent; no HTTP admin endpoint. */
class StopLocal {
    public static void main(String[] args) throws Exception {
        long pid=Long.parseLong(args[0]);String expectedService=args[1],expectedPort=args[2];
        var handle=ProcessHandle.of(pid).orElseThrow(() -> new IllegalStateException("Process already stopped"));
        var vm=VirtualMachine.attach(Long.toString(pid));String address;
        try{address=vm.startLocalManagementAgent();}finally{vm.detach();}
        var bean=new ObjectName("org.springframework.boot:type=Admin,name=SpringApplication");
        boolean requested=false;
        try(var connector=JMXConnectorFactory.connect(new JMXServiceURL(address))) {
            var connection=connector.getMBeanServerConnection();
            Object service=connection.invoke(bean,"getProperty",new Object[]{"spring.application.name"},new String[]{"java.lang.String"});
            Object port=connection.invoke(bean,"getProperty",new Object[]{"server.port"},new String[]{"java.lang.String"});
            if(!expectedService.equals(service) || !expectedPort.equals(port))throw new IllegalStateException("PID belongs to a different app or port; refusing shutdown");
            requested=true;connection.invoke(bean,"shutdown",null,null);
        }catch(java.io.IOException closing){if(!requested)throw closing; /* Local connector can close during shutdown. */}
        handle.onExit().get(60,TimeUnit.SECONDS);
        System.out.println(expectedService+":"+expectedPort+" stopped gracefully");
    }
}
