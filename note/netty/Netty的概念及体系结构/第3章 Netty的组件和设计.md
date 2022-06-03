## 第 3 章  Netty 的组件和设计

从高层次的角度来看，Netty 解决了两个相应的关注领域，我们可将其大致标记为技术的和体系结构的。首先，它的基于 Java NIO 的异步的和事件驱动的实现，保证了高负载下应用程序性能的最大化和可伸缩性。其次，Netty 也包含了一组设计模式，将应用程序逻辑从网络层解耦，简化了开发过程，同时也最大限度地提高了可测试性、模块化以及代码的可重用性

### 1. Channel、EventLoop 和 ChannelFuture

这些类合在一起，可以被认为是 Netty 网络抽象的代表：

- Channel —— Socket
- EventLoop —— 控制流、多线程处理、并发
- ChannelFuture —— 异步通知

#### 1.1 Channel 接口

基本的 I/O 操作 ( bind()、connect()、read()、write() ) 依赖于底层网络传输所提供的原语。在基于 Java 的网络编程中，其基本的构造是 class Socket. Netty 的 Channel 接口所提供的 API，大大地降低了直接使用 Socket 类的复杂性。此外，Channel 也是用于许多预定义的、专门化实现的广泛类层次结构的根，下面是一个简短的部分清单：

- EmbeddedChannel
- LocalServerChannel
- NioDatagramChannel
- NioSctpChannel
- NioSocketChannel

#### 1.2 EventLoop 接口

EventLoop 定义了 Netty 的核心抽象，用于处理连接的生命周期中所发生的事件。下图在高层次上说明了 Channel、EventLoop、Thread 以及 EventLoopGroup 之间的关系

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220601223808949.png" alt="image-20220601223808949" style="zoom:80%;" />

这些关系是：

- 一个 EventLoopGroup 包含一个或者多个 EventLoop
- 一个 EventLoop 在它的生命周期内只和一个 Thread 绑定
- 所有由 EventLoop 处理的 I/O 事件都将在它专有的 Thread 上被处理
- 一个 Channel 在它的生命周期内只注册于一个 EventLoop
- 一个 EventLoop 可能会被分配给一个或多个 Channel

在这种设计中，一个给定 Channel 的 I/O 操作都是由相同的 Thread 执行的，实际上消除了对于同步的需要

#### 1.3 ChannelFuture 接口

Netty 中所有的 I/O 操作都是异步的。因为一个操作可能不会立即返回，所以我们需要一种用于在之后的某个时间点确定其结果的方法。为此，Netty 提供了 ChannelFuture 接口，其 addListener() 方法注册了一个 ChannelFutureListener，以便在某个操作完成时 ( 无论是否成功 ) 得到通知

可以将 ChannelFuture 看作是将来要执行的操作的结果的占位符。它究竟什么时候被执行则可能取决于若干的因素，因此不可能准确地预测，但是可以肯定的是它将会被执行。此外，所有属于同一个 Channel 的操作被保证其将以它们被调用的顺序被执行

### 2. ChannelHandler 和 ChannelPipeline

现在，我们将更加细致地看一看那些管理数据流以及执行应用程序处理逻辑的组件

#### 2.1 ChannelHandler 接口

从应用程序开发人员的角度来看，Netty 的主要组件是 ChannelHandler，它充当了所有处理入站和出站数据的应用程序逻辑的容器。这是可行的，因为 ChannelHandler 的方法是由网络事件触发的。事实上，ChannelHandler 可专门用于几乎任何类型的动作，例如将数据从一种格式转换为另外一种格式，或者处理转换过程中所抛出的异常

#### 2.2 ChannelPipeline 接口

ChannelPipeline 提供了 ChannelHandler 链的容器，并定义了用于在该链上传播入站和出站事件流的 API. 当 Channel 被创建时，它会被自动地分配到它专属的 ChannelPipeline

ChannelHandler 安装到 ChannelPipeline 中的过程如下所示：

