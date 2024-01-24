package com.fchen_group.TPDSInScf.Utils.ScfData;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class MultipartUploadExample {

    private static final int MB = 1024 * 1024;
    private static final int PART_SIZE = 5 * MB; // 分段大小

    public static void main(String[] args) {
        String secretId = "YOUR_SECRET_ID";
        String secretKey = "YOUR_SECRET_KEY";
        String regionName = "YOUR_REGION";
        String bucketName = "YOUR_BUCKET_NAME";

        S3Client s3 = S3Client.builder()
                .region(Region.of(regionName))
                .credentialsProvider(() -> AwsBasicCredentials.create(secretId, secretKey))
                .build();

        File file = new File("path_to_your_file.txt");
        String keyName = "file.txt"; // S3上的文件名

        initiateMultipartUpload(s3, bucketName, keyName);
        uploadParts(s3, bucketName, keyName, file);
    }

    private static void initiateMultipartUpload(S3Client s3, String bucketName, String keyName) {
        CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        CreateMultipartUploadResponse response = s3.createMultipartUpload(createMultipartUploadRequest);
        System.out.println("Upload ID: " + response.uploadId());
    }

    private static void uploadParts(S3Client s3, String bucketName, String keyName, File file) {
        List<CompletedPart> completedParts = new ArrayList<>();

        try {
            long contentLength = file.length();
            long filePosition = 0;
            int partNumber = 1;

            while (filePosition < contentLength) {
                long partSize = Math.min(PART_SIZE, contentLength - filePosition);

                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(keyName)
                        .uploadId("YOUR_UPLOAD_ID") // 上传ID，从 initiateMultipartUpload 中获得
                        .partNumber(partNumber)
                        .build();

                ByteBuffer buffer = ByteBuffer.allocate((int) partSize);
                java.nio.file.Files.newByteChannel(Paths.get(file.getAbsolutePath()), StandardOpenOption.READ)
                        .position(filePosition)
                        .read(buffer);

                buffer.flip();
                UploadPartResponse response = s3.uploadPart(uploadPartRequest, RequestBody.fromByteBuffer(buffer));
                completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(response.eTag())
                        .build());

                filePosition += partSize;
                partNumber++;
            }

            CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .uploadId("YOUR_UPLOAD_ID") // 上传ID，从 initiateMultipartUpload 中获得
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();

            s3.completeMultipartUpload(completeMultipartUploadRequest);
            System.out.println("File uploaded successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
