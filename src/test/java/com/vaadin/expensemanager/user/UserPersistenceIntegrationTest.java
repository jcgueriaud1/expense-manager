package com.vaadin.expensemanager.user;

import java.util.Set;

import com.vaadin.expensemanager.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence + seed integration test for the user domain (Phase 1.1, ADR-0003,
 * ADR-0007), on Testcontainers Postgres with Flyway applied.
 *
 * <p>Covers the acceptance criteria for user storage: {@code email} is unique,
 * {@code sub} is unique only when set (multiple nulls allowed), and the V2
 * bootstrap-admin seed yields an ADMIN with a {@code null} sub. The {@code sub}
 * partial-unique index is exercised through native SQL because the entity has
 * no {@code sub} setter yet (Google claim lands in Phase 1.2).
 */
class UserPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Test
    void savesAndReadsUserWithRoles() {
        var saved = userRepository.saveAndFlush(
                new User("persist@vaadin.com", "Persist Me", Set.of(Role.USER)));

        var loaded = userRepository.findByEmail("persist@vaadin.com").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getName()).isEqualTo("Persist Me");
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getRoles()).containsExactly(Role.USER);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getSub()).isNull();
    }

    @Test
    void emailIsUnique() {
        userRepository.saveAndFlush(new User("dup@vaadin.com", "First", Set.of(Role.USER)));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("dup@vaadin.com", "Second", Set.of(Role.USER))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleNullSubsAreAllowed() {
        insertUser("nullsub-a@vaadin.com", null);
        insertUser("nullsub-b@vaadin.com", null);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from app_user where sub is null", Long.class))
                .isGreaterThanOrEqualTo(2L);
    }

    @Test
    void duplicateNonNullSubIsRejected() {
        insertUser("sub-a@vaadin.com", "google-sub-1");

        assertThatThrownBy(() -> insertUser("sub-b@vaadin.com", "google-sub-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bootstrapAdminSeedIsApplied() {
        var admin = userRepository.findByEmail(adminEmail).orElseThrow();

        assertThat(admin.getSub())
                .as("bootstrap admin is claimed by Google only at first login")
                .isNull();
        assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
    }

    private void insertUser(String email, String sub) {
        jdbcTemplate.update("""
                insert into app_user (sub, email, name, enabled, created_at, updated_at)
                values (?, ?, ?, true, now(), now())
                """, sub, email, "Native " + email);
    }
}
