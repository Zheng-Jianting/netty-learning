## Channel

通道 ( Channel ) 提供与 I/O 服务的直接连接，Channel 用于在字节缓冲区和位于通道另一侧的实体 ( 通常是一个文件或套接字 ) 之间有效地传输数据

通道是一种途径，借助该途径，可以用最小的总开销来访问操作系统本身的 I/O 服务，缓冲区则是通道内部用来发送和接收数据的端点

在我看来，I/O 是将进程内部的数据迁移到外部设备，即输出；或将外部设备迁移至进程内部，即输入；

以进程通过系统调用请求外部数据为例，大致会有以下步骤：

- 进程通过系统调用向操作系统请求外部数据
- 操作系统等待外部数据准备好 ( 例如到达网卡 )，然后将外部数据迁移至内核缓冲区
- 操作系统将数据从内核缓冲区复制到进程缓冲区

```java
用户空间                         内核空间                            外部设备
  进程                           操作系统                          网卡、磁盘等
 channel
 buffer
               1. 系统调用
               ---------->
    								2. 将外部数据迁移至内核缓冲区
    								-------------------------->
    								<--------------------------
    	  3. 将数据复制到进程缓冲区
    	  <----------------------
```

而 channel 的作用就是关联一个外部设备 ( 例如 FileChannel 关联文件，SocketChannel 关联套接字 )，通过 channel 可以在进程和外部设备之间传输数据：

- channel 将外部数据读取到进程中的 buffer
- channel 将 buffer 中的数据写入相关联的外部设备

### 1. 通道基础

与缓冲区不同，通道 API 主要由接口指定，不同的操作系统上通道实现 ( Channel Implementation ) 会有根本性差异，通道实现经常使用操作系统的本地代码

从顶层的 Channel 接口可以发现，对所有通道来说只有两种共同的操作：

```java
public interface Channel extends Closeable {
    public boolean isOpen();
    public void close() throws IOException;
}
```

由于操作系统都是以字节的形式实现底层 I/O 接口的，因此通道只能在字节缓冲区上操作

#### 1.1 打开通道

I/O 可以分为广义的两大类别：File I/O 和 Stream I/O，分别对应于两种类型的通道：

- 文件通道：FileChannel
- 套接字通道：SocketChannel、ServerSocketChannel、DatagramChannel

文件通道和套接字通道的创建方式不同：

- FileChannel 只能通过在 RandomAccessFile、FileInputStream 或 FileOutputStream 对象上调用 _getChannel()_ 方法来创建：

  ```java
  RandomAccessFile raf = new RandomAccessFile("somefile", "r");
  FileChannel fc = raf.getChannel();
  ```

- 套接字通道可以通过工厂方法直接创建：

  ```java
  SocketChannel sc = SocketChannel.open();
  ServerSocketChannel ssc = ServerSocketChannel.open();
  DatagramChannel dc = DatagramChannel.open();
  ```

  注意 java.net 包下的 Socket 类也有 _getChannel()_ 方法，但是只有当通道存在时，它才会返回与 socket 关联的通道，它不会创建新通道

#### 1.2 使用通道

通道通过 ByteBuffer 进行数据传输，以下是简要的相关 UML 类图：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220515164931330.png" alt="image-20220515164931330" style="zoom:67%;" />

```java
public interface ReadableByteChannel extends Channel {
    public int read(ByteBuffer dst) throws IOException;
}
```

```java
public interface WritableByteChannel extends Channel {
    public int write(ByteBuffer src) throws IOException;
}
```

```java
public interface ByteChannel extends ReadableByteChannel, WritableByteChannel
{

}
```

通道可以是单向 ( unidirectional ) 或者双向的 ( bidirectional )：

- 实现 _ReadableByteChannel_ 接口的 channel 可以通过 _read()_ 方法将外部数据读入到 ByteBuffer 中
- 实现 _WritableByteChannel_ 接口的 channel 可以通过 _write()_ 方法将 ByteBuffer 中的数据写入到外部设备

_ByteChannel_ 接口继承 _ReadableByteChannel_ 和 _WritableByteChannel_ 俩个接口，因此此类通道是双向的，其本身不定义新的 API 方法

文件通道和套接字通道都是双向的，这对套接字通道不是问题，但是对于文件通道却是个问题，例如从 _FileInputStream_ 对象的 _getChannel()_ 方法获取的 _FileChannel_ 对象是只读的，但是从接口声明的角度来看却是双向的，在该通道上调用 _write()_ 方法将抛出未经检查的 _NonWritableChannelException_ 异常

注意，通道会连接一个特定的 I/O 服务且通道实例 ( channel instance ) 的性能受它所连接的 I/O 服务的特征限制，例如一个连接到只读文件的 Channel 实例不能进行写操作，即使该实例所属的类可能有 _write()_ 方法

