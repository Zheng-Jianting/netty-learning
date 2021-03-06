## Channel

通道 ( Channel ) 提供与 I/O 服务的直接连接，Channel 用于在字节缓冲区和位于通道另一侧的实体 ( 通常是一个文件或套接字 ) 之间有效地传输数据

通道是一种途径，借助该途径，可以用最小的总开销来访问操作系统本身的 I/O 服务，缓冲区则是通道内部用来发送和接收数据的端点

在我看来，I/O 是将进程内部的数据迁移到外部设备，即输出；或将外部设备迁移至进程内部，即输入；

以进程通过系统调用请求外部数据为例，大致会有以下步骤：

- 进程通过系统调用向操作系统请求外部数据
- 操作系统等待外部数据准备好 ( 例如到达网卡 )，然后将外部数据迁移至内核缓冲区
- 操作系统将数据从内核缓冲区复制到进程缓冲区

```tex
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

<img src="../../picture/nio/channel/image-20220515164931330.png" alt="image-20220515164931330" style="zoom:67%;" />

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

  <img src="../../picture/nio/channel/image-20220515205131371.png" alt="image-20220515205131371" style="zoom:67%;" />

- 对于 read 操作而言，从通道读取的数据会按顺序被分散 ( scatter ) 到多个缓冲区

  <img src="../../picture/nio/channel/image-20220515205157835.png" alt="image-20220515205157835" style="zoom:67%;" />

大多数现代操作系统都支持本地矢量 I/O ( native vectored I/O )，Scatter / Gather 允许您委托操作系统来完成辛苦活：将读取到的数据分开存放到多个桶或者将不同的数据区块合并成一个整体，从而减少或避免了缓冲区拷贝和系统调用

在之前的类图中添加 scatter / gather 接口：

<img src="../../picture/nio/channel/image-20220515204903966.png" alt="image-20220515204903966" style="zoom:67%;" />

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

<img src="../../picture/nio/channel/image-20220516003924138.png" alt="image-20220516003924138" style="zoom:67%;" />

_FileChannel_ 是一个反映 Java 虚拟机外部的一个具体对象的抽象，其具有以下特定：

- 文件通道总是阻塞式的
- _FileChannel_ 对象不能直接创建，一个 _FileChannel_ 实例只能通过在一个打开的 file 对象 ( _RandomAccessFile_、_FileInputStream_、_FileOutputStream_ ) 上调用 _getChannel()_ 方法获取，_getChannel()_ 方法会返回一个关联相同文件的 _FileChannel_ 对象，并且该 _FileChannel_ 对象具有与 file 对象相同的访问权限
- _FileChannel_ 对象是线程安全的

#### 3.1 访问文件

每个 _FileChannel_ 对象都同一个文件描述符 ( file descriptor ) 有一对一的关系，本质上讲，_RandomAccessFile_ 类提供的是同样的抽象内容，在通道出现之前，底层的文件操作都是通过 _RandomAccessFile_ 类的方法来实现的，_FileChannel_ 模拟同样的 I/O 服务，因此它们的 API 自然是很相似的：

<img src="../../picture/nio/channel/image-20220516005024486.png" alt="image-20220516005024486" style="zoom:80%;" />

<img src="../../picture/nio/channel/image-20220516005045603.png" alt="image-20220516005045603" style="zoom:80%;" />

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

<img src="../../picture/nio/channel/image-20220516142056960.png" alt="image-20220516142056960" style="zoom:80%;" />

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

### 4. 内存映射文件

_FileChannel_ 类提供了 _map()_ 方法，该方法可以在一个打开的文件和一个特殊类型的 _ByteBuffer_ 之间建立一个虚拟内存映射

在 _FileChannel_ 上调用 _map()_ 方法会创建一个由磁盘文件支持的虚拟内存映射 ( virtual memory mapping )，并在那块虚拟内存空间外部封装一个 _MappedByteBuffer_ 对象

<img src="../../picture/nio/channel/image-20220516204127818.png" alt="image-20220516204127818" style="zoom:80%;" />

由 _map()_ 方法返回的 _MappedByteBuffer_ 对象的行为在多数方面类似一个基于内存的缓冲区，只不过该对象的数据元素存储在磁盘上的一个文件中：

- 调用 _get()_ 方法会从磁盘文件中获取数据，此数据反映该文件的当前内容，即使在映射建立之后文件已经被一个外部进程做了修改，通过文件映射看到的数据同您用常规方法读取文件看到的内容是完全一样的
- 相似的，对映射的缓冲区实现一个 _put()_ 会更新磁盘上的那个文件 ( 假设对该文件有写权限 )，并且您做的修改对于该文件的其他阅读者也是可见的

通过内存映射机制来访问一个文件会比使用常规方法读写高效得多，甚至比使用通道的效率都高，因为不需要做明确的系统调用以及缓冲区拷贝，更重要的是，操作系统的虚拟内存可以自动缓存内存页 ( memory page )，这些页是用系统内存来缓存的，所以不会消耗 Java 虚拟机内存堆 ( memory heap )

内存映射 I/O 使用文件系统建立从用户空间到可用文件系统页的虚拟内存映射，这样做有几个好处：

- 用户进程把文件数据当作内存，所以无需发布 _read()_ 或 _write()_ 系统调用
- 当用户进程触碰到映射内存空间，会自动产生缺页中断，从而将文件数据从磁盘读进内存；如果用户修改了映射内存空间，相关页会自动标记为脏，随后刷新到磁盘，文件得到更新
- 操作系统的虚拟内存子系统会对页进行智能高速缓存，自动根据系统负载进行内存管理
- 数据总是按页对齐的，无需执行缓冲区拷贝
- 大型文件使用映射，无需耗费大量内存，即可进行数据拷贝

虚拟内存和磁盘 I/O 是紧密关联的，从很多方面看来，它们只是同一件事物的两面

下面让我们看一下如何使用内存映射：

```java
public abstract class FileChannel
    extends AbstractInterruptibleChannel
    implements SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel
{
    public static class MapMode {
        public static final MapMode READ_ONLY = new MapMode("READ_ONLY");
        public static final MapMode READ_WRITE = new MapMode("READ_WRITE");
        public static final MapMode PRIVATE = new MapMode("PRIVATE");
        
        private final String name;
        
        private MapMode(String name) {
            this.name = name;
        }
        
        public String toString() {
            return name;
        }
    }
    
    public abstract MappedByteBuffer map(MapMode mode, long position, long size) throws IOException;
}
```

可以看到，只有一种 _map()_ 方法来创建一个文件映射，其中 position 和 size 参数分别表示映射开始的位置，以及映射的长度，因此，我们可以创建一个 _MappedByteBuffer_ 来代表一个文件中字节的某个子范围，例如：

```java
// 映射文件 [100, 299] 范围的字节
buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 100, 200);

