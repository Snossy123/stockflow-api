package com.arafawood.api.license;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

final class LicenseTokens {

    private LicenseTokens() {
    }

    static PublicKey readPublicKey(byte[] pemBytes) {
        String pem = new String(pemBytes, StandardCharsets.US_ASCII).replace("\uFEFF", "");
        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        if (base64.isEmpty()) {
            throw new IllegalStateException("License public key is empty");
        }
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("License public key is invalid", ex);
        }
    }

    static LicensePayload verify(String token, PublicKey publicKey) {
        if (token == null || publicKey == null) {
            return null;
        }
        String compact = unwrap(token).replaceAll("\\s", "");
        int dot = compact.indexOf('.');
        if (dot <= 0 || dot != compact.lastIndexOf('.')) {
            return null;
        }
        String payloadPart = compact.substring(0, dot);
        String signaturePart = compact.substring(dot + 1);
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payloadPart.getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(signaturePart))) {
                return null;
            }
            String json = new String(Base64.getUrlDecoder().decode(payloadPart), StandardCharsets.UTF_8);
            String client = jsonString(json, "client");
            String fingerprint = normalizeFingerprint(jsonString(json, "fingerprint"));
            String issuedAt = jsonString(json, "issued_at");
            if (client == null || client.isBlank() || client.length() > 200) {
                return null;
            }
            if (fingerprint == null) {
                return null;
            }
            if (issuedAt == null || issuedAt.isBlank() || issuedAt.length() > 40) {
                return null;
            }
            return new LicensePayload(client, fingerprint, issuedAt);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    static String normalizeFingerprint(String value) {
        if (value == null) {
            return null;
        }
        String compact = unwrap(value).replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        if (!compact.matches("[0-9a-f]{64}")) {
            return null;
        }
        return compact;
    }

    private static String unwrap(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String jsonString(String json, String field) {
        String needle = "\"" + field + "\"";
        int from = 0;
        while (from < json.length()) {
            int key = json.indexOf(needle, from);
            if (key < 0) {
                return null;
            }
            int previous = key - 1;
            while (previous >= 0 && Character.isWhitespace(json.charAt(previous))) {
                previous--;
            }
            if (previous >= 0 && json.charAt(previous) != '{' && json.charAt(previous) != ',') {
                from = key + needle.length();
                continue;
            }
            int colon = json.indexOf(':', key + needle.length());
            if (colon < 0) {
                return null;
            }
            int quote = json.indexOf('"', colon + 1);
            if (quote < 0) {
                return null;
            }
            return readJsonString(json, quote + 1);
        }
        return null;
    }

    private static String readJsonString(String json, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < json.length(); i++) {
            char current = json.charAt(i);
            if (current == '"') {
                return out.toString();
            }
            if (current != '\\') {
                out.append(current);
                continue;
            }
            if (i + 1 >= json.length()) {
                throw new IllegalArgumentException("Truncated JSON string");
            }
            char escaped = json.charAt(++i);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 >= json.length()) {
                        throw new IllegalArgumentException("Truncated JSON unicode escape");
                    }
                    out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalArgumentException("Bad JSON escape");
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }
}