- 一个 ChannelInitializer 的实现被注册到了 ServerBootstrap 中 ( 或者用于客户端的 Bootstrap )
- 当 ChannelInitializer.initChannel() 方法被调用时，ChannelInitializer 将在 ChannelPipeline 中安装一组自定义的 ChannelHandler
- ChannelInitializer 将它自己从 ChannelPipeline 中移除

ChannelHandler 是专为支持广泛的用途而设计的，可以将它看作是处理往来 ChannelPipeline 事件 ( 包括数据 ) 的任何代码的通用容器

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220602001416144.png" alt="image-20220602001416144" style="zoom:80%;" />

使得事件流经 ChannelPipeline 是 ChannelHandler 的工作，它们是在应用程序的初始化或者引导阶段被安装的。这些对象接收事件、执行它们所实现的处理逻辑，并将数据传递给链中的下一个 ChannelHandler. 它们的执行顺序是由它们被添加的顺序所决定的。实际上，被我们称为 ChannelPipeline 的是这些 ChannelHandler 的编排顺序

入站和出站 ChannelHandler 可以被安装到同一个 ChannlePipeline 中

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220602002226957.png" alt="image-20220602002226957" style="zoom:80%;" />

如果一个消息或者任何其他的入站事件被读取，那么它会从 ChannelPipeline 的头部开始流动，并被传递给下一个 ChannelInboundHandler. 这个 ChannelHandler 不一定会实际地修改数据，具体取决于它的具体功能，在这之后，数据将会被传递给链中的下一个 ChannelInboundHandler. 最终，数据将会到达 ChannelPipeline 的尾端，届时，所有处理就都结束了

数据的出站运动 ( 即正在被写的数据 ) 在概念上也是一样的。在这种情况下，数据将从 ChannelOutboundHandler 链的尾端开始流动，直到它到达链的头部为止。在这之后，出站数据将会到达网络传输层，这里显示为 Socket。通常情况下，这将触发一个写操作

Netty 能区分 ChannelInboundHandler 实现和 ChannelOutboundHandler 实现，并确保数据只会在具有相同定向类型的两个 ChannelHandler 之间传递

当 ChannelHandler 被添加到 ChannelPipeline 时，它将会被分配一个 ChannelHandlerContext，其代表了 ChannelHandler 和 ChannelPipeline 之间的关联关系，事件可以通过 ChannelHandlerContext 被传递给当前 ChannelHandler 链中的下一个 ChannelHandler，ChannelHandlerContext 的主要功能是关联它所关联的 ChannelHandler 和在同一个 ChannelPipeline 中的其他 ChannelHandler 之间的交互

ChannelHandlerContext 有很多的方法，其中一些方法也存在于 Channel 和 ChannelPipeline 本身上，但是有一点重要的不同：

- 如果调用 Channel 或者 ChannelPipeline 上的这些方法，它们将沿着整个 ChannelPipeline 进行传播
- 而调用位于 ChannelHandlerContext 上的相同方法，则将从当前所关联的 ChannelHandler 开始，并且只会传播给位于该 ChannelPipeline 中的下一个能够处理该事件的 ChannelHandler

#### 2.3 更加深入地了解 ChannelHandler

Netty 以适配器类的形式提供了大量默认的 ChannelHandler 实现，其旨在简化应用程序处理逻辑的开发过程。ChannelPipeline 中的每个 ChannelHandler 将负责把事件转发到链中的下一个 ChannelHandler，这些适配器类 ( 及它们的子类 ) 将自动执行这个操作，所以你可以只重写那些你想要特殊处理的方法和事件

有一些适配器类可以将编写自定义的 ChannelHandler 所需要的努力降到最低限度，因为它们提供了定义在对应接口中的所有方法的默认实现，下面是编写自定义 ChannelHandler 时经常会用到的适配器类：

- ChannelHandlerAdapter
- ChannelInboundHandlerAdapter
- ChannelOutboundHandlerAdapter
- ChannelDuplexHandler