// 映射整个文件
buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
```

_FileChannel_ 类中的静态内部类 _MapMode_ 定义了三种映射模式：

- MapMode.READ_ONLY：文件映射是只读的
- MapMode.READ_WRITE：文件映射是可读写的
- MapMode.PRIVATE：代表 _MappedByteBuffer_ 是一个写时拷贝 ( copy-on-write ) 的映射，这意味着通过 _put()_ 方法所做的任何修改都会导致一个私有的数据拷贝并且该拷贝中的数据只对 _MappedByteBuffer_ 实例可见，这个过程不会对底层文件做任何修改

使用 MapMode.PRIVATE 映射模式的 _MappedByteBuffer_ 使用 _put()_ 方法所作的修改只对自己可见，并且不会修改底层文件，但是这种映射模式还是可以看到通过其他方式对文件所做的修改，尽管写时拷贝的映射可以防止底层文件被修改，您也必须以 read/write 权限来打开文件以建立 MapMode.PRIVATE 映射，只有这样，返回的 _MappedByteBuffer_ 对象才能允许使用 _put()_ 方法

您应该注意到了没有 _unmap()_ 方法，也就是说，一个映射一旦建立之后将保持有效，直到 _MappedByteBuffer_ 对象被施以垃圾收集动作为止，关闭相关联的 _FileChannel_ 并不会破坏映射

_MappedByteBuffer_ 还定义了几个它独有的方法：

```java
public abstract class MappedByteBuffer
    extends ByteBuffer
{
    // This is a partial API listing
    public final MappedByteBuffer load() { ... }
    public final boolean isLoaded() { ... }
    public final MappedByteBuffer force() { ... }
}
```

当我们为一个文件建立虚拟内存映射之后，文件数据通常不会因此被从磁盘读取到内存 ( 这取决于操作系统 )，虚拟内存系统将根据您的需要来把文件中相应区块的数据读进来，缺页中断需要一定的时间，因为将文件数据读取到内存需要一次或多次的磁盘访问

_load()_ 方法会加载整个文件以使它常驻内存，因此这是一个代价高的操作，它会导致大量的页调入 ( page-in )，具体数量取决于文件中被映射区域的实际大小，然而，_load()_ 方法返回并不能保证文件就会完全常驻内存，这是由于请求页面调入 ( demand paging ) 是动态的，对于那些要求近乎实时访问 ( near-realtime access ) 的程序，解决方案就是预加载

我们可以通过调用 _isLoad()_ 方法来判断一个被映射的文件是否完全常驻内存了：

- 如果返回 true，那么很大概率是映射缓冲区的访问延迟很少或者根本没有延迟
- 返回 false 并不一定意味着访问缓冲区将很慢或者该文件并未完全常驻内存

_isLoad()_ 方法的返回值只是一个暗示，由于垃圾收集的异步性质、底层操作系统以及运行系统的动态性等因素，想要在任意时刻准确判断全部映射页的状态是不可能的

_force()_ 方法同 _FileChannel_ 对象的同名方法相似，该方法会强制将映射缓冲区上的更改应用到永久磁盘存储器上，当用 _MappedByteBuffer_ 对象来更新一个文件时，应该总是使用 _MappedByteBuffer.force()_ 而非 _FileChannel.force()_，因为通道对象可能不清楚映射缓冲区做出的文件的全部更改

如果映射是以 MapMode.READ_ONLY 或 MAP_MODE.PRIVATE 模式建立的，那么调用 _force()_ 方法将不起任何作用，因为永远不会有更改需要同步到磁盘上 ( 但是这样做也是没有害处的 )

因为 _MappedByteBuffer_ 也是 _ByteBuffer_，所以能够被传递给 _SocketChannel_ 之类通道的 _read()_ 或 _write()_ 方法：

```java
// 从与 channel 相关联的 I/O 设备 (磁盘文件, socket 等) 中读取数据到 mappedByteBuffer 中
// 数据写入到 mappedByteBuffer 中也就意味着写入到了被映射的文件
channel.read(mappedByteBuffer);

