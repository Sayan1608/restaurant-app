package com.restaurantapp.auth;

import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.io.Serializable;

public final class AuthenticatedUser implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final Instant createdAt;

    private AuthenticatedUser(UserAccount account) {
        this.id = account.getId();
        this.name = account.getName();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
        this.role = account.getRole();
        this.createdAt = account.getCreatedAt();
    }

    public static AuthenticatedUser from(UserAccount account) {
        return new AuthenticatedUser(account);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // SessionRegistry keys its sessions by principal, so two loads of the same account must be equal.
    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof AuthenticatedUser user && Objects.equals(id, user.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
