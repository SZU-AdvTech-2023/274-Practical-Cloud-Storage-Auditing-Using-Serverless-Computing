package com.fchen_group.TPDSInScf.Utils;

import com.fchen_group.TPDSInScf.Utils.ReedSolomon.ReedSolomon;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrentFileProcessing {
    private static final int THREAD_COUNT = 4; // 假设使用4个线程处理文件

    public static void GCalculateFile(String filePath, int SHARD_NUMBER, ReedSolomon reedSolomon, byte[][] parity) {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(SHARD_NUMBER);

        try {
            File inputFile = new File(filePath);
            FileInputStream in = new FileInputStream(inputFile);

            for (int i = 0; i < SHARD_NUMBER; i++) {
                int index = i;

                executorService.execute(() -> {
                    try {
                        byte[] data = readBlockFromFile(in, index, SHARD_NUMBER);
                        byte[] encodedParity = reedSolomon.encodeParity(data, 0, 1);
                        synchronized (parity) {
                            parity[index] = encodedParity; // 保持顺序性
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(); // 等待所有任务完成
            executorService.shutdown(); // 关闭线程池
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] readBlockFromFile(FileInputStream in, int index, int shardNumber) throws IOException {
        // 计算每个块的大小
        long totalFileSize = in.available();
        long blockSize = (totalFileSize + shardNumber - 1) / shardNumber;

        // 定位文件读取位置
        long startPos = blockSize * index;
        in.skip(startPos);

        // 读取数据块
        int bytesRead;
        byte[] data = new byte[(int) Math.min(blockSize, totalFileSize - startPos)];
        bytesRead = in.read(data);

        if (bytesRead < 0) {
            throw new IOException("Error reading file");
        }

        return data;
    }
}