// 将 mappedByteBuffer 中的数据写入到与 channel 相关联的 I/O 设备
// 也就意味着将被映射的文件写入到了与 channel 相关联的 I/O 设备
channel.write(mappedByteBuffer);
```

如果再结合 scatter / gather，那么从内存缓冲区和被映射文件内容中组织数据就变得很容易了，下面的例子就是以此方式写 HTTP 回应的：

```java
package com.zhengjianting.nio.channel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class MappedHttp {
    private static final String OUTPUT_FILE = "MappedHttp.out";
    private static final String LINE_SEP = "\r\n";
    private static final String SERVER_ID = "Server: Ronsoft Dummy Server";
    private static final String HTTP_HDR = "HTTP/1.0 200 OK" + LINE_SEP + SERVER_ID + LINE_SEP;
    private static final String HTTP_404_HDR = "HTTP/1.0 404 Not Found" + LINE_SEP + SERVER_ID + LINE_SEP;
    private static final String MSG_404 = "Could not open file: ";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: filename");
            return;
        }
        String file = args[0];
        ByteBuffer header = ByteBuffer.wrap(bytes(HTTP_HDR));
        ByteBuffer dynhdrs = ByteBuffer.allocate(128);
        ByteBuffer[] gather = { header, dynhdrs, null };
        long contentLength = -1;
        String contentType = "unknown/unknown";
        try {
            FileInputStream fis = new FileInputStream(file);
            FileChannel fc = fis.getChannel();
            MappedByteBuffer fileData = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            gather[2] = fileData;
            contentLength = fc.size();
            contentType = URLConnection.guessContentTypeFromName(file);
        } catch (IOException e) {
            // file could not be opened, report problem
            ByteBuffer buf = ByteBuffer.allocate(128);
            String msg = MSG_404 + e + LINE_SEP;
            buf.put(bytes(msg));
            buf.flip();
            // use the HTTP error response
            gather[0] = ByteBuffer.wrap(bytes(HTTP_404_HDR));
            gather[2] = buf;
            contentLength = msg.length();
            contentType = "text/plain";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("Content-Length: ").append(contentLength);
        sb.append(LINE_SEP);
        sb.append("Content-Type: ").append(contentType);
        sb.append(LINE_SEP).append(LINE_SEP);
        dynhdrs.put(bytes(sb.toString()));
        dynhdrs.flip();

        FileOutputStream fos = new FileOutputStream(OUTPUT_FILE);
        FileChannel out = fos.getChannel();
        while (out.write(gather) > 0) {
            // Empty body; loop util all buffers are empty
        }
        out.close();
        System.out.println("output written to: " + OUTPUT_FILE);
    }

    private static byte[] bytes(String string) {
        return string.getBytes(StandardCharsets.US_ASCII);
    }
}
```

#### 4.1 Channel-to-Channel 传输

由于经常需要从一个位置将文件数据批量传输到另一个位置，_FileChannel_ 类添加了一些优化方法来提高该传输过程的效率：

```java
public abstract class FileChannel
    extends AbstractInterruptibleChannel
    implements SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel
{
    public abstract long transferTo(long position, long count, WritableByteChannel target) throws IOException;
    public abstract long transferFrom(ReadableByteChannel src, long position, long count) throws IOException;
}
```

_transferTo()_ 和 _transferFrom()_ 方法允许将一个通道交叉连接到另一个通道，而不需要通过一个中间缓冲区来传递数据

只有 _FileChannel_ 类有这两个方法，因此 channel-to-channel 传输中通道之一必须是 _FileChannel_，例如不能在 socket 通道之间直接传输数据，不过 socket 通道实现了 _WritableByteChannel_ 和 _ReadableByteChannel_ 接口：

- 文件的内容可以用 _transferTo()_ 方法传输给一个 socket 通道
- 也可以用 _transferFrom()_ 方法将数据从一个 socket 通道直接读取到一个文件中

请求的数据传输将从 position 参数指定的位置开始，传输的字节数不超过 count 参数的值，实际传输的字节数会由方法返回，可能少于请求的字节数

channel-to-channel 传输是可以极其快速的，特别是在底层操作系统提供本地支持的时候，某些操作系统可以不必通过用户空间传递数据而进行直接的数据传输，对于大量的数据传输，这会是一个巨大的帮助

下面的例子以一系列文件作为参数，并将其按序依次传输至一个 WritableByteChannel ( in this case，stdout )：

```java
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
```

### 5. Socket 通道

socket 通道有与文件通道不同的特征：socket 通道类可以运行非阻塞模式并且是可选择的，借助新的 NIO 类，再也没有为每个 socket 连接使用一个线程的必要了，也避免了管理大量线程所需的上下文交换总开销，一个或几个线程就可以管理成百上千的活动 socket 连接了，并且只有很少甚至可能没有性能损失

下面解释一下阻塞模式和非阻塞模式的区别，首先回顾一下在文章开头提到的进程通过系统调用请求外部数据的过程：

```tex
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

