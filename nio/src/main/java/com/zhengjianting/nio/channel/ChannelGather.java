package com.zhengjianting.nio.channel;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class ChannelGather {
    private static final String DEMOGRAPHIC = "blahblah.txt";

    public static void main(String[] args) throws Exception {
        int reps = 10;
        if (args.length > 0)
            reps = Integer.parseInt(args[0]);
        FileOutputStream fos = new FileOutputStream(DEMOGRAPHIC);
        GatheringByteChannel gatherChannel = fos.getChannel();
        ByteBuffer[] bs = utterBS(reps);
        while (gatherChannel.write(bs) > 0) {
            // Empty body
            // Loop util write() returns zero
        }
        fos.close();
    }

    private static final String[] col1 = {
            "Aggregate", "Enable", "Leverage",
            "Facilitate", "Synergize", "Repurpose",
            "Strategize", "Reinvent", "Harness"
    };

    private static final String[] col2 = {
            "cross-platform", "best-of-breed", "frictionless",
            "ubiquitous", "extensible", "compelling",
            "mission-critical", "collaborative", "integrated"
    };

    private static final String[] col3 = {
            "methodologies", "infomediaries", "platforms",
            "schemas", "mindshare", "paradigms",
            "functionalities", "web services", "infrastructures"
    };

    private static final String newLine = System.getProperty("line.separator");

    private static ByteBuffer[] utterBS(int howMany) {
        List<ByteBuffer> list = new LinkedList<>();
        for (int i = 0; i < howMany; i++) {
            list.add(pickRandom(col1, " "));
            list.add(pickRandom(col2, " "));
            list.add(pickRandom(col3, newLine));
        }
        ByteBuffer[] bufs = new ByteBuffer[list.size()];
        list.toArray(bufs);
        return bufs;
    }

    private static final Random rand = new Random();

    private static ByteBuffer pickRandom(String[] strings, String suffix) {
        String string = strings[rand.nextInt(strings.length)];
        int total = string.length() + suffix.length();
        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.put(string.getBytes(StandardCharsets.US_ASCII));
        buf.put(suffix.getBytes(StandardCharsets.US_ASCII));
        buf.flip();
        return buf;
    }
}