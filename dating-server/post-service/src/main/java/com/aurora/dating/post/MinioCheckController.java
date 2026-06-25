package com.aurora.dating.post;

import com.aurora.dating.post.storage.StorageProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@RestController
public class MinioCheckController {

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    public MinioCheckController(S3Client s3Client, StorageProperties storageProperties) {
        this.s3Client = s3Client;
        this.storageProperties = storageProperties;
    }

    @GetMapping("/internal/check/minio")
    public Map<String, String> checkMinio() {
        s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(storageProperties.getBucket())
                .build());

        return Map.of(
                "minio", "ok",
                "endpoint", storageProperties.getEndpoint(),
                "bucket", storageProperties.getBucket()
        );
    }
}