根据 UNIX 网络编程对 I/O 模型的分类，UNIX 提供了 5 种 I/O 模型，除了异步 I/O 模型，包括阻塞 I/O 模型和非阻塞 I/O 模型在内的其余四种 I/O 模型在第三步将数据从内核复制到用户空间都是阻塞的

阻塞 I/O 模型、非阻塞 I/O 模型、I/O 复用模型、信号驱动 I/O 模型这四种模型的区别主要在于第二步：等待内核准备好外部数据，这里仅介绍阻塞 I/O 模型和非阻塞 I/O 模型，以套接字接口为例：

- 阻塞 I/O 模型：当数据还没到达网卡，或是还没将到达网卡的数据传输至内核，进程都是阻塞的

  <img src="../../picture/nio/channel/image-20220518175110002.png" alt="image-20220518175110002" style="zoom:67%;" />

- 非阻塞 I/O 模型：与阻塞 I/O 模型不同，当内核没有准备好数据时会直接返回给进程一个 EWOULDBLOCK 错误，一般进程都对进行轮询检查这个状态，查看内核是否准备好数据

  在介绍选择器时我们将了解到如何使用选择器来避免轮询并在异步连接之后收到通知，非阻塞 I/O 和可选择性是紧密相连的

  <img src="../../picture/nio/channel/image-20220518175143790.png" alt="image-20220518175143790" style="zoom:67%;" />

