package com.zhengjianting.nio.buffer;

import java.nio.ByteBuffer;

public class BufferConstruct {
    public static void allocate() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(100);
        print(byteBuffer);
    }

    public static void wrap() {
        byte[] bytes = new byte[100];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 12, 42);

        System.out.println("before clear");
        print(byteBuffer);

        byteBuffer.clear();

        System.out.println("after clear");
        print(byteBuffer);
    }

    public static void print(ByteBuffer byteBuffer) {
        System.out.println("position: " + byteBuffer.position());
        System.out.println("limit: " + byteBuffer.limit());
        System.out.println("capacity: " + byteBuffer.capacity());
    }

    public static void main(String[] args) {
        allocate();
        System.out.println();
        wrap();
    }
}