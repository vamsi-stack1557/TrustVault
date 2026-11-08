package com.snovasys.trustvault.service;

import com.snovasys.trustvault.dto.ApiDtos.AuditResponse;
import com.snovasys.trustvault.dto.ApiDtos.AuditVerifyResponse;
import com.snovasys.trustvault.model.*;
import com.snovasys.trustvault.repo.AuditChainStateRepository;
import com.snovasys.trustvault.repo.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuditService {
    private static final String GENESIS = "GENESIS";
    private final AuditEventRepository events;
    private final AuditChainStateRepository chain;
    private final byte[] key;

    public AuditService(AuditEventRepository events, AuditChainStateRepository chain,
                        @Value("${app.audit-hmac-key}") String key) {
        this.events = events;
        this.chain = chain;
        this.key = key.getBytes(StandardCharsets.UTF_8);
        if (this.key.length < 32) throw new IllegalStateException("TRUSTVAULT_AUDIT_HMAC_KEY must be at least 32 bytes");
    }

    @Transactional
    public void success(AppUser actor, String action, String type, String resourceId, String details) {
        append(actor == null ? null : actor.getId(), actor == null ? "system" : actor.getEmail(),
                action, type, resourceId, Outcome.SUCCESS, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(AppUser actor, String action, String type, String resourceId, String details) {
        append(actor == null ? null : actor.getId(), actor == null ? "anonymous" : actor.getEmail(),
                action, type, resourceId, Outcome.DENIED, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String actorEmail, String action, String type, String resourceId, String details) {
        append(null, safe(actorEmail, 254), action, type, resourceId, Outcome.FAILURE, details);
    }

    private void append(Long actorId, String actorEmail, String action, String type, String resourceId,
                        Outcome outcome, String details) {
        AuditChainState state = chain.lockSingleton().orElseThrow(() -> new IllegalStateException("Audit chain state missing"));
        RequestMeta meta = requestMeta();
        AuditEvent event = new AuditEvent(Instant.now().truncatedTo(ChronoUnit.MICROS), actorId, safe(actorEmail, 254), safe(action, 80),
                safe(type, 40), safe(resourceId, 100), outcome, safe(details, 2000),
                safe(meta.ip(), 64), safe(meta.userAgent(), 500), state.getLastHash());
        event.setEventHash(sign(event));
        events.saveAndFlush(event);
        state.setLastHash(event.getEventHash());
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> latest(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        return events.findAllByOrderByIdDesc(PageRequest.of(0, limit)).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AuditVerifyResponse verify() {
        List<AuditEvent> all = events.findAllByOrderByIdAsc();
        String previous = GENESIS;
        for (AuditEvent event : all) {
            if (!previous.equals(event.getPreviousHash())) {
                return new AuditVerifyResponse(false, all.size(), previous, currentStoredHash(), "Broken previous-hash link at event " + event.getId());
            }
            String actual = sign(event);
            if (!constantEquals(actual, event.getEventHash())) {
                return new AuditVerifyResponse(false, all.size(), actual, event.getEventHash(), "HMAC verification failed at event " + event.getId());
            }
            previous = actual;
        }
        String stored = currentStoredHash();
        boolean valid = constantEquals(previous, stored);
        return new AuditVerifyResponse(valid, all.size(), previous, stored,
                valid ? "Audit chain is internally consistent" : "Audit chain state does not match the event history");
    }

    private String currentStoredHash() {
        return chain.findById(1).map(AuditChainState::getLastHash).orElse("MISSING");
    }
    private AuditResponse toResponse(AuditEvent e) {
        return new AuditResponse(e.getId(), e.getCreatedAt(), e.getActorId(), e.getActorEmail(), e.getAction(),
                e.getResourceType(), e.getResourceId(), e.getOutcome(), e.getDetails(), e.getIpAddress(),
                e.getPreviousHash(), e.getEventHash());
    }
    private String sign(AuditEvent e) {
        String canonical = pack(e.getCreatedAt(), e.getActorId(), e.getActorEmail(), e.getAction(),
                e.getResourceType(), e.getResourceId(), e.getOutcome(), e.getDetails(), e.getIpAddress(),
                e.getUserAgent(), e.getPreviousHash());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Unable to sign audit event", ex); }
    }
    private String pack(Object... values) {
        StringBuilder out = new StringBuilder();
        for (Object value : values) {
            String s = value == null ? "" : value.toString();
            out.append(s.length()).append(':').append(s).append('|');
        }
        return out.toString();
    }
    private boolean constantEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    private RequestMeta requestMeta() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return new RequestMeta(request.getRemoteAddr(), request.getHeader("User-Agent"));
        }
        return new RequestMeta("unknown", "unknown");
    }
    private String safe(String value, int max) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\\r\\n\\u0000]", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
    private record RequestMeta(String ip, String userAgent) {}
}
