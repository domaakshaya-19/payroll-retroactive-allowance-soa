import java.nio.file.*;
import java.security.*;
import java.util.Base64;

/** JDK-only local development RSA key generator; never commit the output. */
class LocalKeys {
    public static void main(String[] args) throws Exception {
        var directory = Path.of(args[0]);
        Files.createDirectories(directory);
        if (Files.exists(directory.resolve("jwt-private.pem")) || Files.exists(directory.resolve("jwt-public.pem"))) {
            throw new IllegalStateException("JWT key files already exist; refusing to overwrite");
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        write(directory.resolve("jwt-private.pem"), "PRIVATE KEY", pair.getPrivate().getEncoded());
        write(directory.resolve("jwt-public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
    }
    static void write(Path path, String label, byte[] bytes) throws Exception {
        var encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes);
        Files.writeString(path, "-----BEGIN " + label + "-----\n" + encoded + "\n-----END " + label + "-----\n");
    }
}