_ByteChannel_ 的 _read()_ 和 _write()_ 方法使用 _ByteBuffer_ 对象作为参数，两种方法均返回已传输的字节数，可能比缓冲区的字节数少甚至为 0，缓冲区的位置也会发生与已传输字节相同数量的前移

下面的例子演示了如何从一个通道复制数据到另一个通道：

```java
channel (src) -------------- System.in
    |
    |
    ↓
ByteBuffer
    |
    |
    ↓
channel (dest) ------------- System.out
```

```java
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
        // 只读取一次, 不一定能把通道的数据全部读到缓冲区中, 因此循环读取
        while (src.read(buffer) != -1) {
            buffer.flip(); // 将缓冲区从写状态转换为读状态
            dest.write(buffer);
            // write() 不一定能把缓冲区内的数据全部写入通道, compact 将缓冲区内数据进行压缩, 并且将缓冲区从读状态转化为写状态
            buffer.compact();
        }
        // 此时 src 通道的数据都读取完毕了, 但缓冲区可能还有数据 (最后一次 write 没把缓冲区排干净), 将缓冲区从写状态转换为读状态
        buffer.flip();
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
```

通道可以以阻塞 ( blocking ) 或非阻塞 ( nonblocking ) 模式运行，非阻塞模式的通道不会让调用的线程休眠，请求的操作要么立即完成，要么返回一个结果表明未进行任何操作，只有面向流的 ( stream-oriented ) 的通道，如 sockets 和 pipes 才能使用非阻塞模式

#### 1.3 关闭通道

与缓冲区不同，通道不能被重复使用，一个打开的通道即代表与一个特定 I/O 服务的特定连接并封装该连接的状态，当通道关闭时，连接会丢失，然后通道不再连接任何东西

### 2. Scatter / Gather

通道提供了一种被称为 Scatter / Gather 的重要新功能 ( 有时也被称为矢量 I/O )，它是指在多个缓冲区上实现一个简单的 I/O 操作

- 对于 write 操作而言，数据是从几个缓冲区按顺序聚集 ( gather ) 并沿着通道发送的

  <img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220515205131371.png" alt="image-20220515205131371" style="zoom:67%;" />

- 对于 read 操作而言，从通道读取的数据会按顺序被分散 ( scatter ) 到多个缓冲区

  <img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220515205157835.png" alt="image-20220515205157835" style="zoom:67%;" />

大多数现代操作系统都支持本地矢量 I/O ( native vectored I/O )，Scatter / Gather 允许您委托操作系统来完成辛苦活：将读取到的数据分开存放到多个桶或者将不同的数据区块合并成一个整体，从而减少或避免了缓冲区拷贝和系统调用

在之前的类图中添加 scatter / gather 接口：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220515204903966.png" alt="image-20220515204903966" style="zoom:67%;" />

```java
public interface ScatteringByteChannel extends ReadableByteChannel {
    // 返回值为读取的字节数, 可能为 0, 当到达 EOF (end-of-stream) 时返回 -1
    public long read(ByteBuffer[] dsts) throws IOException;
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException;
}
```

```java
public interface GatheringByteChannel extends WritableByteChannel {
    // 返回写入的字节数, 可能为 0
    public long write(ByteBuffer[] srcs) throws IOException;
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException;
}
```

### 3. 文件通道

_FileChannel_ 类实现了常用的 read，write 以及 scatter / gather 操作：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220516003924138.png" alt="image-20220516003924138" style="zoom:67%;" />

_FileChannel_ 是一个反映 Java 虚拟机外部的一个具体对象的抽象，其具有以下特定：

- 文件通道总是阻塞式的
- _FileChannel_ 对象不能直接创建，一个 _FileChannel_ 实例只能通过在一个打开的 file 对象 ( _RandomAccessFile_、_FileInputStream_、_FileOutputStream_ ) 上调用 _getChannel()_ 方法获取，_getChannel()_ 方法会返回一个关联相同文件的 _FileChannel_ 对象，并且该 _FileChannel_ 对象具有与 file 对象相同的访问权限
- _FileChannel_ 对象是线程安全的

#### 3.1 访问文件

每个 _FileChannel_ 对象都同一个文件描述符 ( file descriptor ) 有一对一的关系，本质上讲，_RandomAccessFile_ 类提供的是同样的抽象内容，在通道出现之前，底层的文件操作都是通过 _RandomAccessFile_ 类的方法来实现的，_FileChannel_ 模拟同样的 I/O 服务，因此它们的 API 自然是很相似的：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220516005024486.png" alt="image-20220516005024486" style="zoom:80%;" />

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220516005045603.png" alt="image-20220516005045603" style="zoom:80%;" />

以下是 _FileChannel_ 的部分 API：

