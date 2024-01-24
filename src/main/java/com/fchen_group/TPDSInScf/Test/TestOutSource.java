package com.fchen_group.TPDSInScf.Test;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fchen_group.TPDSInScf.Core.ChallengeData;
import com.fchen_group.TPDSInScf.Core.IntegrityAuditing;
import com.fchen_group.TPDSInScf.Core.MyIntegrityAuditing;
import com.fchen_group.TPDSInScf.Core.ProofData;
import com.fchen_group.TPDSInScf.Run.AWSClient;
import com.fchen_group.TPDSInScf.Run.OldAWSClient;
import com.fchen_group.TPDSInScf.Utils.AWSCloudAPI;
import com.fchen_group.TPDSInScf.Utils.FileProcessor;
import com.fchen_group.TPDSInScf.Utils.ScfData.RequestClass;
import com.fchen_group.TPDSInScf.Utils.YunAPI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.io.*;
import java.time.Duration;
import java.util.Properties;

public class TestOutSource {
    public static LambdaClient lambdaClient;
    public static void main(String[] args) throws IOException {
        System.out.println("-------------======= 华丽的分割线 =========----------");
        //返回Java虚拟机中的堆内存总量
        long xmsMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        //返回Java虚拟机中使用的最大堆内存
        long xmxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        System.out.println("-Xms:" + xmsMemory + "M");
        System.out.println("-Xmx:" + xmxMemory + "M");
        String arch = System.getProperty("os.arch");
        String dataModel = System.getProperty("sun.arch.data.model");

        System.out.println("操作系统架构: " + arch);
        System.out.println("JVM位数: " + dataModel);


        String filePath = "E:\\project\\file\\20GB.txt";
        TestOutSource.testOutSource(filePath, 255, 223, 1);
//        TestOutSource.testOutSource0(filePath, 255, 223, 1);
//        TestOutSource.testOldSource(filePath, 255, 223, 1);
    }
//public static void main(String[] args) throws IOException {
//    String filePath = "E:\\project\\file\\1000MB.txt";
//    long start_time_genKey = System.nanoTime();
//    IntegrityAuditing integrityAuditing = new IntegrityAuditing(filePath, 255, 223);
//    long end_time_genKey = System.nanoTime();
//    System.out.println(end_time_genKey-start_time_genKey);
//}



