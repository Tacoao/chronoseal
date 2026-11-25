package fr.chronoseal.capsule.seal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SealProperties.class)
public class SealGrpcConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel sealChannel(SealProperties properties) {
        // Pas de TLS ici, c'est le mTLS d'Istio qui chiffre dans le cluster.
        return ManagedChannelBuilder.forAddress(properties.host(), properties.port())
                .usePlaintext()
                .build();
    }
}
