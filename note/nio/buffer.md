## Buffer

除了 Boolean 类型以外，每一种 Java 基本类型都有对应的 Buffer：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513135158283.png" alt="image-20220513135158283" style="zoom:80%;" />

### 1. 缓冲区基础

缓冲区实质上是一个数组，缓冲区提供了对数据的结构化访问以及维护读写位置等信息

#### 1.1 属性

所有缓冲区都具有以下四个属性：

- capacity：缓冲区能够容纳的数据元素的最大数量，在缓冲区创建时指定，并且不能被修改
- limit：缓冲区的第一个不能被读或写的元素索引
- position：下一个要被读或写的元素索引
- mark：一个备忘位置，调用 _mark()_ 设定 mark = position，调用 _reset()_ 设定 position = mark

```java
public abstract class Buffer {
    // Invariants: mark <= position <= limit <= capacity
    private int mark = -1;
    private int position = 0;
    private int limit;
    private int capacity;
}
```

下图展示了一个新创建的 capacity 为 10 的 ByteBuffer：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513140844056.png" alt="image-20220513140844056" style="zoom:80%;" />

#### 1.2 Put

以 HeapByteBuffer 的其中两个 put 方法为例，下面是相关的代码片段：

```java
public abstract class Buffer {
    final int nextPutIndex() {                          // package-private
        int p = position;
        if (p >= limit)
            throw new BufferOverflowException();
        position = p + 1;
        return p;
    }
    
    final int checkIndex(int i) {                       // package-private
        if ((i < 0) || (i >= limit))
            throw new IndexOutOfBoundsException();
        return i;
    }
}
```



```java
public abstract class ByteBuffer extends Buffer implements Comparable<ByteBuffer> {
    final byte[] hb;                  // Non-null only for heap buffers
    final int offset;
    boolean isReadOnly;                 // Valid only for heap buffers
}
```



```java
class HeapByteBuffer extends ByteBuffer {
    protected int ix(int i) {
        return i + offset;
    }
    
    public ByteBuffer put(byte x) {
        hb[ix(nextPutIndex())] = x;
        return this;
    }

    public ByteBuffer put(int i, byte x) {
        hb[ix(checkIndex(i))] = x;
        return this;
    }
}
```

可以看出 `ByteBuffer put(byte x)` 这个方法会将 position 自增，而 `ByteBuffer put(int i, byte x)` 不会影响 position 的值

例如，在新建的缓冲区上执行以下代码：

```java
buffer.put((byte) 'H').put((byte) 'e').put((byte) 'l').put((byte) 'l').put((byte) 'o');
```

在五次调用 `ByteBuffer put(byte x)` 后，缓冲区的状态为：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513144356008.png" alt="image-20220513144356008" style="zoom:80%;" />

如果想要将缓冲区的内容从 "Hello" 修改为 "Mellow"，可以这样实现：

```java
// ByteBuffer put(int i, byte x) 不会影响 position 的值
buffer.put(0, (byte) 'M');

// ByteBuffer put(byte x) 会将 hb[position] 设为 x, 并且将 position 加 1
buffer.put((byte) 'w');
```

修改后的缓冲区如图所示：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513145508074.png" alt="image-20220513145508074" style="zoom:80%;" />

#### 1.3 Flip

flip() 函数可以将缓冲区从写状态转化为读状态，这其实就是通过修改位置信息实现的：

```java
public abstract class Buffer {
    public final Buffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }
}
```

例如，对上述写入 "Mellow" 的缓冲区执行 flip 操作，缓冲区则从写状态转化为读状态：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513145732858.png" alt="image-20220513145732858" style="zoom:80%;" />

#### 1.4 Get

以 HeapByteBuffer 的其中两个 get 方法为例，其实现与 put 类似，下面是相关的代码片段：

