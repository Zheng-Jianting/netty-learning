package com.zhengjianting.nio.channel;

import java.io.FileInputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

public class ChannelTransfer {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: filename ...");
            return;
        }
        catFiles(Channels.newChannel(System.out), args);
    }

    public static void catFiles(WritableByteChannel target, String[] files) throws Exception {
        for (String file : files) {
            FileInputStream fis = new FileInputStream(file);
            FileChannel channel = fis.getChannel();
            channel.transferTo(0, channel.size(), target);
            channel.close();
            fis.close();
        }
    }
}