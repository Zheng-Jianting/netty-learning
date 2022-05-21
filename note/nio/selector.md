## Selector

选择器提供选择执行已经就绪的任务的能力，这使得多元 I/O 成为可能，就绪选择和多元执行使得单线程能够有效率地同时管理多个 I/O 通道，C/C++ 代码的工具箱中，许多年前就已经有 _select()_ 和 _poll()_ 这两个 POSIX ( 可移植性操作系统接口 ) 系统调用可供使用了

### 1. 选择器基础

从最基础的层面来看，选择器提供了询问通道是否已经准备好执行每个 I/O 操作的能力，例如，我们需要了解一个 SocketChannel 对象是否还有更多的字节需要读取，或者我们需要知道 ServerSocketChannel 是否有需要准备接受的连接

在与 SelectableChannel 联合使用时，选择器提供了这种服务，就绪选择的真正价值在于潜在的大量的通道可以同时进行就绪状态的检查，调用者可以轻松地决定多个通道中的哪一个准备好要运行，有两种方式可以选择：

- 线程处于休眠状态，直到至少一个注册到选择器的通道就绪
- 也可以周期性地轮询选择器，看看从上次检查之后，是否有通道处于就绪状态

传统的监控多个 socket 的 Java 解决方案是为每个 socket 创建一个线程并使得线程可以在 _read()_ 调用中阻塞，直到数据可用，这事实上将每个被阻塞的线程当作了 socket 监控器，并将 Java 虚拟机的线程调度当作了通知机制，这两种本来都不是为了这种目的而设计的，程序员和 Java 虚拟机都为管理所有这些线程的复杂性和性能损耗付出了代价，这在线程数量的增长失控时表现得更为突出

真正的就绪选择必须由操作系统来做，操作系统的一项最重要的功能就是处理 I/O 请求并通知各个线程它们的数据已经准备好了，选择器类提供了这种抽象，使得 Java 代码能够以可移植的方式，请求底层的操作系统提供就绪选择服务

```tex
                         用户空间               内核空间             外部设备
                           进程                 操作系统           网卡、磁盘等
                   ↙ SelectableChannel1            ↑
    Selector   ← ← ← SelectableChannel2            |
       ↓           ↖ SelectableChanneln            |
       |									    |
       |	       select / poll / epoll		  |
       | ————————————————————————————————————————> |
```

#### 1.1 选择器，可选择通道和选择键类

**选择器 ( Selector )**

选择器类管理着一个被注册的通道集合的信息和它们的就绪状态，对注册到它之上的通道执行就绪选择，并管理选择键

```java
public abstract class Selector implements Closeable {
    protected Selector() { }
    public static Selector open() throws IOException { ... }
    public abstract boolean isOpen();
    public abstract SelectorProvider provider();
    public abstract Set<SelectionKey> keys();
    public abstract Set<SelectionKey> selectedKeys();
    public abstract int selectNow() throws IOException;
    public abstract int select(long timeout) throws IOException;
    public abstract int select() throws IOException;
    public abstract Selector wakeup();
    public abstract void close() throws IOException;
}
```

**可选择通道 ( SelectableChannel )**

这个抽象类提供了实现通道的可选择性所需要的公共方法，它是所有支持就绪检查的通道类的父类，FileChannel 对象不是可选择的，因为它没有继承 SelectableChannel，所有 socket 通道都是可选择的，包括从管道 ( Pipe ) 对象中获得的通道

SelectableChannel 可以被注册到 Selector 对象上，同时可以指定对那个选择器而言，哪种操作是感兴趣的，一个通道可以被注册到多个选择器上，但对每个选择器而言只能被注册一次

```java
public abstract class SelectableChannel
    extends AbstractInterruptibleChannel
    implements Channel
{
    protected SelectableChannel() { }
    public abstract SelectorProvider provider();
    public abstract int validOps();
    public abstract boolean isRegistered();
    public abstract SelectionKey keyFor(Selector sel);
    public abstract SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException;
    public final SelectionKey register(Selector sel, int ops) throws ClosedChannelException { ... }
    public abstract SelectableChannel configureBlocking(boolean block) throws IOException;
    public abstract boolean isBlocking();
    public abstract Object blockingLock();
}
```

非阻塞特性使得选择器能够对多个可选择通道进行就绪选择，因此在通道被注册到一个选择器上之前，必须先设置为非阻塞模式

**选择键 ( SelectionKey )**

选择键封装了特定的通道与特定的选择器的注册关系，选择键包含了两个比特集 ( 以整数的形式进行编码 )，指示了该注册关系所关心的通道操作，以及通道已经准备好的操作

在 JDK 1.4 中，有四种被定义的可选择操作：read、write、connect、accept