```java
public abstract class Buffer {
    final int nextGetIndex() {                          // package-private
        int p = position;
        if (p >= limit)
            throw new BufferUnderflowException();
        position = p + 1;
        return p;
    }
    
    final int checkIndex(int i) {                       // package-private
        if ((i < 0) || (i >= limit))
            throw new IndexOutOfBoundsException();
        return i;
    }
}
```



```java
public abstract class ByteBuffer extends Buffer implements Comparable<ByteBuffer> {
    final byte[] hb;                  // Non-null only for heap buffers
    final int offset;
    boolean isReadOnly;                 // Valid only for heap buffers
}
```



```java
class HeapByteBuffer extends ByteBuffer {
    protected int ix(int i) {
        return i + offset;
    }

    public byte get() {
        return hb[ix(nextGetIndex())];
    }

    public byte get(int i) {
        return hb[ix(checkIndex(i))];
    }
}
```

除此之外，在缓冲区为读状态下：

- _remaining()_ 函数返回缓冲区中剩余的元素数量
- _hasRemaining()_ 判断缓冲区中是否还有可读的元素

在缓冲区为写状态下：

- _remaining()_ 函数返回缓冲区中还能写入的元素数量
- _hasRemaining()_ 判断缓冲区中是否还能继续写入元素

```java
public abstract class Buffer {
    public final int remaining() {
        int rem = limit - position;
        return rem > 0 ? rem : 0;
    }
    
    public final boolean hasRemaining() {
        return position < limit;
    }
}
```

因此，可以循环读取缓冲区中的内容：

```java
while (buffer.hasRemaining()) {
    buffer.get();
}
```

#### 1.5 Clear

一旦缓冲区对象经历了 Put -> Flip -> Get，它就可以被重新使用了，_clear()_ 函数将缓冲区重置为空状态，它并不改变缓冲区中的任何数据元素，而仅仅改变位置信息使得缓冲区可以被重新利用

```java
public abstract class Buffer {
    public final Buffer clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }
}
```

#### 1.6 PutFlipGet Example

```java
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
```

#### 1.7 Compact

例如，向 buffer 写入 "Mellow" 之后执行 flip，从 buffer 中读取前两个字符 "Me"，缓冲区状态如下：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513160110507.png" alt="image-20220513160110507" style="zoom:80%;" />

如果此时想继续向 buffer 内写入内容，那么就可以执行 _compact()_ 函数，以 HeapByteBuffer 为例：

```java
class HeapByteBuffer extends ByteBuffer {
    public ByteBuffer compact() {
        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        System.arraycopy(hb, ix(pos), hb, ix(0), rem);
        position(rem);
        limit(capacity());
        discardMark();
        return this;
    }
}
```

以上图为例，compact 就是把 hb[2，6) 向前移动至 hb[0，4)，之后将 position 设为缓冲区内剩余的元素数量，即 lim - pos = 6 - 2 = 4，将 limit 设为 capacity，如图所示：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513160650493.png" alt="image-20220513160650493" style="zoom:80%;" />

而此时，缓冲区也从读状态转换成了写状态

#### 1.8 Mark

mark 记录了一个 position，使得缓冲区可以在之后调用 _reset()_ 将 position 设为之前记录的 mark

```java
public abstract class Buffer {
    public final Buffer mark() {
        mark = position;
        return this;
    }
    
    public final Buffer reset() {
        int m = mark;
        if (m < 0)
            throw new InvalidMarkException();
        position = m;
        return this;
    }
}
```

#### 1.9 批量移动

除了一次读写一个元素，缓冲区还能批量读写，以 HeapByteBuffer 为例：

- `ByteBuffer get(byte[] dst, int offset, int length)` 表示从缓冲区读取 length 个元素，放置到 dst 数组中 ( 从 offset 开始放置 )
- `ByteBuffer put(byte[] src, int offset, int length)` 表示将 length 个 src 数组中的元素 ( 从 offse 开始 ) 写入到缓冲区中
- `ByteBuffer put(ByteBuffer src)` 表示把从 src 中读取的元素写入到缓冲区中

