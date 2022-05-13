package com.zhengjianting.nio.buffer;

import java.nio.ByteBuffer;

public class BufferShare {
    public static void duplicate() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.position(3).limit(6).mark().position(5);
        ByteBuffer dupeBuffer = buffer.duplicate();
        buffer.clear();

        // buffer 和 dupeBuffer 的位置属性是独立的
        System.out.println("buffer: ");
        print(buffer);
        System.out.println("dupeBuffer: ");
        print(dupeBuffer);

        // buffer 和 dupeBuffer 共享 hb 数组, 对其中一个缓冲区进行读写, 是对另一个缓冲区可见的
        buffer.put(5, (byte) 'T');
        System.out.println((char) dupeBuffer.get(5));
    }

    public static void asReadOnlyBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();

        // readOnlyBuffer 是只读的
        System.out.println(readOnlyBuffer.isReadOnly());

        // 但是由于和 buffer 共享 hb 数组, 因此对 buffer 进行写操作是对 readOnlyBuffer 可见的
        buffer.put(5, (byte) 'T');
        System.out.println((char) readOnlyBuffer.get(5));
    }

    public static void slice() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.position(3).limit(5);
        ByteBuffer sliceBuffer = buffer.slice();

        // sliceBuffer 和 buffer 共享一段 hb 数组的子序列
        System.out.println("buffer: ");
        print(buffer);
        System.out.println("sliceBuffer: ");
        print(sliceBuffer);

        buffer.put(3, (byte) 'T');
        System.out.println((char) sliceBuffer.get(0));
    }

    public static void print(ByteBuffer buffer) {
        System.out.println(buffer.mark());
    }

    public static void main(String[] args) {
        duplicate();
        asReadOnlyBuffer();
        slice();
    }
}