package com.zhengjianting.nio.channel;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class ConnectAsync {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 1234;
        if (args.length == 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        InetSocketAddress addr = new InetSocketAddress(host, port);
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        System.out.println("initiating connection");
        sc.connect(addr);
        while (!sc.finishConnect()) {
            doSomethingUseful();
        }
        System.out.println("connection established");
        sc.close();
    }

    private static void doSomethingUseful() {
        System.out.println("doing something useless");
    }
}