```java
public abstract class SelectionKey {
    protected SelectionKey() { }
    public abstract SelectableChannel channel();
    public abstract Selector selector();
    public abstract boolean isValid();
    public abstract void cancel();
    public abstract int interestOps();
    public abstract SelectionKey interestOps(int ops);
    public abstract int readyOps();
    public static final int OP_READ = 1 << 0;
    public static final int OP_WRITE = 1 << 2;
    public static final int OP_CONNECT = 1 << 3;
    public static final int OP_ACCEPT = 1 << 4;
    public final boolean isReadable() { return (readyOps() & OP_READ) != 0; }
    public final boolean isWritable() { return (readyOps() & OP_WRITE) != 0; }
    public final boolean isConnectable() { return (readyOps() & OP_CONNECT) != 0; }
    public final boolean isAcceptable() { return (readyOps() & OP_ACCEPT) != 0; }
    public final Object attach(Object ob) { ... }
    public final Object attachment() { return attachment; }
}
```

下面是 Selector、SelectableChannel、SelectionKey 三者之间的关系：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220521144238949.png" alt="image-20220521144238949" style="zoom:80%;" />

#### 1.2 建立选择器

下面的代码创建了一个新的选择器，然后将这三个 socket 通道注册到选择器上，并且每个通道感兴趣的操作各不相同，其中 _select()_ 方法将线程置于休眠状态，直到至少有一个通道感兴趣的事情发生或者 10 秒后超时

```java
Selector selector = Selector.open();
channel1.register(selector, SelectionKey.OP_READ);
channel2.register(selector, SelectionKey.OP_WRITE);
channel3.register(selector, SelectionKey.OP_READ | SelectionKey.WRITE);
// Wait up to 10 seconds for a channel to become ready
readyCount = selector.select(10000);
```

Selector 对象是通过调用静态工厂方法 _open()_ 来实例化的，选择器不是像通道或流 ( stream ) 那样的基本 I/O 对象：数据从来没有通过它们进行传递，当不再需要使用它时，需要调用 _close()_ 方法来释放它可能占用的资源并将所有相关的选择键设置为无效

### 2. 使用选择键

就像之前提到的那样，选择键表示了一个特定的通道对象和一个特定的选择器对象之间的注册关系

```java
public abstract class SelectionKey {
    protected SelectionKey() { }
    
    // 返回与该选择键相关联的 SelectableChannel 对象
    public abstract SelectableChannel channel();
    
    // 返回与该选择键相关联的 Selector 对象
    public abstract Selector selector();
    
    // isValid = true 直到通道关闭或选择器关闭或调用了 cancel() 方法
    public abstract boolean isValid();
    
    // 取消将通道注册到选择器上
    public abstract void cancel();
    
    // 通道在该选择器上所关心的操作
    public abstract int interestOps();
    
    // 重新设置通道在该选择器上所关心的操作
    public abstract SelectionKey interestOps(int ops);
    
    // 通道准备好要执行的操作
    public abstract int readyOps();
    
    public static final int OP_READ = 1 << 0;
    public static final int OP_WRITE = 1 << 2;
    public static final int OP_CONNECT = 1 << 3;
    public static final int OP_ACCEPT = 1 << 4;
    public final boolean isReadable() { return (readyOps() & OP_READ) != 0; }
    public final boolean isWritable() { return (readyOps() & OP_WRITE) != 0; }
    public final boolean isConnectable() { return (readyOps() & OP_CONNECT) != 0; }
    public final boolean isAcceptable() { return (readyOps() & OP_ACCEPT) != 0; }
    
    public final Object attach(Object ob) { ... }
    public final Object attachment() { return attachment; }
}
```

其中，_attach()_ 方法允许在选择键上放置一个 "附件"，并在后面通过调用 _attachment()_ 获取，这是一种允许您将任意对象与选择键关联的便捷的方法，这个对象可以是任何对您而言有意义的对象，例如业务对象、会话句柄、其他通道等等

SelectableChannel 类的一个 _register()_ 方法的重载版本接受一个 Object 类型的参数，这是一个方便您在注册时附加一个对象到新生成的键上的方法，以下两种方式是等价的：

```java
SelectionKey key = channel.register(selector, SelectionKey.OP_READ, myObject);
```

```java
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
key.attach(myObject);
```

### 3. 使用选择器

```java
public abstract class Selector implements Closeable {
    protected Selector() { }
    public static Selector open() throws IOException { ... }
    public abstract boolean isOpen();
    public abstract SelectorProvider provider();
    
    // 已注册的键的集合 ( Registered key set )
    public abstract Set<SelectionKey> keys();
    
    // 已选择的键的集合 ( Selected key set )
    public abstract Set<SelectionKey> selectedKeys();
    
    public abstract int selectNow() throws IOException;
    public abstract int select(long timeout) throws IOException;
    public abstract int select() throws IOException;
    
    public abstract Selector wakeup();
    public abstract void close() throws IOException;
}
```

选择器维护着注册过的通道的集合，并且这些注册关系中的任意一个都是封装在 SelectionKey 对象中的，每一个 Selector 对象将这些 SelectionKey 分为三种类型：

- 已注册的键的集合：与选择器关联的已经注册的键的集合，通过 _keys()_ 方法返回
- 已选择的键的集合：已注册的键的集合的子集，这个集合的每个成员都是相关的通道被选择器 ( 在前一个选择操作中 ) 判断为已经准备好的，通过 _selectedKeys()_ 方法返回
- 已取消的键的集合 ( Cancelled key set )：已注册的键的集合的子集，这个集合包含了调用过 _cancel()_ 方法的键

