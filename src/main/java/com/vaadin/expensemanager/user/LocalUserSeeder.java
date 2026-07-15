package com.vaadin.expensemanager.user;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a plain {@code USER} account for the {@code local}/{@code test}
 * form-stub login (ADR-0012).
 *
 * <p>The bootstrap {@code ADMIN} is seeded by migration in every profile, so
 * both role paths are exercisable offline once this adds the ordinary user:
 * log in as the admin to see admin content, or as this user to see the plain
 * path. Idempotent — only inserts if the row is missing — so it is safe across
 * the shared Testcontainers database and repeated local starts.
 *
 * <p>Profile-scoped: never runs in {@code staging}/{@code prod}, where accounts
 * are auto-provisioned by real Google login (ADR-0007).
 */
@Component
@Profile({"local", "test"})
@Order(0)
public class LocalUserSeeder implements ApplicationRunner {

    /** Well-known email for the seeded plain user; the login password is the shared dev password. */
    public static final String PLAIN_USER_EMAIL = "user@vaadin.com";

    private static final Logger log = LoggerFactory.getLogger(LocalUserSeeder.class);

    private final UserRepository userRepository;

    public LocalUserSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(PLAIN_USER_EMAIL)) {
            return;
        }
        userRepository.save(new User(PLAIN_USER_EMAIL, "Demo User", Set.of(Role.USER)));
        log.info("Seeded local/test plain user {}", PLAIN_USER_EMAIL);
    }
}
