package com.snovasys.trustvault.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "actor_id") private Long actorId;
    @Column(name = "actor_email", nullable = false, length = 254) private String actorEmail;
    @Column(name = "event_action", nullable = false, length = 80) private String action;
    @Column(name = "resource_type", nullable = false, length = 40) private String resourceType;
    @Column(name = "resource_id", length = 100) private String resourceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Outcome outcome;
    @Column(length = 2000) private String details;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "previous_hash", nullable = false, length = 64) private String previousHash;
    @Column(name = "event_hash", nullable = false, length = 64) private String eventHash;
    protected AuditEvent() {}
    public AuditEvent(Instant createdAt, Long actorId, String actorEmail, String action, String resourceType,
                      String resourceId, Outcome outcome, String details, String ipAddress, String userAgent, String previousHash) {
        this.createdAt=createdAt; this.actorId=actorId; this.actorEmail=actorEmail; this.action=action;
        this.resourceType=resourceType; this.resourceId=resourceId; this.outcome=outcome; this.details=details;
        this.ipAddress=ipAddress; this.userAgent=userAgent; this.previousHash=previousHash;
    }
    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public Outcome getOutcome() { return outcome; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getPreviousHash() { return previousHash; }
    public String getEventHash() { return eventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
}
