package com.fchen_group.TPDSInScf.Utils;



import java.io.*;
import java.util.Properties;
import java.time.Duration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;


public class AWSCloudAPI implements YunAPI{
    private String bucketName;
    private String regionName;

    private S3Client s3;

    public AWSCloudAPI(String cosConfigFilePath) {
        String AWSSecretKey =null;
        String AWSSecretId = null;

        //read configuration file
        try {
            FileInputStream propertiesFIS = new FileInputStream(cosConfigFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            regionName = properties.getProperty("AWSRegionName");
            this.bucketName = properties.getProperty("AWSBucketName");
            AWSSecretId =properties.getProperty("AWSSecretId");
            AWSSecretKey =properties.getProperty("AWSSecretKey");
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert regionName != null;
        assert bucketName != null;
        assert AWSSecretId != null;
        assert AWSSecretKey != null;

        String finalAWSSecretId = AWSSecretId;
        String finalAWSSecretKey = AWSSecretKey;
        s3 = S3Client.builder()
                .region(Region.of(regionName))
                .credentialsProvider(() -> AwsBasicCredentials.create(finalAWSSecretId, finalAWSSecretKey))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(20)) // 连接超时时间
                        .maxConnections(1))
                .build();
//        s3 = S3Client.builder()
//                .region(Region.of(regionName))
//                .credentialsProvider(() -> AwsBasicCredentials.create(finalAWSSecretId, finalAWSSecretKey))
//                .httpClientBuilder(ApacheHttpClient.builder()
//                        .connectionTimeout(Duration.ofSeconds(20))) // 连接超时时间
//                .build();
    }

    public AWSCloudAPI(String secretId, String secretKey,String regionName, String bucketName) {
        this.bucketName = bucketName;
        this.regionName = regionName;

        s3 = S3Client.builder()
                .region(Region.of(regionName))
                .credentialsProvider(() -> AwsBasicCredentials.create(secretId, secretKey))
                .build();
    }

        public void uploadFile(String localFilePath, String cloudFileName) {

        File localFile = new File(localFilePath);
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(cloudFileName)
                .build();
        s3.putObject(objectRequest,RequestBody.fromFile(localFile));
//        s3.putObject(objectRequest,RequestBody.fromFile(localFile));
    }

    public byte[] downloadPartFile(String cloudFileName, long startPos, int length) {

        // 计算结束位置
        long endPos = startPos + length - 1;

        // 创建 Range
        String range = "bytes=" + startPos + "-" + endPos;

        GetObjectRequest getObjectRequest=GetObjectRequest.builder().bucket(bucketName).key(cloudFileName).range(range).build();
        ResponseBytes<GetObjectResponse> objectBytes=s3.getObjectAsBytes(getObjectRequest);
        byte[] data = objectBytes.asByteArray();
        return data;
    }

//    public static void main(String[] args) {
//        String cosConfigFilePath = "E:\\project\\IntegrityCheckingUsingSCF-master\\Properties";
//        AWSCloudAPI awsCloudAPI = new AWSCloudAPI(cosConfigFilePath);
//
//        long startTime;
//        long[] uploadTimes = new long[6];
////        String[] fileSizes = {"20MB", "50MB", "100MB", "200MB", "500MB", "2000MB"};
//        String[] fileSizes = {"20MB", "50MB"};
////        String[] fileSizes = {"20MB", "50MB", "100MB", "200MB"};
//        for (int i = 0; i < fileSizes.length; i++) {
//            startTime = System.nanoTime();
//            awsCloudAPI.uploadFile("E:\\project\\file\\" + fileSizes[i] + ".txt", fileSizes[i] + ".txt");
//            uploadTimes[i] = System.nanoTime() - startTime;
//        }
//
//        // Write the execution times with file sizes to a text file
//        try {
//            FileWriter writer = new FileWriter("E:\\project\\file\\myUploadResult.txt");
//            for (int i = 0; i < fileSizes.length; i++) {
//                writer.write(fileSizes[i] + " file: " + uploadTimes[i] + " ns\n");
//            }
//            writer.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public static void main(String[] args) {
        String cosConfigFilePath = "E:\\project\\IntegrityCheckingUsingSCF-master\\Properties";
        AWSCloudAPI awsCloudAPI = new AWSCloudAPI(cosConfigFilePath);
        awsCloudAPI.uploadFile("E:\\project\\file\\1000MB.txt", "1000MB.txt");
    }
}
