package com.telemune.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.glue.GlueClient;

/**
 * AWS client configuration.
 * Credentials are resolved from environment variables (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY).
 * Falls back to the AWS Default Credentials chain (IAM role, instance profile, etc.)
 * when env vars are not set — never hardcode secrets in source code.
 */
@Configuration
public class AthenaConfig {

    @Value("${aws.access-key:}")
    private String accessKey;

    @Value("${aws.secret-key:}")
    private String secretKey;

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Bean
    public AthenaClient athenaClient() {
        return AthenaClient.builder()
                .region(Region.of(region))
                .credentialsProvider(resolveCredentials())
                .build();
    }

    @Bean
    public GlueClient glueClient() {
        return GlueClient.builder()
                .region(Region.of(region))
                .credentialsProvider(resolveCredentials())
                .build();
    }

    private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider resolveCredentials() {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
        }
        // Prefer IAM role / instance profile / environment chain
        return DefaultCredentialsProvider.create();
    }
}
