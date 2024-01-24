package com.fchen_group.TPDSInScf.Utils.ScfData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fchen_group.TPDSInScf.Core.ChallengeData;

public class RequestClass {
    public int PARITY_SHARDS;
    public int DaTA_SHARDS;
    public ChallengeData challengeData;
    public String bucketName;
    public String regionName;
    public String secretId;
    public String secretKey;

    // 无参构造函数
    public RequestClass() {
    }

    // 添加注解指定 JSON 属性名与 Java 对象属性名的映射关系
    @JsonCreator
    public RequestClass(
            @JsonProperty("PARITY_SHARDS") int PARITY_SHARDS,
            @JsonProperty("DaTA_SHARDS") int DaTA_SHARDS,
            @JsonProperty("challengeData") ChallengeData challengeData,
            @JsonProperty("bucketName") String bucketName,
            @JsonProperty("regionName") String regionName,
            @JsonProperty("secretId") String secretId,
            @JsonProperty("secretKey") String secretKey
    ) {
        this.PARITY_SHARDS = PARITY_SHARDS;
        this.DaTA_SHARDS = DaTA_SHARDS;
        this.challengeData = challengeData;
        this.bucketName = bucketName;
        this.regionName = regionName;
        this.secretId = secretId;
        this.secretKey = secretKey;
    }
}
