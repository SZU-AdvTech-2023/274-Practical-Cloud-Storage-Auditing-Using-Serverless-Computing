package com.fchen_group.TPDSInScf.Utils;

import com.fchen_group.TPDSInScf.Utils.ReedSolomon.ReedSolomon;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class FileProcessor {
    private static final int THREAD_COUNT = 16; // 设置线程池大小
    private static ExecutorService executorService;
    private static final int BUFFER_SIZE = 8 * 1024 * 1024; // 8MB buffer size

    static {
        executorService = Executors.newFixedThreadPool(THREAD_COUNT);
    }

//    public static void copyFile(String filePath, String outputFilePath) {
//        try (InputStream inputStream = new FileInputStream(filePath);
//             OutputStream outputStream = new FileOutputStream(outputFilePath)) {
//
//            byte[] buffer = new byte[BUFFER_SIZE];
//            int bytesRead;
//            while ((bytesRead = inputStream.read(buffer)) != -1) {
//                final byte[] data = new byte[bytesRead];
//                System.arraycopy(buffer, 0, data, 0, bytesRead);
//
//                executorService.execute(() -> {
//                    try {
//                        outputStream.write(data);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                });
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            executorService.shutdown();
//        }
//    }

    public static void copyFile(String filePath, String outputFilePath) throws IOException {
        int bufferSize = 8 * 1024; // 缓冲区大小，可以根据需要调整

        try (InputStream inputStream = new FileInputStream(filePath);
             OutputStream outputStream = new FileOutputStream(outputFilePath)) {

            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }


//    public static void calculateFile(String filePath, int SHARD_NUMBER, ReedSolomon reedSolomon, byte[][] parity) {
//        CountDownLatch latch = new CountDownLatch(SHARD_NUMBER); // 使用CountDownLatch来追踪任务完成数量
//        Semaphore[] semaphores = new Semaphore[THREAD_COUNT];
//        for (int i = 0; i < THREAD_COUNT; i++) {
//            semaphores[i] = new Semaphore(1); // 初始化每个线程的信号量，初始值为1
//        }
//        byte[][] tmpArray = new byte[THREAD_COUNT][223];
//        try {
//            File inputFile = new File(filePath);
//            FileInputStream in = null;
//            in = new FileInputStream(inputFile);
//            for (int i = 0; i < SHARD_NUMBER; i++) {
//                int tmp1=i%THREAD_COUNT;
//                semaphores[tmp1].acquire();
//                in.read(tmpArray[tmp1]);
//
//                int index = i;
//                int tmp2=tmp1;
//                executorService.execute(() -> {
//
//                    parity[index] = reedSolomon.encodeParity(tmpArray[tmp2], 0, 1);
//                    System.out.println(index / (SHARD_NUMBER / 100));
//                    semaphores[tmp2].release(); // 释放信号量
//                    latch.countDown(); // 每次任务完成时减少CountDownLatch的数量
//                });
//            }
//            latch.await(); // 等待CountDownLatch的数量减少到0，即所有任务完成
//        } catch (IOException | InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public static void calculateFile(String filePath, int SHARD_NUMBER, ReedSolomon reedSolomon, byte[][] parity) {
        CountDownLatch latch = new CountDownLatch(SHARD_NUMBER); // 使用CountDownLatch来追踪任务完成数量
        Semaphore[] semaphores = new Semaphore[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            semaphores[i] = new Semaphore(1); // 初始化每个线程的信号量，初始值为0
        }
        byte[][] tmpArray = new byte[THREAD_COUNT][223];

        try {
            File inputFile = new File(filePath);
            FileInputStream in = null;
            in = new FileInputStream(inputFile);
            for (int i = 0; i < SHARD_NUMBER; i++) {
                int tmp1 = i % THREAD_COUNT;
                semaphores[tmp1].acquire(); // 获取信号量
                in.read(tmpArray[tmp1]);

                int index = i;
                int tmp2 = tmp1;
                executorService.execute(() -> {
                    parity[index] = reedSolomon.encodeParity(tmpArray[tmp2], 0, 1);
                    System.out.println(index / (SHARD_NUMBER / 100));
                    semaphores[tmp2].release(); // 释放信号量
                    latch.countDown(); // 每次任务完成时减少CountDownLatch的数量
                });
            }
            latch.await(); // 等待CountDownLatch的数量减少到0，即所有任务完成
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public static void newcalculateFile(String filePath, int SHARD_NUMBER, ReedSolomon reedSolomon, byte[][] parity){
        try (FileInputStream fileInputStream = new FileInputStream((filePath))) {
            CountDownLatch latch = new CountDownLatch(SHARD_NUMBER);
            byte[] buffer = new byte[256];
            int bytesReadTotal = 0;
            int bytesRemaining = 223;
            int i=0;

            for(;i<SHARD_NUMBER;i++){
                fileInputStream.read(buffer, 0, 223);

                final byte[] data = new byte[223];
                System.arraycopy(buffer, 0, data, 0, 223);
                final int bytesReadFinal = bytesReadTotal;
                int index=i;
                executorService.execute(() ->
                {
                    parity[index]=reedSolomon.encodeParity(data,0,1);
                    latch.countDown();
                });
            }
            latch.await();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    public static void GCalculateFile(String filePath, int SHARD_NUMBER, ReedSolomon reedSolomon, byte[][] parity) {
        try (FileInputStream fileInputStream = new FileInputStream((filePath))) {

            byte[] buffer = new byte[223];
            int bytesReadTotal = 0;
            int i=0;
            for(;i<SHARD_NUMBER;i++){
                fileInputStream.read(buffer, 0, 223);
                parity[i]=reedSolomon.encodeParity(buffer,0,1);


//                final byte[] data = new byte[223];
//                System.arraycopy(buffer, 0, data, 0, 223);
//                final int bytesReadFinal = bytesReadTotal;
//                int index=i;
//                executorService.execute(() ->
//                {
//                    parity[index]=reedSolomon.encodeParity(data,0,1);
//                    latch.countDown();
//                });
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static byte[] readBlockFromFile(String filePath, long position) {
        byte[] data = new byte[223];

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            file.seek(position);
            int bytesRead = file.read(data, 0, 223);
            if (bytesRead == -1) {
                throw new EOFException("End of file reached before reading requested length.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }


    public static void main(String[] args) {}
}