    private  static void init(String id,String key,String region) {

// 设置超时时间
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .socketTimeout(Duration.ofSeconds(60)) // 设置为60秒，你可以根据需要调整
                .build();
        lambdaClient= LambdaClient.builder()
                .region(Region.of(region)).credentialsProvider(() -> AwsBasicCredentials.create(id, key))
                .httpClient(httpClient).build();

    }
    private  static String requestToJson(RequestClass requestObject){
        ObjectMapper objectMapper = new ObjectMapper();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(requestObject);
            return payload;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static void testOutSource(String filePath, int BLOCK_SHARDS, int DATA_SHARDS, int taskCount) throws IOException {
        long time[] = new long[5];
        long start_time_genKey = System.nanoTime();
        MyIntegrityAuditing integrityAuditing = new MyIntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
        String cosConfigFilePath = "E:\\project\\IntegrityCheckingUsingSCF-master\\Properties";
        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify ,x-Prove(从腾讯云控制台读取)
        long end_time_genKey = System.nanoTime();
        time[0] = end_time_genKey - start_time_genKey;
        //start auditing
        System.out.println("---KeyGen phase start---");
        integrityAuditing.genKey();
        System.out.println("---KeyGen phase finished---");


        //cal tags , divide source file by block,and then upload tags and file block
        System.out.println("---OutSource phase start---");
        time[1] = integrityAuditing.outSource();// tags,source file were ready ; return data process time
        System.out.println();
        System.out.println("---OutSource phase finish---");
        //这下面两是要上传文件的本地备份
        String uploadSourceFilePath = "E:\\project\\file\\sourceFile.txt";
        //奇偶校验部分
        String uploadParitiesPath = "E:\\project\\file\\parities.txt";

//        //firstly store file in local
        File uploadSourceFile = new File(uploadSourceFilePath);
        uploadSourceFile.createNewFile();
        FileProcessor.copyFile(filePath,uploadSourceFilePath);
        System.out.println("store file in local");
        //cal source file size
        long sourceFileSize = uploadSourceFile.length();

        //then store tags in local
        File uploadParities = new File(uploadParitiesPath);
        uploadParities.createNewFile();
        OutputStream osParities = new FileOutputStream(uploadParities, false);
        for (int i = 0; i < integrityAuditing.parity.length; i++) {
            osParities.write(integrityAuditing.parity[i]);
        }
        osParities.close();
        System.out.println("store tags in local");
        //cal Extra storage cost
        long extraStorageSize = uploadParities.length();
        System.out.println("extraStorageSize is " + extraStorageSize + " Bytes");


        //store the performance in local 将表现存入一个结果中，
        String performanceFilePath = new String("E:\\project\\file\\newResult.txt");
        File performanceFile = new File(performanceFilePath);

        if (performanceFile.exists() && taskCount == 1) {
            performanceFile.delete();
        }
        performanceFile.createNewFile();
        FileWriter resWriter = new FileWriter(performanceFile, true);

        String title = "Audit data size is " + String.valueOf(sourceFileSize) + ". No." + String.valueOf(taskCount) + " audit process. \r\n";
        resWriter.write(title);


        for (int i = 0; i < 5; i++) {
            resWriter.write("time[" + i + "] = " + String.valueOf(time[i]) + "  ");
        }
        resWriter.write("\r\n");
        resWriter.close();
    }

    public static void testOutSource0(String filePath, int BLOCK_SHARDS, int DATA_SHARDS, int taskCount) throws IOException {
        long time[] = new long[5];
        long start_time_genKey = System.nanoTime();
        MyIntegrityAuditing integrityAuditing = new MyIntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
        String cosConfigFilePath = "E:\\project\\IntegrityCheckingUsingSCF-master\\Properties";
        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify ,x-Prove(从腾讯云控制台读取)
        long end_time_genKey = System.nanoTime();
        time[0] = end_time_genKey - start_time_genKey;
        //start auditing
        System.out.println("---KeyGen phase start---");
        integrityAuditing.genKey();
        System.out.println("---KeyGen phase finished---");


        //cal tags , divide source file by block,and then upload tags and file block
        System.out.println("---OutSource phase start---");
        time[1] = integrityAuditing.outSource0();// tags,source file were ready ; return data process time
        System.out.println();
        System.out.println("---OutSource phase finish---");
        //这下面两是要上传文件的本地备份
        String uploadSourceFilePath = "E:\\project\\file\\sourceFile.txt";
        //奇偶校验部分
        String uploadParitiesPath = "E:\\project\\file\\parities.txt";

//        //firstly store file in local
        File uploadSourceFile = new File(uploadSourceFilePath);
        uploadSourceFile.createNewFile();
        FileProcessor.copyFile(filePath,uploadSourceFilePath);
        System.out.println("store file in local");
        //cal source file size
        long sourceFileSize = uploadSourceFile.length();

        //then store tags in local
        File uploadParities = new File(uploadParitiesPath);
        uploadParities.createNewFile();
        OutputStream osParities = new FileOutputStream(uploadParities, false);
        for (int i = 0; i < integrityAuditing.parity.length; i++) {
            osParities.write(integrityAuditing.parity[i]);
        }
        osParities.close();
        System.out.println("store tags in local");
        //cal Extra storage cost
        long extraStorageSize = uploadParities.length();
        System.out.println("extraStorageSize is " + extraStorageSize + " Bytes");


        //store the performance in local 将表现存入一个结果中，
        String performanceFilePath = new String("E:\\project\\file\\newResult0.txt");
        File performanceFile = new File(performanceFilePath);

        if (performanceFile.exists() && taskCount == 1) {
            performanceFile.delete();
        }
        performanceFile.createNewFile();
        FileWriter resWriter = new FileWriter(performanceFile, true);

        String title = "Audit data size is " + String.valueOf(sourceFileSize) + ". No." + String.valueOf(taskCount) + " audit process. \r\n";
        resWriter.write(title);


        for (int i = 0; i < 5; i++) {
            resWriter.write("time[" + i + "] = " + String.valueOf(time[i]) + "  ");
        }
        resWriter.write("\r\n");
        resWriter.close();
    }

    public static void testOldSource(String filePath, int BLOCK_SHARDS, int DATA_SHARDS, int taskCount) throws IOException {
        long time[] = new long[5];
        long start_time_genKey = System.nanoTime();
        IntegrityAuditing integrityAuditing = new IntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
        String cosConfigFilePath = "E:\\project\\IntegrityCheckingUsingSCF-master\\Properties";
        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify ,x-Prove(从腾讯云控制台读取)
        long end_time_genKey = System.nanoTime();
        time[0] = end_time_genKey - start_time_genKey;

        //start auditing
        System.out.println("---KeyGen phase start---");

        integrityAuditing.genKey();

        System.out.println("---KeyGen phase finished---");


        //cal tags , divide source file by block,and then upload tags and file block
        System.out.println("---OutSource phase start---");
        time[1] = integrityAuditing.outSource();// tags,source file were ready ; return data process time
        System.out.println();
        //这下面两是要上传文件的本地备份
        String uploadSourceFilePath = "E:\\project\\file\\sourceFile.txt";
        //奇偶校验部分
        String uploadParitiesPath = "E:\\project\\file\\parities.txt";

        //firstly store file in local
        File uploadSourceFile = new File(uploadSourceFilePath);
        uploadSourceFile.createNewFile();
        OutputStream osFile = new FileOutputStream(uploadSourceFile, false);
        for (int i = 0; i < integrityAuditing.originalData.length; i++) {
            osFile.write(integrityAuditing.originalData[i]);
        }
        osFile.close();
        System.out.println("store file in local");
        //cal source file size
        long sourceFileSize = uploadSourceFile.length();

        //then store tags in local
        File uploadParities = new File(uploadParitiesPath);
        uploadParities.createNewFile();
        OutputStream osParities = new FileOutputStream(uploadParities, false);
        for (int i = 0; i < integrityAuditing.parity.length; i++) {
            osParities.write(integrityAuditing.parity[i]);
        }
        osParities.close();
        System.out.println("store tags in local");
        //cal Extra storage cost
        long extraStorageSize = uploadParities.length();
        System.out.println("extraStorageSize is " + extraStorageSize + " Bytes");




        //store the performance in local 将表现存入一个结果中，
        String performanceFilePath = new String("E:\\project\\file\\result.txt");
        File performanceFile = new File(performanceFilePath);

        if (performanceFile.exists() && taskCount == 1) {
            performanceFile.delete();
        }
        performanceFile.createNewFile();
        FileWriter resWriter = new FileWriter(performanceFile, true);

        String title = "Audit data size is " + String.valueOf(sourceFileSize) + ". No." + String.valueOf(taskCount) + " audit process. \r\n";
        resWriter.write(title);
        for (int i = 0; i < 5; i++) {
            resWriter.write("time[" + i + "] = " + String.valueOf(time[i]) + "  ");
        }
        resWriter.write("\r\n");
        resWriter.close();
    }
}
