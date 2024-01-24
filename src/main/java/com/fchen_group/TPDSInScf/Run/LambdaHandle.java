package com.fchen_group.TPDSInScf.Run;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fchen_group.TPDSInScf.Core.ChallengeData;
import com.fchen_group.TPDSInScf.Core.IntegrityAuditing;
import com.fchen_group.TPDSInScf.Core.ProofData;
import com.fchen_group.TPDSInScf.Utils.AWSCloudAPI;
import com.fchen_group.TPDSInScf.Utils.ScfData.RequestClass;
import com.fchen_group.TPDSInScf.Utils.ScfData.ResponseClass;
import com.fchen_group.TPDSInScf.Utils.YunAPI;

public class LambdaHandle implements RequestHandler<RequestClass, ResponseClass>{
    public ResponseClass handleRequest(RequestClass request, Context context){
        ChallengeData challengeData =request.challengeData;
        String bucketName = request.bucketName;
        String regionName = request.regionName;
        int DATA_SHARDS = request.DaTA_SHARDS;
        int PARITY_SHARDS = request.PARITY_SHARDS;
        String secretId = request.secretId;
        String secretKey = request.secretKey;
        //拿S3存储桶里的数据

        YunAPI yunAPI = new AWSCloudAPI(secretId, secretKey, regionName, bucketName);
//get ProofData from cloud by using challengeData from cloud
        IntegrityAuditing integrityAuditing = new IntegrityAuditing(DATA_SHARDS, PARITY_SHARDS);
        byte[][] downloadData = new byte[challengeData.index.length][DATA_SHARDS];
        byte[][] downloadParity = new byte[challengeData.index.length][PARITY_SHARDS];
        for (int i = 0; i < challengeData.index.length; i++) {
            downloadData[i] = yunAPI.downloadPartFile("sourceFile.txt", challengeData.index[i] * DATA_SHARDS, DATA_SHARDS);
            downloadParity[i] = yunAPI.downloadPartFile("parities.txt", challengeData.index[i] * PARITY_SHARDS, PARITY_SHARDS);
        }
        System.out.println("down load from COS successfully");

        ProofData proofData = integrityAuditing.prove(challengeData, downloadData, downloadParity);

        ResponseClass responseClass=new ResponseClass(proofData);

        return responseClass;
    }
}
