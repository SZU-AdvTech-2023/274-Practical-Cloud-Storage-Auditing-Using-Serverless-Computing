package com.fchen_group.TPDSInScf.Utils;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;

public interface YunAPI {
    public default void uploadFile(String localFilePath, String cloudFileName) {};
    public default byte[] downloadPartFile(String cloudFileName, long startPos, int length) {
        return new byte[0];
    };

}
