package com.zhengjianting.nio.selector;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class SelectSockets {
    public static int PORT_NUMBER = 1234;

    public static void main(String[] args) throws Exception {
        new SelectSockets().go(args);
    }

    public void go(String[] args) throws Exception {
        int port = PORT_NUMBER;
        if (args.length > 0) // Override default listen port
            port = Integer.parseInt(args[0]);
        System.out.println("Listening in port " + port);

        // Create a new Selector for use below
        Selector selector = Selector.open();
        // Allocate an unbound server socket channel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        // Set the port server channel will listen to
        serverChannel.bind(new InetSocketAddress(port));
        // Set nonblocking mode for the listening socket
        serverChannel.configureBlocking(false);

        // Register the ServerSocketChannel with the Selector
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            // This may block for a long time. Upon Returning, the
            // selected set contains keys of the ready channels.
            int n = selector.select();
            if (n == 0)
                continue; // nothing to do

            // Get an iterator over the set of selected keys
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

            // Look at each key in the selected set
            while (it.hasNext()) {
                SelectionKey key = it.next();

                // Is a new connection coming in?
                if (key.isAcceptable()) {
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel channel = server.accept();
                    registerChannel(selector, channel, SelectionKey.OP_READ);
                    sayHello(channel);
                }

                // Is there data to read in this channel
                if (key.isReadable())
                    readDataFromSocket(key);

                // Remove key from selected set; it's been handled
                it.remove();
            }
        }
    }

    /**
     * Register the given channel with the given selector for the given
     * operations of interest
     */
    protected void registerChannel(Selector selector, SelectableChannel channel, int ops) throws Exception {
        if (channel == null)
            return; // could happen

        // Set the new channel nonblocking
        channel.configureBlocking(false);

        // Register it with the selector
        channel.register(selector, ops);
    }

    // Use the same byte buffer for all channels. A single thread is
    // servicing all the channels, so no danger of concurrent access.
    private ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

    // Spew a greeting to the incoming client connection.
    private void sayHello(SocketChannel channel) throws Exception {
        buffer.clear();
        buffer.put("Hi there!\r\n".getBytes());
        buffer.flip();

        channel.write(buffer);
    }


    /**
     * Sample data handler method for a channel with data ready to read.
     * @param key
     *  A SelectionKey object associated with a channel determined by
     *  the selector to be ready for reading. If the channel returns
     *  an EOF condition, it is closed here, which automatically
     *  invalidates the associated key. The selector will then
     *  de-register the channel on the next select call.
     */
    private void readDataFromSocket(SelectionKey key) throws Exception {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        int count;
        buffer.clear();

        // Loop while data is available; channel is nonblocking
        while ((count = socketChannel.read(buffer)) > 0) {
            buffer.flip(); // Make buffer readable

            // Send the data; don't assume it goes all at once
            while (buffer.hasRemaining())
                socketChannel.write(buffer);

            // WARNING: the above loop is evil. Because
            // it's writing back to the same nonblocking
            // channel it read the data from, this code can
            // potentially spin in a busy loop. In real life
            // you'd do something more useful than this.

            buffer.clear();
        }

        if (count < 0)
            socketChannel.close(); // Close channel on EOF, invalidates the key
    }
}