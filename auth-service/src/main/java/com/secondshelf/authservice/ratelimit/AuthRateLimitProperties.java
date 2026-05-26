package com.secondshelf.authservice.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "auth.rate-limit")
public class AuthRateLimitProperties {

    private boolean enabled = true;

    @Valid
    private EndpointLimit login = new EndpointLimit(10, 10, Duration.ofMinutes(1));

    @Valid
    private EndpointLimit register = new EndpointLimit(5, 5, Duration.ofMinutes(1));

    @Valid
    private RefreshEndpointLimit refresh = new RefreshEndpointLimit(30, 30, Duration.ofMinutes(1), false);

    @Getter
    @Setter
    public static class EndpointLimit {

        @Min(1)
        private int capacity;

        @Min(1)
        private int refillTokens;

        @NotNull
        private Duration refillPeriod;

        public EndpointLimit() {
        }

        public EndpointLimit(int capacity, int refillTokens, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriod = refillPeriod;
        }
    }

    @Getter
    @Setter
    public static class RefreshEndpointLimit extends EndpointLimit {

        private boolean includeTokenFingerprint;

        public RefreshEndpointLimit() {
        }

        public RefreshEndpointLimit(int capacity,
                                    int refillTokens,
                                    Duration refillPeriod,
                                    boolean includeTokenFingerprint) {
            super(capacity, refillTokens, refillPeriod);
            this.includeTokenFingerprint = includeTokenFingerprint;
        }
    }
}
