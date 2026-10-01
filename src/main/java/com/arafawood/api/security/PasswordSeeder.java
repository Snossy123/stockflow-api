package com.arafawood.api.security;

import com.arafawood.api.repo.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PasswordSeeder implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public PasswordSeeder(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        users.findAll().forEach(user -> {
            String hash = user.getPasswordHash();
            if (hash == null || !(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"))) {
                user.setPasswordHash(encoder.encode(hash == null ? "arafa123" : hash));
                users.save(user);
            }
        });
    }
}
