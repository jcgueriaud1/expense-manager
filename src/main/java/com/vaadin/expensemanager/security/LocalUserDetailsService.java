package com.vaadin.expensemanager.security;

import com.vaadin.expensemanager.user.UserRepository;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed {@link UserDetailsService} for the {@code local}/{@code test}
 * form-stub login (ADR-0012).
 *
 * <p>Looks up the {@link com.vaadin.expensemanager.user.User} by email over the
 * same records production uses, and returns an {@link AppUserDetails} whose
 * authorities come from the local roles — so the stub authenticates against the
 * real user table with production-identical authorities. Every user shares one
 * configured dev password (encoded here), matching the "email + dev password"
 * design; there is no per-user password column.
 *
 * <p>Profile-scoped: real Google OAuth replaces this in {@code staging}/
 * {@code prod} (ADR-0007, ADR-0013, Phase 1.2), where no
 * {@code UserDetailsService} is wired.
 */
@Service
@Profile({"local", "test"})
public class LocalUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final String encodedDevPassword;

    public LocalUserDetailsService(UserRepository userRepository,
            PasswordEncoder passwordEncoder, DevLoginProperties devLoginProperties) {
        this.userRepository = userRepository;
        // Encode the single shared dev password once at construction rather than
        // per login attempt.
        this.encodedDevPassword = passwordEncoder.encode(devLoginProperties.password());
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> new AppUserDetails(user, encodedDevPassword))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No local user with email " + email));
    }
}