```java
class HeapByteBuffer extends ByteBuffer {
    public ByteBuffer get(byte[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        int pos = position();
        if (length > limit() - pos)
            throw new BufferUnderflowException();
        System.arraycopy(hb, ix(pos), dst, offset, length);
        position(pos + length);
        return this;
    }
    
    public ByteBuffer put(byte[] src, int offset, int length) {
        checkBounds(offset, length, src.length);
        int pos = position();
        if (length > limit() - pos)
            throw new BufferOverflowException();
        System.arraycopy(src, offset, hb, ix(pos), length);
        position(pos + length);
        return this;
    }

    public ByteBuffer put(ByteBuffer src) {
        if (src instanceof HeapByteBuffer) {
            if (src == this)
                throw new IllegalArgumentException();
            HeapByteBuffer sb = (HeapByteBuffer)src;
            int pos = position();
            int sbpos = sb.position();
            int n = sb.limit() - sbpos;
            if (n > limit() - pos)
                throw new BufferOverflowException();
            System.arraycopy(sb.hb, sb.ix(sbpos),
                             hb, ix(pos), n);
            sb.position(sbpos + n);
            position(pos + n);
        } else if (src.isDirect()) {
            int n = src.remaining();
            int pos = position();
            if (n > limit() - pos)
                throw new BufferOverflowException();
            src.get(hb, ix(pos), n);
            position(pos + n);
        } else {
            super.put(src);
        }
        return this;
    }
}
```

### 2. 创建缓冲区

除了 Boolean 类型，每一种 Java 基本类型都有对应的缓冲区类，它们都是抽象类，不能直接实例化，但是都包含静态工厂方法用来创建相应的类实例

以 ByteBuffer 为例，有以下创建缓冲区的静态工厂方法：

```java
public abstract class ByteBuffer extends Buffer implements Comparable<ByteBuffer> {
    public static ByteBuffer allocateDirect(int capacity) {
        return new DirectByteBuffer(capacity);
    }
    
    public static ByteBuffer allocate(int capacity) {
        if (capacity < 0)
            throw new IllegalArgumentException();
        return new HeapByteBuffer(capacity, capacity);
    }
    
    public static ByteBuffer wrap(byte[] array, int offset, int length) {
        try {
            return new HeapByteBuffer(array, offset, length);
        } catch (IllegalArgumentException x) {
            throw new IndexOutOfBoundsException();
        }
    }
    
    public static ByteBuffer wrap(byte[] array) {
        return wrap(array, 0, array.length);
    }
}
```

先不讨论 allocateDirect 方法，其它静态工厂方法创建的都是 HeapByteBuffer

#### 2.1 Allocate

以下代码从堆空间中分配了一个 byte 数组作为备份存储器 ( 缓冲区实质上是一个数组 )

```java
ByteBuffer byteBuffer = ByteBuffer.allocate(100);
```

#### 2.2 Wrap

以下代码构造了一个新的缓冲区对象，但数据元素会存在于数组中，这意味着通过调用 _put()_ 函数造成的对缓冲区的改动会直接影响这个数组，而且对这个数组的任何改动也会对这个缓冲区对象可见

```java
byte[] bytes = new byte[100];
ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
```

使用 `ByteBuffer wrap(byte[] array, int offset, int length)` 构造缓冲区对象会将 position 设为 offset，limit 设为 offset + length，例如：

```java
byte[] bytes = new byte[100];
ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 12, 42);
```

上述代码创建了一个 position 为 12，limit 为 54，capacity 为 100 的缓冲区对象

这个函数并不像您可能认为的那样，创建了一个只占用了一个数组子集的缓冲区，这个缓冲区可以存取这个数组的全部范围，offset 和 length 参数只是设置了初始状态，对上述 byteBuffer 调用 _clear()_ 函数，position 将变为 0，limit 将变为 100

