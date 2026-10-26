package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
class SharedRetroStoreTest {
    @TempDir Path directory;
    @Test void fileStoreSurvivesOneReplicaDisconnectingAndRejectsOtherCredentials() throws Exception {
        Class<?> type=Class.forName("org.h2.tools.Server");
        Object server=type.getMethod("createTcpServer",String[].class).invoke(null,(Object)new String[]{"-tcpPort","0","-baseDir",directory.toString(),"-ifNotExists"});
        type.getMethod("start").invoke(server);
        try {
            int port=(int)type.getMethod("getPort").invoke(server);String url="jdbc:h2:tcp://localhost:"+port+"/./payroll";
            try(var first=DriverManager.getConnection(url,"retro_owner","test-only-retro")){
                first.createStatement().execute("create table dedup_test(event_id int primary key)");
                try(var second=DriverManager.getConnection(url,"retro_owner","test-only-retro")){
                    first.setAutoCommit(false);first.createStatement().execute("insert into dedup_test values(1)");first.rollback();
                    second.createStatement().execute("insert into dedup_test values(2)");
                }
            }
            try(var survivor=DriverManager.getConnection(url,"retro_owner","test-only-retro")){
                var rows=survivor.createStatement().executeQuery("select count(*) from dedup_test");assertTrue(rows.next());assertEquals(1,rows.getInt(1));
                assertThrows(SQLException.class,() -> survivor.createStatement().execute("insert into dedup_test values(2)"));
            }
            assertThrows(SQLException.class,() -> DriverManager.getConnection(url,"allowance_owner","wrong-domain"));
            assertTrue(Files.exists(directory.resolve("payroll.mv.db")));
        } finally {type.getMethod("stop").invoke(server);}
    }
}
