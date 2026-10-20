package com.arafawood.api.license;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Production images bake {@code true} into {@code license/enforce.txt}.
 * When that file is present its value wins, so a shop cannot turn the check off
 * by editing compose environment variables.
 */
final class EnforceFlag {

    private EnforceFlag() {
    }

    static Boolean read() {
        try (InputStream in = EnforceFlag.class.getResourceAsStream("/license/enforce.txt")) {
            if (in == null) {
                return null;
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the license enforce flag", ex);
        }
    }

    static Boolean parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new IllegalStateException("Invalid license enforce flag");
    }
}
