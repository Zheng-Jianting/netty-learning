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

