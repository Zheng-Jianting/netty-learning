package com.zhengjianting.nio.buffer;

import java.nio.CharBuffer;

public class BufferFillDrain {
    private static int index = 0;
    private static final String[] strings = {
            "A random string value",
            "The product of an infinite number of monkeys",
            "Hey hey we're the Monkees",
            "Opening act for the Monkees: Jimi Hendrix"
    };

    public static void main(String[] args) {
        CharBuffer buffer = CharBuffer.allocate(100);
        while (fillBuffer(buffer)) {
            buffer.flip();
            drainBuffer(buffer);
            buffer.clear();
        }
    }

    public static boolean fillBuffer(CharBuffer buffer) {
        if (index >= strings.length)
            return false;
        String string = strings[index++];
        for (int i = 0; i < string.length(); i++)
            buffer.put(string.charAt(i));
        return true;
    }

    public static void drainBuffer(CharBuffer buffer) {
        while (buffer.hasRemaining())
            System.out.print(buffer.get());
        System.out.println();
    }
}