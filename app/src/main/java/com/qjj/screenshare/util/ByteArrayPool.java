package com.qjj.screenshare.util;

import java.util.LinkedList;

/**
 * 字节数组对象池，用于减少频繁申请内存导致的 GC
 */
public class ByteArrayPool {
    private static final int MAX_POOL_SIZE = 50;
    private static final LinkedList<byte[]> pool = new LinkedList<>();

    public static synchronized byte[] get(int size) {
        for (int i = 0; i < pool.size(); i++) {
            byte[] bytes = pool.get(i);
            if (bytes.length >= size) {
                pool.remove(i);
                return bytes;
            }
        }
        return new byte[size];
    }

    public static synchronized void release(byte[] bytes) {
        if (pool.size() < MAX_POOL_SIZE) {
            pool.add(bytes);
        }
    }
}