接下来我们将研究 3 个 ChannelHandler 的子类型：编码器、解码器和 SimpleChannelInboundHandler\<T\> —— ChannelInboundHandlerAdapter 的一个子类

#### 2.4 编码器和解码器

当你通过 Netty 发送或者接收一个消息的时候，就将会发生一次数据转换。入站消息会被解码，也就是说，从字节转换为另一种格式，通常是一个 Java 对象。如果是出站消息，则会发生相反方向的转换：它将从它的当前格式被编码为字节。这两种方向的转换的原因很简单：网络数据总是一系列的字节

所有由 Netty 提供的编码器 / 解码器适配器类都实现了 ChannelOutboundHandler 或者 ChannelInboundHandler 接口：

- 对于入站数据来说，channelRead 方法 / 事件已经被重写了。对于每个从入站 Channel 读取的消息，这个方法都将会被调用。随后，它将调用由预置解码器所提供的 decode() 方法，并将已解码的字节转发给 ChannelPipeline 中的下一个 ChannelInboundHandler
- 出站消息的模式是相反方向的：编码器将消息转换为字节，并将它们转发给下一个 ChannelOutboundHandler

#### 2.5 抽象类 SimpleChannelInboundHandler

最常见的情况是，你的应用程序会利用一个 ChannelHandler 来接收解码消息，并对该数据应用业务逻辑。要创建一个这样的 ChannelHandler，你只需要扩展基类 SimpleChannelInboundHandler\<T\>，其中 T 是你要处理的消息的 Java 类型

在这种类型 ChannelHandler 中，最重要的方法是 channelRead0(ChannelHandlerContext，T). 除了要求不要阻塞当前的 I/O 线程之外，其具体实现完全取决于你

### 3. 引导

Netty 的引导类为应用程序的网络层配置提供了容器，这涉及将一个进程绑定到某个指定的端口，或者将一个进程连接到另一个运行在某个指定主机的指定端口上的进程

通常来说，我们把前面的用例称作引导一个服务器，后面的用例称作引导一个客户端。虽然这个术语简单方便，但是它略微掩盖了一个重要的事实，即服务器和客户端实际上表示了不同的网络行为，换句话说，是监听传入的连接还是建立到一个或者多个进程的连接

因此，有两种类型的引导：一种用于客户端 ( 简单地称为 Bootstrap )，而另一种 ( ServerBootstrap ) 用于服务器。无论你的应用程序使用哪种协议或者处理哪种类型的数据，唯一决定它使用哪种引导类的是它作为一个客户端还是作为一个服务器，下表比较了这两种类型的引导类：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220603171533607.png" alt="image-20220603171533607" style="zoom:80%;" />

这两种类型的引导类之间的第一个区别已经讨论过了：ServerBootstrap 将绑定到一个端口，因为服务器必须要监听连接，而 Bootstrap 则是由想要连接到远程节点的客户端应用程序所使用的

第二个区别可能更加明显，引导一个客户端只需要一个 EventLoopGroup，但是一个 ServerBootstrap 则需要两个 ( 实际上，ServerBootstrap 类也可以只使用一个 EventLoopGroup，此时其将在两个场景下共用同一个 EventLoopGroup )

因为服务器需要两组不同的 Channel. 第一组将只包含一个 ServerChannel，代表服务器自身的已绑定到某个本地端口的正在监听的套接字。而第二组将包含所有已创建的用来处理传入客户端连接 ( 对于每个服务器已经接受的连接都有一个 ) 的 Channel。下图说明了这个模型，并且展示了为何需要两个不同的 EventLoopGroup

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220603173740304.png" alt="image-20220603173740304" style="zoom:80%;" />

与 ServerChannel 相关联的 EventLoopGroup 将分配一个负责为传入连接请求创建 Channel 的 EventLoop. 一旦连接被接受，第二个 EventLoopGroup 就会给它的 Channel 分配一个 EventLoop
