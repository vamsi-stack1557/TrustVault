package com.snovasys.trustvault.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(name = "account_role", nullable = false, length = 20)
    private Role role;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {}
    public AppUser(String email, String displayName, String passwordHash, Role role, boolean enabled, Instant createdAt) {
        this.email = email.toLowerCase().trim();
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
