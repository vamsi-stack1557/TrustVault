package com.snovasys.trustvault.model;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_chain_state")
public class AuditChainState {
    @Id private Integer id;
    @Column(name = "last_hash", nullable = false, length = 64) private String lastHash;
    protected AuditChainState() {}
    public Integer getId() { return id; }
    public String getLastHash() { return lastHash; }
    public void setLastHash(String lastHash) { this.lastHash = lastHash; }
}
