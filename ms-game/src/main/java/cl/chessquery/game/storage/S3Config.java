package cl.chessquery.game.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${cloud.aws.s3.endpoint:}")
    private String endpoint;

    @Value("${cloud.aws.s3.public-endpoint:}")
    private String publicEndpoint;

    @Value("${cloud.aws.credentials.access-key:minioadmin}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key:minioadmin}")
    private String secretKey;

    @Value("${cloud.aws.region.static:us-east-1}")
    private String region;

    @Bean
    public S3Client s3Client() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credentials)
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                   .forcePathStyle(true); // requerido para MinIO
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        S3Presigner.Builder builder = S3Presigner.builder()
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());

        String presignEndpoint = (publicEndpoint != null && !publicEndpoint.isBlank())
                ? publicEndpoint
                : endpoint;
        if (presignEndpoint != null && !presignEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(presignEndpoint));
        }

        return builder.build();
    }
}
