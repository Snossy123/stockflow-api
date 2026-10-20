package com.arafawood.api.license;

import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseConfig {

    @Bean
    LicenseService licenseService(LicenseProperties properties) throws IOException {
        Boolean baked = EnforceFlag.read();
        if (baked != null) {
            properties.setEnforce(baked);
        }
        PublicKey publicKey = null;
        try (InputStream in = LicenseConfig.class.getResourceAsStream("/license/public.pem")) {
            if (in != null) {
                publicKey = LicenseTokens.readPublicKey(in.readAllBytes());
            }
        }
        return new LicenseService(properties, publicKey);
    }
}