#### 3.1 选择过程

Selector 类的核心是选择过程，基本上，选择器是对 _select()_、_poll()_ 等本地调用 ( native call ) 或者类似的操作系统特定的系统调用的包装，但是 Selector 所作的不仅仅是简单地像本地代码传送参数，它对每个选择操作应用了特定的过程，对这个过程的理解是合理地管理键和它们所表示状态信息的基础

Selector 类的 _select()_ 方法有以下三种不同的形式，它们仅仅在所注册的通道当前都没有就绪时，是否阻塞的方面有所不同：

- _select()_ 在没有通道就绪时将无限阻塞，正常情况下，这个方法将返回一个非零的值，因为直到至少一个通道就绪前它都会阻塞
- _select(long timeout)_ 接受一个超时参数，如果达到超时时间仍然没有通道就绪，它将返回 0
- _selectNow()_ 是完全非阻塞的，它执行就绪检查过程，但不阻塞，如果当前没有通道就绪，它将立即返回 0

当任意一种 _select()_ 被调用时，选择器都将执行以下步骤：

- 检查已取消的键的集合 ( Cancelled key set )，将其中的键从另外两个集合中移除，并且注销相关的通道

- 检查已注册的键的集合 ( Registered key set ) 中选择键的 interest 集合，在该步骤执行后，对 interest 集合的改动不会影响剩余的步骤

  当系统调用完成，每个通道的就绪状态将确定下来，对于那些还没准备好的通道将不会执行任何操作，对于那些操作系统指示至少已经准备好 interest 集合中的一种操作的通道，有以下两种情况：

  - 与该通道关联的选择键不在已选择的键的集合 ( Selected key set ) 中，那么将先清空选择键的 ready 集合，然后将当前通道已经准备好的操作的比特掩码添加到 ready 集合中，并且将该选择键添加到已选择的键的集合 ( Selected key set ) 中
  - 如果与该通道关联的选择键在已选择的键的集合 ( Selected key set ) 中，那么将当前通道已经准备好的操作的比特掩码添加到 ready 集合中，它的 ready 集合是累积的，所有在之前添加到 ready 集合的比特位都不会被清除

- 步骤 2 可能会花费很长时间，特别是当线程处于休眠状态时，与该选择器相关的键可能会同时被取消，因此当步骤 2 结束时，步骤 1 将重新执行

- select 操作返回的值是在步骤 2 中 ready 集合被修改的键的数量，而不是已选择的键的集合 ( Selected key set ) 大小，也就是说，返回值不是已准备好的通道总数，而是从上一个 _select()_ 调用之后进入就绪状态的通道的数量

  在之前 _select()_ 调用中就绪的，并且在本次调用中仍然就绪的通道不会被计入，而那些在前一次调用中已经就绪但已经不再处于就绪状态的通道也不会被计入，这些通道可能仍然在已选择的键的集合 ( Selected key set ) 中，但不会被计入返回值中

#### 3.2 停止选择过程

有三种方式可以唤醒在 select() 方法中睡眠的线程：

- 调用 _wakeup()_ 使得选择器上第一个还没有返回的选择操作立即返回，如果当前没有在进行中的选择，那么下一次调用 _select()_ 方法时将立即返回
- 调用 _close()_ 方法使得任何一个阻塞在 _select()_ 方法的线程被唤醒，就像 _wakeup()_ 方法被调用了一样，选择键将被取消，通道不再注册到该选择器上
- 调用 _interrupt()_

这些方法都不会关闭任何一个相关的通道，中断一个选择器与中断一个通道是不一样的，选择器不会改变任意一个相关的通道，它只会检查它们的状态

#### 3.3 管理选择键

选择是累积的，一旦一个选择器将一个选择键添加到已选择的键的集合 ( Selected key set ) 中，它就不会移除这个键；并且，一旦一个键处于已选择的键的集合 ( Selected key set ) 中，这个键的 ready 集合只会被设置，而不会被清理；它提供了极大的灵活性，但把合理地管理键以确保它们表示的状态信息不会变得陈旧的任务交给了程序员

处理思想是只有在已选择的键的集合 ( Selected key set ) 中的选择键才被认为是包含了合法的就绪信息的，因此应当手动清除陈旧的选择键，清理一个 SelectKey 的 ready 集合的方式是将这个键从已选择的键的集合 ( Selected key set ) 中移除

这种框架提供了很多灵活性，通常的做法是在选择器上调用一次 select 操作 ( 这将更新已选择的键的集合 )，然后遍历 _selectKeys()_ 方法返回的键的集合，在按顺序检查每个键的过程中，相关的通道也根据键的就绪集合进行处理，然后将选择键从已选择的键的集合 ( Selected key set ) 中移除 ( 通过在 Iterator 对象上调用 _remove()_ 方法 )，然后检查下一个键，完成后，通过再次调用 _select()_ 方法重复这个过程
