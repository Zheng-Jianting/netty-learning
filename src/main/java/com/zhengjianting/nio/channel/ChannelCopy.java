package com.zhengjianting.nio.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ChannelCopy {
    public static void main(String[] args) throws IOException {
        ReadableByteChannel source = Channels.newChannel(System.in);
        WritableByteChannel dest = Channels.newChannel(System.out);
        channelCopy1(source, dest);
        // channelCopy2(source, dest);
        source.close();
        dest.close();
    }

    /**
     * 优点: 系统调用次数少 (dest.write() 次数少)
     * 缺点: 数据复制次数多 (compact 需要移动数据)
     */
    public static void channelCopy1(ReadableByteChannel src, WritableByteChannel dest) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
        while (src.read(buffer) != -1) { // 只读取一次, 不一定能把通道的数据全部读到缓冲区中, 因此循环读取
            buffer.flip(); // 将缓冲区从写状态转换为读状态
            dest.write(buffer);
            buffer.compact(); // write() 不一定能把缓冲区内的数据全部写入通道, compact 将缓冲区内数据进行压缩, 并且将缓冲区从读状态转化为写状态
        }
        buffer.flip(); // 此时 src 通道的数据都读取完毕了, 但缓冲区可能还有数据 (最后一次 write 没把缓冲区排干净), 将缓冲区从写状态转换为读状态
        while (buffer.hasRemaining())
            dest.write(buffer);
    }

    /**
     * 优点: 不需要复制数据
     * 缺点: 系统调用次数多 (dest.write() 次数多)
     */
    public static void channelCopy2(ReadableByteChannel src, WritableByteChannel dest) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
        while (src.read(buffer) != -1) {
            buffer.flip();
            while (buffer.hasRemaining()) // 把填入缓冲区的数据全部排干净
                dest.write(buffer);
            buffer.clear();
        }
    }
}