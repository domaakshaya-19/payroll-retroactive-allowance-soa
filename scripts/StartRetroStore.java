import java.nio.file.*;
import java.net.*;
import java.time.Duration;
import java.util.zip.ZipFile;

/** Local-only, file-backed storage process shared by the two Retro replicas. */
class StartRetroStore {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        Path local=root.resolve(".local");Files.createDirectories(local);
        Path pidFile=local.resolve("retro-store.pid");
        if(Files.exists(pidFile)) {
            var existing=ProcessHandle.of(Long.parseLong(Files.readString(pidFile).trim()));
            if(existing.isPresent() && existing.get().isAlive()) {
                String command=existing.get().info().commandLine().orElse("");
                if(!command.contains("org.h2.tools.Server") || !command.contains("9094"))throw new IllegalStateException("Stored PID does not identify the Retro H2 server");
                System.out.println("Retro store already running: PID="+existing.get().pid());return;
            }
        }
        try(var socket=new java.net.ServerSocket(9094,1,InetAddress.getLoopbackAddress())){} // Refuse an occupied port.
        Path app=root.resolve("retro-adjustment-service/target/retro-adjustment-service-1.0.0-SNAPSHOT.jar");
        Path h2=local.resolve("h2.jar");
        try(var zip=new ZipFile(app.toFile())) {
            var entry=zip.stream().filter(e -> e.getName().startsWith("BOOT-INF/lib/h2-") && e.getName().endsWith(".jar")).findFirst().orElseThrow();
            try(var input=zip.getInputStream(entry)){Files.copy(input,h2,StandardCopyOption.REPLACE_EXISTING);}
        }
        Path data=root.resolve("data/retro-adjustment-service");Files.createDirectories(data);
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        var builder=new ProcessBuilder(java,"-cp",h2.toString(),"org.h2.tools.Server","-tcp","-tcpPort","9094","-baseDir",data.toString(),"-ifNotExists");
        builder.directory(root.toFile());
        // The server authenticates incoming JDBC connections; it needs none of the app secrets.
        builder.environment().keySet().removeIf(key -> key.endsWith("PASSWORD") || key.endsWith("DB_USER") || key.equals("JWT_PRIVATE_KEY"));
        builder.redirectOutput(local.resolve("retro-store.log").toFile());builder.redirectError(local.resolve("retro-store.error.log").toFile());
        var process=builder.start();Files.writeString(pidFile,Long.toString(process.pid()));
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while(System.nanoTime()<deadline && process.isAlive()) {
            try(var socket=new Socket()){socket.connect(new InetSocketAddress("localhost",9094),250);System.out.println("Retro store ready: PID="+process.pid());return;}
            catch(java.io.IOException pending){Thread.sleep(100);}
        }
        process.destroy();throw new IllegalStateException("Retro store failed; inspect .local/retro-store.error.log");
    }
}
