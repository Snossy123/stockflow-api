package com.arafawood.api.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LicenseServiceTest {

    @Test
    void devModeSkipsTheLicense() {
        LicenseProperties properties = new LicenseProperties();
        properties.setKey("not-a-key");
        LicenseService service = new LicenseService(properties, null);
        assertFalse(service.view().enforced());
        assertTrue(service.view().valid());
        assertTrue(service.allowsApi());
    }

    @Test
    void productionWithoutPublicKeyRefusesToStart() throws Exception {
        LicenseProperties properties = enforced("");
        assertThrows(IllegalStateException.class, () -> new LicenseService(properties, null));
    }

    @Test
    void validKeyMatchesTheMachine() throws Exception {
        KeyPair pair = rsa();
        String fingerprint = "ab".repeat(32);
        LicenseService service = new LicenseService(
                enforced(sign(pair, "عرفة للأخشاب", fingerprint), fingerprint),
                pair.getPublic());
        assertTrue(service.view().valid());
        assertEquals("عرفة للأخشاب", service.view().client());
        assertNull(service.view().message());
    }

    @Test
    void quotedClientAndDifferentMachineAreRejected() throws Exception {
        KeyPair pair = rsa();
        String fingerprint = "ab".repeat(32);
        String token = sign(pair, "Shop \"A\"\\B", fingerprint);
        LicenseService valid = new LicenseService(enforced(token, fingerprint.toUpperCase()), pair.getPublic());
        assertEquals("Shop \"A\"\\B", valid.view().client());

        LicenseService otherMachine = new LicenseService(enforced(token, "cd".repeat(32)), pair.getPublic());
        assertFalse(otherMachine.view().valid());
        assertEquals(LicenseService.DENIAL_MESSAGE, otherMachine.view().message());

        String tampered = token.substring(0, token.length() - 4) + "aaaa";
        LicenseService badSignature = new LicenseService(enforced(tampered, fingerprint), pair.getPublic());
        assertFalse(badSignature.view().valid());
    }

    @Test
    void filterBlocksApiAndLeavesHealthOpen() throws Exception {
        KeyPair pair = rsa();
        LicenseService service = new LicenseService(enforced("", "ab".repeat(32)), pair.getPublic());
        LicenseFilter filter = new LicenseFilter(service);
        FilterChain chain = (request, response) -> ((HttpServletResponse) response).setStatus(204);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), blocked, chain);
        assertEquals(403, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains(LicenseService.DENIAL_CODE));
        assertTrue(blocked.getContentAsString().contains(LicenseService.DENIAL_MESSAGE));

        MockHttpServletResponse health = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/health"), health, chain);
        assertEquals(204, health.getStatus());

        MockHttpServletResponse status = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/license/status"), status, chain);
        assertEquals(204, status.getStatus());
    }

    @Test
    void enforceFlagParsesTrueAndFalse() {
        assertEquals(Boolean.TRUE, EnforceFlag.parse("true\n"));
        assertEquals(Boolean.FALSE, EnforceFlag.parse(" false "));
        assertEquals(null, EnforceFlag.parse("  "));
        assertThrows(IllegalStateException.class, () -> EnforceFlag.parse("yes"));
    }

    @Test
    @EnabledIf("toolsAvailable")
    void shellSignerMatchesTheVerifier() throws Exception {
        Path script = Path.of("..", "deploy", "license-generate.sh").toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            script = Path.of("deploy", "license-generate.sh").toAbsolutePath().normalize();
        }
        assertTrue(Files.isRegularFile(script), script.toString());

        Path dir = Files.createTempDirectory("arafa-license");
        Path privateKey = dir.resolve("private.pem");
        Path publicKey = dir.resolve("public.pem");
        assertEquals(0, run(dir, "openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                "-out", privateKey.toString()));
        assertEquals(0, run(dir, "openssl", "pkey", "-in", privateKey.toString(), "-pubout", "-out", publicKey.toString()));

        String fingerprint = "ab".repeat(32);
        ProcessBuilder builder = new ProcessBuilder(
                "bash", script.toString(), "--client", "عرفة للأخشاب", "--fingerprint", fingerprint);
        builder.environment().put("LICENSE_PRIVATE_KEY_FILE", privateKey.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, process.waitFor(), output);
        String token = output.lines().reduce((first, second) -> second).orElse("");
        LicenseService service = new LicenseService(
                enforced(token, fingerprint),
                LicenseTokens.readPublicKey(Files.readAllBytes(publicKey)));
        assertTrue(service.view().valid(), token);
        assertEquals("عرفة للأخشاب", service.view().client());
    }

    public static boolean toolsAvailable() {
        return commandExists("openssl") && commandExists("python3") && commandExists("bash");
    }

    private static LicenseProperties enforced(String key, String fingerprint) {
        LicenseProperties properties = new LicenseProperties();
        properties.setEnforce(true);
        properties.setKey(key);
        properties.setFingerprint(fingerprint);
        return properties;
    }

    private static LicenseProperties enforced(String key) {
        return enforced(key, "");
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String sign(KeyPair pair, String client, String fingerprint, String issuedAt) throws Exception {
        String json = "{\"client\":" + js(client) + ",\"fingerprint\":" + js(fingerprint) + ",\"issued_at\":" + js(issuedAt) + "}";
        String payload = b64(json.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.US_ASCII));
        return payload + "." + b64(signature.sign());
    }

    private static String sign(KeyPair pair, String client, String fingerprint) throws Exception {
        return sign(pair, client, fingerprint, "2026-10-01T00:00:00Z");
    }

    private static String js(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static int run(Path dir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(dir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    private static boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
