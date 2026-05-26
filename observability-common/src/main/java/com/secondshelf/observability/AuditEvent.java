package com.secondshelf.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AuditEvent {

    private final String eventType;
    private final Long actorUserId;
    private final Long targetUserId;
    private final String entityId;
    private final AuditOutcome outcome;
    private final String reason;
    private final String errorCode;
    private final Map<String, Object> attributes;

    private AuditEvent(Builder builder) {
        this.eventType = builder.eventType;
        this.actorUserId = builder.actorUserId;
        this.targetUserId = builder.targetUserId;
        this.entityId = builder.entityId;
        this.outcome = builder.outcome;
        this.reason = builder.reason;
        this.errorCode = builder.errorCode;
        this.attributes = Map.copyOf(builder.attributes);
    }

    public static Builder builder(String eventType, AuditOutcome outcome) {
        return new Builder(eventType, outcome);
    }

    public String getEventType() {
        return eventType;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getEntityId() {
        return entityId;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public static final class Builder {
        private final String eventType;
        private final AuditOutcome outcome;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private Long actorUserId;
        private Long targetUserId;
        private String entityId;
        private String reason;
        private String errorCode;

        private Builder(String eventType, AuditOutcome outcome) {
            this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
            this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        }

        public Builder actorUserId(Long actorUserId) {
            this.actorUserId = actorUserId;
            return this;
        }

        public Builder targetUserId(Long targetUserId) {
            this.targetUserId = targetUserId;
            return this;
        }

        public Builder entityId(Object entityId) {
            this.entityId = entityId == null ? null : String.valueOf(entityId);
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