```java
public abstract class FileChannel
    extends AbstractInterruptibleChannel
    implements SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel
{
    // This is a partial API listing
    public abstract long position() throws IOException;
    public abstract FileChannel position(long newPosition) throws IOException;
    public abstract long size() throws IOException;
    public abstract FileChannel truncate(long size) throws IOException;
    public abstract void force(boolean metaData) throws IOException;
    
    public abstract int read(ByteBuffer dst) throws IOException;
    public abstract int read(ByteBuffer dst, long position) throws IOException;
    public abstract long read(ByteBuffer[] dsts, int offset, int length) throws IOException;
    public final long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }
    
    public abstract int write(ByteBuffer src) throws IOException;
    public abstract int write(ByteBuffer src, long position) throws IOException;
    public abstract long write(ByteBuffer[] srcs, int offset, int length) throws IOException;
    public final long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }
}
```

同底层的文件描述符一样，每个 _FileChannel_ 都有一个叫 "file position" 的概念，表示与 _FileChannel_ 相关联的文件的 position，这个 position 值决定文件中哪一处的数据接下来将被读或者写，有两种形式的 _position()_ 方法：

- _position()_ 返回与 _FileChannel_ 相关联的文件的 position
- _position(long newPosition)_ 将与 _FileChannel_ 相关联的文件的 position 设置为 newPosition，newPosition 允许超过文件尾，但是不会改变文件大小，当 newPosition 超过文件尾时，调用 _read()_ 方法会返回文件尾条件 ( end-of-file indication )，调用 _write()_ 方法会导致文件增长以容纳新字节，但可能会导致文件空洞 ( file hole )

文件空洞：当磁盘上一个文件的分配空间小于它的文件大小时会出现 "文件空洞"，大多数现代文件系统只会为实际写入的数据分配磁盘空间，假如数据被写入到文件中非连续的位置上，这将导致文件出现逻辑上不包含数据的区域 ( 即空洞 )

例如，下述代码将产生有两个空洞的磁盘文件：

```java
package com.zhengjianting.nio.channel;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class FileHole {
    public static void main(String[] args) throws IOException {
        File temp = File.createTempFile("holy", null);
        RandomAccessFile file = new RandomAccessFile(temp, "rw");
        FileChannel channel = file.getChannel();

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(100);
        putData(0, byteBuffer, channel);
        putData(50000, byteBuffer, channel);
        putData(5000000, byteBuffer, channel);

        System.out.println("Wrote temp file '" + temp.getPath() + "', size = " + channel.size());
        channel.close();
        file.close();
    }

    public static void putData(int position, ByteBuffer buffer, FileChannel channel) throws IOException {
        String string = "*<-- location " + position;
        buffer.clear();
        buffer.put(string.getBytes(StandardCharsets.US_ASCII));
        buffer.flip();
        channel.position(position);
        channel.write(buffer);
    }
}
```

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220516142056960.png" alt="image-20220516142056960" style="zoom:80%;" />

当调用 _FileChannel_ 对象的 _read()_ 或 _write()_ 方法时，文件 position 会自动更新，如果 position 值达到了文件大小的值：

- _read()_ 方法会返回一个文件尾条件值 ( -1 )
- 不同于缓冲区的是，调用 _write()_ 方法时 position 前进到超过文件大小的值，该文件会扩展以容纳新写入的字节

类似于缓冲区，在调用 _read()_ 或 _write()_ 方法时指定 position，不会改变当前文件的 position，当指定的 position 超过文件大小时，可能会导致文件中出现一个空洞

_truncate(long size)_ 方法用于减小文件的大小：

- 如果新 size 小于当前 size，超出新 size 的所有字节会被丢弃
- 如果新 size 大于或等于当前 size，该文件不会被修改

这两种情况下，文件的 position 都会被设置为所提供的新 size 值

_force(boolean metaData)_ 方法告诉通道强制将全部待定的修改都同步到磁盘的文件上，所有的现代文件系统都会缓存数据和延迟磁盘文件更新以提高性能，例如调用 _channel.write(ByteBuffer src)_ 方法时大致会经过以下过程：

- 进程发起系统调用，向操作系统请求将 ByteBuffer 中的数据写入与 channel 相关联的磁盘文件
- 操作系统将 ByteBuffer 中的数据从进程缓冲区复制到内核缓冲区
- 操作系统将内核缓冲区中的数据写入磁盘文件

操作系统为了提高性能，将数据复制到内核缓冲区后可能不会立即写入磁盘文件，而 _force(boolean metaData)_ 方法就是要求位于内核缓冲区的待定修改立即同步到磁盘

boolean 类型的参数 metaData 表示元数据 ( metadata ) 是否要被同步更新到磁盘，元数据指文件所有者、访问权限、最后修改时间等信息

#### 3.2 文件锁定
