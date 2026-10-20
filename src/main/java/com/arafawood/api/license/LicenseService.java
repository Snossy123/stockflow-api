package com.arafawood.api.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;

public final class LicenseService {

    public static final String DENIAL_MESSAGE = "النظام غير مفعّل على هذا الجهاز";
    public static final String DENIAL_CODE = "LICENSE";

    private final boolean enforced;
    private final boolean valid;
    private final String client;

    public LicenseService(LicenseProperties properties, PublicKey publicKey) {
        this.enforced = properties.isEnforce();
        if (!enforced) {
            this.valid = true;
            this.client = null;
            return;
        }
        if (publicKey == null) {
            throw new IllegalStateException("License public key is missing from the image");
        }
        LicensePayload payload = LicenseTokens.verify(properties.getKey(), publicKey);
        String machine = LicenseTokens.normalizeFingerprint(properties.getFingerprint());
        if (payload == null || machine == null || !same(payload.fingerprint(), machine)) {
            this.valid = false;
            this.client = null;
            return;
        }
        this.valid = true;
        this.client = payload.client();
    }

    public boolean allowsApi() {
        return !enforced || valid;
    }

    public LicenseView view() {
        return new LicenseView(enforced, valid, client, valid ? null : DENIAL_MESSAGE);
    }

    private static boolean same(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }
}
