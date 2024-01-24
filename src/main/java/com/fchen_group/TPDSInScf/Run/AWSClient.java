package com.fchen_group.TPDSInScf.Run;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fchen_group.TPDSInScf.Core.*;
import com.fchen_group.TPDSInScf.Utils.AWSCloudAPI;
import com.fchen_group.TPDSInScf.Utils.CloudAPI;
import com.fchen_group.TPDSInScf.Utils.FileProcessor;
import com.fchen_group.TPDSInScf.Utils.ScfData.RequestClass;
import com.fchen_group.TPDSInScf.Utils.YunAPI;
import org.apache.http.client.CredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.http.SdkHttpClient;



import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;


/**此类定义客户端在审核过程中的操作,即客户端发起一个审计
 * This class define the action of the client in the audit process
 *
 * @author jquan, fchen-group of SZU
 * @Version 2.0 2021.12.02
 * @Time 2021.3 - 2021.12
 */

public class AWSClient {

    public static LambdaClient lambdaClient;

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



    /**
     * this method used to check the correctness of the protocol
     */
    //delete
    public static void main(String args[]) throws IOException {

        String filePath = "E:\\project\\file\\100MB.txt";
        auditTask(filePath, 255, 223, 1);
    }

    /**
     * the action of the client in the audit process
     * @param filePath
     * @param BLOCK_SHARDS PROTOCOL PARAMETER
     * @param DATA_SHARDS PROTOCOL PARAMETER
     * @param taskCount  used in this method for control when writing system result
     * */
    public static void auditTask(String filePath, int BLOCK_SHARDS, int DATA_SHARDS, int taskCount) throws IOException {

        MyIntegrityAuditing integrityAuditing = new MyIntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
        String cosConfigFilePath = "E:\\project\\IntegrityCheckingUsingSCF-master\\Properties";
        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify ,x-Prove(从腾讯云控制台读取)
        long time[] = new long[5];


        //start auditing
        System.out.println("---KeyGen phase start---");
        long start_time_genKey = System.nanoTime();
        integrityAuditing.genKey();
        long end_time_genKey = System.nanoTime();
        time[0] = end_time_genKey - start_time_genKey;
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
//        try {
//            byte[] tmp = new byte[223];
//            File inputFile = new File(filePath);
//            FileInputStream in = null;
//            in = new FileInputStream(inputFile);
//            int SHARD_NUMBER=integrityAuditing.SHARD_NUMBER;
//            for (int i = 0; i < SHARD_NUMBER; i++) {
//                in.read(tmp);
//                osFile.write(tmp);
////                System.out.println(i+" / "+SHARD_NUMBER);
//            }
//            in.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        osFile.close();
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


        //upload File and tags to COS
        long start_time_upload = System.nanoTime();
        YunAPI yunAPI=new AWSCloudAPI(cosConfigFilePath);


        yunAPI.uploadFile(uploadSourceFilePath, "sourceFile.txt");
        yunAPI.uploadFile(uploadParitiesPath, "parities.txt");
        System.out.println("upload File and tags to COS");
            long end_time_upload = System.nanoTime();
        time[2] = end_time_upload - start_time_upload;
        System.out.println("---OutSource phase finished---");
        System.out.println("time2:"+time[2]);

        //这里建立了SCF的连接，需要看看这个foo是不是SCF要求的



        //prepare challengeData
        System.out.println("---Audit phase start---");
        long start_time_audit = System.nanoTime();
        ChallengeData challengeData = integrityAuditing.audit(460);
        long end_time_audit = System.nanoTime();
        time[3] = end_time_audit - start_time_audit;


        String AWSSecretKey =null;
        String AWSSecretId = null;
        String regionName =null;
        String bucketName =null;
        String functionName =null;


        //read configuration file
        try {
            FileInputStream propertiesFIS = new FileInputStream(cosConfigFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            regionName = properties.getProperty("AWSRegionName");
            bucketName = properties.getProperty("AWSBucketName");
            AWSSecretId =properties.getProperty("AWSSecretId");
            AWSSecretKey =properties.getProperty("AWSSecretKey");
            functionName =properties.getProperty("AWSFunctionName");
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert regionName != null;
        assert bucketName != null;
        assert AWSSecretId != null;
        assert AWSSecretKey != null;
        int PARITY_SHARDS=BLOCK_SHARDS-DATA_SHARDS;

        RequestClass requestObject=new RequestClass(PARITY_SHARDS,DATA_SHARDS,challengeData,bucketName,regionName,AWSSecretId,AWSSecretKey);
        String payload=requestToJson(requestObject);
        System.out.println(payload);

//准备调用请求
        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payload))
                .build();

        //获取客户端:
        AWSClient.init(AWSSecretId,AWSSecretKey,regionName);



// 调用 Lambda 函数并处理调用的响应
        InvokeResponse response = AWSClient.lambdaClient.invoke(request);


        System.out.println("---Audit phase finished---");
        System.out.println("Waiting SCF return Proof for verifying");

        //read response
        String responseDataStr = response.payload().asUtf8String();
        System.out.println(responseDataStr);


        //Extract the serialized proof data in the body
        int indexOfDataProof = responseDataStr.indexOf("dataProof");
        //Consider the location of the symbol placeholder,for more detailed can be observed in the print sequence
        String targetStr = responseDataStr.substring(indexOfDataProof - 2, responseDataStr.length() - 1);

        ProofData proofData = JSON.parseObject(targetStr, ProofData.class);

        // System.out.println("Get proofData content:" + proofData.dataProof.toString() + "\t and" + proofData.parityProof.toString());
        //write proofData to file，calculate the communication cost
        String proofDataStoragePath = "E:\\project\\file\\proofData.txt";
        File proofDataCost = new File(proofDataStoragePath);
        proofDataCost.createNewFile();
        OutputStream osProofData = new FileOutputStream(proofDataCost, false);
        osProofData.write(proofData.parityProof);
        osProofData.write(proofData.dataProof);
        osProofData.close();

        //cal communication cost
        long proofDataSize = proofDataCost.length();
        System.out.println("proofDataSize is " + proofDataSize + " Bytes");


        //execute verify parse
        System.out.println("---Verify phase start---");
        long start_time_verify = System.nanoTime();
        if (integrityAuditing.verify(challengeData, proofData)) {
            System.out.println("---Verify phase finished---");
            System.out.println("The data is intact in the cloud.The auditing process is success!");
        }else {
            System.out.println("The data no no no");
        }
        long end_time_verify = System.nanoTime();
        time[4] = end_time_verify - start_time_verify;


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

        resWriter.write("StorageCost " + String.valueOf(extraStorageSize) + "  CommunicationCost " + String.valueOf(proofDataSize) + "\r\n");
        for (int i = 0; i < 5; i++) {
            resWriter.write("time[" + i + "] = " + String.valueOf(time[i]) + "  ");
        }
        resWriter.write("\r\n");
        resWriter.close();
    }
//    public static void auditTaskLocal(String filePath, int BLOCK_SHARDS, int DATA_SHARDS, int taskCount) throws IOException {
//        IntegrityAuditing integrityAuditing = new IntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
//        String cosConfigFilePath = "E:\\project\\IntegrityCheckingUsingSCF-master\\Properties";
//        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify ,x-Prove(从腾讯云控制台读取)
//        long time[] = new long[5];
//
//
//        //start auditing
//        System.out.println("---KeyGen phase start---");
//        long start_time_genKey = System.nanoTime();
//        integrityAuditing.genKey();
//        long end_time_genKey = System.nanoTime();
//        time[0] = end_time_genKey - start_time_genKey;
//        System.out.println("---KeyGen phase finished---");
//
//
//        //cal tags , divide source file by block,and then upload tags and file block
//        System.out.println("---OutSource phase start---");
//        time[1] = integrityAuditing.outSource();// tags,source file were ready ; return data process time
//        //这下面两是要上传文件的本地备份
//        String uploadSourceFilePath = "E:\\project\\file\\sourceFile.txt";
//        //奇偶校验部分
//        String uploadParitiesPath = "E:\\project\\file\\parities.txt";
//
//        //firstly store file in local
//        File uploadSourceFile = new File(uploadSourceFilePath);
//        uploadSourceFile.createNewFile();
//        OutputStream osFile = new FileOutputStream(uploadSourceFile, false);
//        for (int i = 0; i < integrityAuditing.originalData.length; i++) {
//            osFile.write(integrityAuditing.originalData[i]);
//        }
//        osFile.close();
//        System.out.println("store file in local");
//        //cal source file size
//        long sourceFileSize = uploadSourceFile.length();
//
//        //then store tags in local
//        File uploadParities = new File(uploadParitiesPath);
//        uploadParities.createNewFile();
//        OutputStream osParities = new FileOutputStream(uploadParities, false);
//        for (int i = 0; i < integrityAuditing.parity.length; i++) {
//            osParities.write(integrityAuditing.parity[i]);
//        }
//        osParities.close();
//        System.out.println("store tags in local");
//        //cal Extra storage cost
//        long extraStorageSize = uploadParities.length();
//        System.out.println("extraStorageSize is " + extraStorageSize + " Bytes");
//
//
//        //upload File and tags to COS
//        long start_time_upload = System.nanoTime();
//        YunAPI yunAPI=new AWSCloudAPI(cosConfigFilePath);
//
//
////        yunAPI.uploadFile(uploadSourceFilePath, "sourceFile.txt");
////        yunAPI.uploadFile(uploadParitiesPath, "parities.txt");
//        System.out.println("upload File and tags to COS");
//        long end_time_upload = System.nanoTime();
//        time[2] = end_time_upload - start_time_upload;
//        System.out.println("---OutSource phase finished---");
//        System.out.println("time2:"+time[2]);
//
//        //这里建立了SCF的连接，需要看看这个foo是不是SCF要求的
//
//
//
//        //prepare challengeData
//        System.out.println("---Audit phase start---");
//        long start_time_audit = System.nanoTime();
//        ChallengeData challengeData = integrityAuditing.audit(460);
//        long end_time_audit = System.nanoTime();
//        time[3] = end_time_audit - start_time_audit;
//
//
//        //store the performance in local 将表现存入一个结果中，
//        String performanceFilePath = new String("E:\\project\\file\\result.txt");
//        File performanceFile = new File(performanceFilePath);
//
//        if (performanceFile.exists() && taskCount == 1) {
//            performanceFile.delete();
//        }
//        performanceFile.createNewFile();
//        FileWriter resWriter = new FileWriter(performanceFile, true);
//
//        String title = "Audit data size is " + String.valueOf(sourceFileSize) + ". No." + String.valueOf(taskCount) + " audit process. \r\n";
//        resWriter.write(title);
//
////        resWriter.write("StorageCost " + String.valueOf(extraStorageSize) + "  CommunicationCost " + String.valueOf(proofDataSize) + "\r\n");
//        for (int i = 0; i < 5; i++) {
//            resWriter.write("time[" + i + "] = " + String.valueOf(time[i]) + "  ");
//        }
//        resWriter.write("\r\n");
//        resWriter.close();
//    }
}
