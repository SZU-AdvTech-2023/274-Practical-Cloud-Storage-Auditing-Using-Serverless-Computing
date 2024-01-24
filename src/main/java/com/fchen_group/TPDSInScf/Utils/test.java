package com.fchen_group.TPDSInScf.Utils;

import com.fchen_group.TPDSInScf.Run.AWSClient;

import java.io.IOException;

public class test {

    public static void cc(int[][]a){
        a[0]= new int[]{1,2};
        return;
    }



    public static void main(String[] args) throws IOException {
//        String filePath = "E:\\project\\file\\10GB.pdf";
//        AWSClient.auditTaskLocal(filePath, 255, 223, 1);

        int[][] a=new int[2][2];
        test.cc(a);
        System.out.println(a[0][0]);


    }
}