全部 socket 通道类 ( _DatagramChannel_、_SocketChannel_、_ServerSocketChannel_ ) 都是由位于 java.nio.channels.spi 包中的 _AbstractSelectableChannel_ 引申而来，这意味着我们可以用一个 _Selector_ 对象来对准备就绪 ( 内核准备好数据 ) 的 socket 通道进行选择 ( readiness selection )

<img src="../../picture/nio/channel/image-20220518231242968.png" alt="image-20220518231242968" style="zoom:67%;" />

注意 _DatagramChannel_ 和 _SocketChannel_ 实现定义读和写功能的接口而 _ServerSocketChannel_ 不实现，_ServerSocketChannel_ 负责监听传入的连接和创建新的 _SocketChannel_ 对象，它本身不传输数据

全部 socket 通道类 ( _DatagramChannel_、_SocketChannel_、_ServerSocketChannel_ ) 在被实例化时都会创建一个对等 socket 对象，其位于包 java.net 中 ( _Socket_、_ServerSocket_、_DatagramSocket_ )，可以通过 _socket()_ 方法获取

#### 5.1 非阻塞模式

Socket 通道可以在非阻塞模式下运行，非阻塞 I/O 是许多复杂的、高性能的程序构建的基础，要把一个 socket 通道置于非阻塞模式，我们要依靠所以 socket 通道类的公有父类：_SelectableChannel_，下面是方法就是关于通道的阻塞模式的：

```java
public abstract class SelectableChannel
    extends AbstractInterruptibleChannel
    implements Channel
{
    // This is a partial API listing
    public abstract SelectableChannel configureBlocking(boolean block) throws IOException;
    public abstract boolean isBlocking();
    public abstract Object blockingLock();
}
```

设置或重新设置一个通道的阻塞模式是很简单的，只有调用 _configureBlocking()_ 方法即可，另外可以通过调用 _isBlocking()_ 方法来判断某个 socket 通道当前处于哪种模式

_blockingLock()_ 方法返回一个对象，只有拥有此对象的锁的线程才能更改通道的阻塞模式：

```java
Object lockObj = serverChannel.blockingLock();
synchronized (lockObj) {
    // This thread now owns the lock; mode can't be changed
}
```

#### 5.2 ServerSocketChannel

_ServerSocketChannel_ 是一个基于通道的 socket 监听器，它同我们所熟悉的 _java.net.ServerSocket_ 执行相同的基本任务，不过它增加了通道语义，因此能够在非阻塞模式下运行

用静态的 _open()_ 工厂方法创建一个新的 _ServerSocketChannel_ 对象，将会返回同一个未绑定的 _java.net.ServerSocket_ 关联的通道

```java
ServerSocketChannel ssc = ServerSocketChannel.open();
ssc.bind(new InetSocketAddress(1234));
```

一旦创建了一个 _ServerSocketChannel_ 并调用了 _bind()_ 方法绑定了一个监听端口，就可以调用 _accept()_ 方法：

- 如果在 _ServerSocket_ 上调用 ( _ServerSocketChannel.socket()_ )，那么它会同任何其他的 _ServerSocket_ 表现一样：总是阻塞并返回一个 _java.net.Socket_ 对象
- 如果在 _ServerSocketChannel_ 上调用 _accept()_，则会返回 _SocketChannel_ 对象，返回的对象能够在非阻塞模式下运行

如果 _ServerSocketChannel_ 配置为非阻塞模式，当没有传入连接在等待时，_ServerSocketChannel.accept()_ 会立即返回 null，下面的例子演示了如何使用一个非阻塞的 accept() 方法：

```java
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
```

#### 5.3 SocketChannel

