package fr.chronoseal.capsule.seal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chronoseal.seal")
public record SealProperties(
        @DefaultValue("localhost") String host,
        @DefaultValue("50051") int port,
        @DefaultValue("3s") Duration deadline) {
}