如果 _hasArray()_ 函数返回 true 表示缓冲区有一个可存取的备份数组，可以通过 _array()_ 函数获得

```java
public abstract class Buffer {
    // Tells whether or not this buffer is backed by an accessible array.
    public abstract boolean hasArray();
    
    // Returns the array that backs this buffer  (optional operation).
    public abstract Object array();
}
```

### 3. 复制缓冲区

```java
public abstract class ByteBuffer extends Buffer implements Comparable<ByteBuffer> {
    public abstract ByteBuffer duplicate();
    public abstract ByteBuffer asReadOnlyBuffer();
    public abstract ByteBuffer slice();
}
```

#### 3.1 Duplicate

```java
class HeapByteBuffer extends ByteBuffer {
    public ByteBuffer duplicate() {
        return new HeapByteBuffer(hb, 
                                  this.markValue(), 
                                  this.position(), 
                                  this.limit(), 
                                  this.capacity(), 
                                  offset);
    }
}
```

以 ByteBuffer 为例，_duplicate()_ 创建一个新的 ByteBuffer：

- 它和调用 _duplicate()_ 的 ByteBuffer 共享一个 hb 数组
- 并且新的 ByteBuffer 的 capacity，limit，position，mark 属性和调用 _duplicate()_ 的 ByteBuffer 相同

由于本质上两个 ByteBuffer 共享同一个 hb 数组，因此其中一个 ByteBuffer 对缓冲区进行读写对另一个 ByteBuffer 可见

虽然复制时，新的 ByteBuffer 和 capacity，limit，position，mark 属性和调用 _duplicate()_ 的 ByteBuffer 相同，但是两个 ByteBuffer 的位置信息是相互独立的

例如，以下代码复制了一个缓冲区对象：

```java
ByteBuffer buffer = ByteBuffer.allocate(8);
buffer.position(3).limit(6).mark().position(5);
ByteBuffer dupeBuffer = buffer.duplicate();
buffer.clear();
```

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513224633244.png" alt="image-20220513224633244" style="zoom:80%;" />

#### 3.2 AsReadOnlyBuffer

```java
class HeapByteBuffer extends ByteBuffer {
    public ByteBuffer asReadOnlyBuffer() {
        return new HeapByteBufferR(hb, 
                                   this.markValue(), 
                                   this.position(), 
                                   this.limit(), 
                                   this.capacity(), 
                                   offset);
    }
}
```

_asReadOnlyBuffer()_ 和 _duplicate()_ 一样都是创建一个新的缓冲区对象，新的缓冲区对象和调用 _asReadOnlyBuffer()_ 的缓冲区对象共享一个 hb 数组，但位置信息也是相互独立的

_asReadOnlyBuffer()_ 和 _duplicate()_ 唯一的区别是 _asReadOnlyBuffer()_ 创建的是只读缓冲区对象，不允许写操作，它调用 _isReadOnly()_ 返回 true，但是，对原来的 ByteBuffer 进行写操作是对新的 ByteBuffer 可见的

#### 3.3 Slice

```java
class HeapByteBuffer extends ByteBuffer {
    public ByteBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        return new HeapByteBuffer(hb, -1, 0, rem, rem, pos + offset);
    }
}
```

_slice()_ 也与 _duplicate()_ 类似，也是创建一个新的 ByteBuffer，但是 _slice()_ 创建的新的 ByteBuffer 和原始 ByteBuffer 共享一段 hb 数组的子序列，新的 ByteBuffer 的 position 为 0，limit 和 capacity 为原始 ByteBuffer 剩余的元素数量

例如，以下代码的执行结果如下：

```java
ByteBuffer buffer = ByteBuffer.allocate(8);
buffer.position(3).limit(5);
ByteBuffer sliceBuffer = buffer.slice();
```

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220513230519270.png" alt="image-20220513230519270" style="zoom:80%;" />