```java
public abstract class SocketChannel
    extends AbstractSelectableChannel
    implements ByteChannel, ScatteringByteChannel, GatheringByteChannel, NetworkChannel
{
    // This is a partial API listing
    public static SocketChannel open() throws IOException { ... }
    public static SocketChannel open(SocketAddress remote) throws IOException { ... }
    public abstract Socket socket();
    public abstract boolean connect(SocketAddress remote) throws IOException;
    public abstract boolean isConnectionPending();
    public abstract boolean finishConnect() throws IOException;
    public abstract boolean isConnected();
    public final int validOps() {
        return (SelectionKey.OP_READ
                | SelectionKey.OP_WRITE
                | SelectionKey.OP_CONNECT);
    }
}
```

_Socket_ 和 _SocketChannel_ 类封装点对点、有序的网络连接，类似于我们所熟知并喜爱的 TCP/IP 网络连接，_SocketChannel_ 扮演客户端发起同一个监听服务器的连接，直到连接成功，它才能收到数据并且只会从连接到的地址接收

虽然每个 _SocketChannel_ 对象都会创建一个对等的 _Socket_ 对象，反过来却不成立，直接创建的 _Socket_ 对象不会关联 _SocketChannel_ 对象，它们的 _getChannel()_ 方法只返回 null

新创建的 _SocketChannel_ 虽已打开却是未连接的，在一个未连接的 _SocketChannel_ 对象上尝试一个 I/O 操作会导致 _NotYetConnectedException_ 异常，也就是说，_SocketChannel_ 关联一个 I/O 服务 ( socket )，而这个 socket 需要和监听服务器建立连接之后才能和与之建立连接的 socket 传输数据，这也是为什么会有两个 _getAddress()_ 方法：

```java
public abstract class SocketChannel
    extends AbstractSelectableChannel
    implements ByteChannel, ScatteringByteChannel, GatheringByteChannel, NetworkChannel
{
    // Returns the remote address to which this channel's socket is connected.
    public abstract SocketAddress getRemoteAddress() throws IOException;
    
    // Returns the socket address that this channel's socket is bound to.
    public abstract SocketAddress getLocalAddress() throws IOException;
}
```

我们可以通过在通道上直接调用 _connect()_ 方法或在通道关联的 _Socket_ 对象上调用 _connect()_ 来将该 socket 通道连接，以下两种方式是等价的：

```java
SocketChannel socketChannel = SocketChannel.open();
socketChannel.connect(new InetSocketAddress("someHost", somePort));
```

```java
SocketChannel socketChannel = SockectChannel.open(new InetSocketAddress("someHost", somePort));
```

当配置为非阻塞模式的 _SocketChannel_ 调用 _connect()_ 方法时，它发起对请求地址的连接并且立即返回值，如果返回值是 true，说明连接立即建立了 ( 这可能是本地环回连接 )；如果连接不能立即建立，_connect()_ 方法返回 false 并且继续连接建立过程

 面向流的 socket 建立连接状态需要一定的时间，因为两个待连接系统之间必须进行包对话以建立维护流 socket 所需的状态信息，假设 _SocketChannel_ 正在进行连接，_isConnectPending()_ 方法就会返回 true 值

调用 _finishConnect()_ 方法来完成连接过程，如果连接已经完成，该方法会立即返回 true，当连接未完成时：

- 配置为非阻塞模式的 _SocketChannel_ 会立即返回 false
- 配置为阻塞模式的 _SocketChannel_ 会阻塞直到连接完成或失败，相对应地返回 true 或抛出异常

一旦连接过程成功完成，_isConnected()_ 将返回 true 值

下面的例子演示了如何管理异步连接：

```java
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
```

#### 5.4 DatagramChannel

### 6. 管道

java.nio.channels 包中有一个名为 _Pipe_ ( 管道 ) 的类，广义上讲，管道就是一个用来在两个实体之间单向传输数据的导管，在 Unix 操作系统中，管道被用来连接一个进程的输出和另一个进程的输入，_Pipe_ 类实现一个管道范例，不过它所创建的管道是进程内 ( 在 Java 虚拟机进程内部 ) 而非进程间使用的

