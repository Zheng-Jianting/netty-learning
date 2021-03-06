package com.zhengjianting.nio.channel;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

// Start this program, then "telnet localhost 1234" to connect it
public class ChannelAccept {
    public static final String GREETING = "Hello I must be going.\r\n";

    public static void main(String[] args) throws Exception {
        int port = 1234;
        if (args.length > 0)
            port = Integer.parseInt(args[0]);
        ByteBuffer buffer = ByteBuffer.wrap(GREETING.getBytes());
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));
        ssc.configureBlocking(false);
        while (true) {
            System.out.println("Waiting for connections");
            SocketChannel sc = ssc.accept();
            if (sc == null) {
                // no connections, snooze a while
                Thread.sleep(2000);
            } else {
                System.out.println("Incoming connection from: " + sc.getRemoteAddress());
                sc.write(buffer);
                buffer.rewind(); // position = 0
                sc.close();
            }
        }
    }
}