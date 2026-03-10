package dev.squadx.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Configuration for AWS S3 integration.
 * Used for session recording storage.
 * Only activated when aws.s3.access-key is set.
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "aws.s3")
@Data
public class S3Config {

    private String bucket;
    private String region;
    private String accessKey;
    private String secretKey;

    @Bean
    @ConditionalOnProperty(name = "aws.s3.access-key", matchIfMissing = false)
    public S3Client s3Client() {
        log.info("Configuring S3 client for region: {}, bucket: {}", region, bucket);

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "aws.s3.access-key", matchIfMissing = false)
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