```java
public abstract class Pipe {
    protected Pipe() { }
    public abstract SourceChannel source();
    public abstract SinkChannel sink();
    public static Pipe open() throws IOException {
        return SelectorProvider.provider().openPipe();
    }
    
    public static abstract class SourceChannel
        extends AbstractSelectableChannel
        implements ReadableByteChannel, ScatteringByteChannel
    {
        protected SourceChannel(SelectorProvider provider) {
            super(provider);
        }
        
        public final int validOps() {
            return SelectionKey.OP_READ;
        }
    }
    
    public static abstract class SinkChannel
        extends AbstractSelectableChannel
        implements WritableByteChannel, GatheringByteChannel
    {
        protected SinkChannel(SelectorProvider provider) {
            super(provider);
        }
        
        public final int validOps() {
            return SelectionKey.OP_WRITE;
        }
    }
}
```

_Pipe_ 类创建一对提供环回机制的 _Channel_ 对象，这两个通道的远端是连接起来的，以便任何写在 _SinkChannel_ 对象上的数据都能出现在 _SourceChannel_ 对象上

<img src="../../picture/nio/channel/image-20220519192424295.png" alt="image-20220519192424295" style="zoom:80%;" />

_Pipe_ 实例是通过调用不带参数的 _Pipe.open()_ 工厂方法来创建的，_Pipe_ 类定义了两个通道类来实现管道：_Pipe.SourceChannel_ ( 管道复杂读的一端 )，_Pipe.SinkChannel_ ( 管道负责写的一端 )，这两个通道实例是在 _Pipe_ 对象创建的同时被创建的，可以通过在 _Pipe_ 对象上分别调用 _source()_ 和 _sink()_ 方法来获取

管道可以被用来仅在同一个进程 ( Java 虚拟机进程 ) 内部传输数据，虽然有更加高效的方式在线程之间传输数据，但是使用管道的好处在于封装性

管道所能承载的数据量是依赖实现的 ( implementation-dependent )，唯一可保证的是写到 _SinkChannel_ 中的字节都能按照同样的顺序在 _SourceChannel_ 上重现

下面的例子演示了如何使用管道：

```tex
			    		   用户空间					关联的 I/O 设备
				<--	|	   buffer
Worker Thread	 <-- |		  ↓
				<-- |	 SinkChannel  --->
			 	  			 ↓		    ↓ Pipe
			 	<-- |	SourceChannel ---↓
 Main Thread	 <-- |		  ↓
	 	   		<--	|  WritableByteChannel			   System.out
```

```java
package com.zhengjianting.nio.channel;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

public class PipeTest {
    public static void main(String[] args) throws Exception {
        WritableByteChannel out = Channels.newChannel(System.out);
        ReadableByteChannel workerChannel = startWorker(10);
        ByteBuffer buffer = ByteBuffer.allocate(100);
        while (workerChannel.read(buffer) >= 0) {
            buffer.flip();
            out.write(buffer);
            buffer.clear();
        }
    }

    // This method can return a SocketChannel or FileChannel instance just as easily
    private static ReadableByteChannel startWorker(int reps) throws Exception {
        Pipe pipe = Pipe.open();
        Worker worker = new Worker(pipe.sink(), reps);
        worker.start();
        return pipe.source();
    }

    /**
     * A worker thread object which writes data down a channel.
     * Note: this object knows nothing about Pipe, uses only a generic WritableByteChannel.
     */
    private static class Worker extends Thread {
        WritableByteChannel channel;
        int reps;

        Worker(WritableByteChannel channel, int reps) {
            this.channel = channel;
            this.reps = reps;
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocate(100);
            try {
                for (int i = 0; i < reps; i++) {
                    doSomeWork(buffer);
                    // channel may not take it all at once
                    while (channel.write(buffer) > 0) {
                        // empty
                    }
                }
                channel.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private final String[] products = {
                "No good deed goes unpunished",
                "To be, or what?",
                "No matter where you go, there you are",
                "Just say \"Yo\"",
                "My karma ran over my dogma"
        };
        private final Random rand = new Random();

        private void doSomeWork(ByteBuffer buffer) {
            String product = products[rand.nextInt(products.length)];
            buffer.clear();
            buffer.put(product.getBytes());
            buffer.put("\r\n".getBytes());
            buffer.flip();
        }
    }
}
```

