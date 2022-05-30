## 第 1 章  Netty——异步和事件驱动

### 1. Netty 简介

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220528162731061.png" alt="image-20220528162731061" style="zoom:80%;" />

#### 1.1 异步和事件驱动

本质上，一个既是异步的又是事件驱动的系统会表现出一种特殊的、对我们来说极具价值的行为：它可以以任意的顺序响应在任意的时间点产生的事件

这种能力对于实现最高级别的可伸缩性至关重要，定义为：一种系统、网络或者进程在需要处理的工作不断增长时，可以通过某种可行的方式或者扩大它的处理能力来适应这种增长的能力

异步和可伸缩性之间的联系：

- 非阻塞网络调用使得我们可以不必等待一个操作的完成。完全异步的 I/O 正是基于这个特性构建的，并且更进一步：异步方法会立即返回，并且在它完成时，会直接或者在稍后的某个时间点通知用户
- 选择器使得我们能够通过较少的线程便可监视许多连接上的事件

将这些元素结合在一起，与使用阻塞 I/O 来处理大量事件相比，使用非阻塞 I/O 来处理更快速、更经济。从网络编程的角度来看，这是构建我们理想系统的关键，而且你会看到，这也是 Netty 的设计底蕴的关键

### 2. Netty 的核心组件

下面将讨论 Netty 的主要构件块，现在，只需要将它们看作是域对象，而不是具体的 Java 类

- Channel
- 回调
- Future
- 事件和 ChannelHandler

这些构件块代表了不同类型的构造：资源、逻辑以及通知，你的应用程序将使用它们来访问网络以及流经网络的数据

#### 2.1 Channel

Channel 是 Java NIO 的一个基本构造，它代表一个到实体 ( 如一个硬件设备、一个文件、一个网络套接字或者一个能够执行一个或者多个不同的 I/O 操作的程序组件 ) 的开放连接，如读操作和写操作

目前，可以把 Channel 看作是传入 ( 入站 ) 或者传出 ( 出战 ) 数据的载体，因此，它可以被打开或者被关闭，连接或者断开连接

#### 2.2 回调

一个回调其实就是一个方法，一个指向已经被提供给另外一个方法的方法的引用。这使得后者 ( 指接受回调的方法 ) 可以在适当的时候调用前者。回调在广泛的编程场景中都有应用，而且也是在操作完成后通知相关方最常见的方式之一

Netty 在内部使用了回调来处理事件，当一个回调被触发时，相关的事件可以被一个 interfaceChannelHandler 的实现处理，以下代码展示了一个例子：当一个新的连接已经被建立时，ChannelHandler 的 channelActive() 回调方法将会被调用，并打印出一条信息

```java
// 被回调触发的 ChannelHandler
public class ConnectHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Client " + ctx.channel().remoteAddress() + " connected");
    }
}
```

#### 2.3 Future

Future 提供了另一种在操作完成时通知应用程序的方式。这个对象可以看作是一个异步操作的结果的占位符，它将在未来的某个时刻完成，并提供对其结果的访问

JDK 预置了 interface java.util.concurrent.Future，但是其所提供的实现，只允许手动检查对应的操作是否已经完成，或者一直阻塞直到它完成，这是非常繁琐的，所以 Netty 提供了它自己的实现——ChannelFuture，用于在执行异步操作的时候使用

ChannelFuture 提供了几种额外的方法，这些方法使得我们能够注册一个或者多个 ChannelFutureListener 实例。监听器的回调方法 operationComplete()，将会在对应的操作完成时被调用，然后监听器可以判断该操作是成功地完成了还是出错了。如果是后者，我们可以检索产生的 Throwable. 简而言之，由 ChannelFutureListener 提供的通知机制消除了手动检查对应的操作是否完成的必要

每个 Netty 的出站 I/O 操作都将返回一个 ChannelFuture，也就是说，它们都不会阻塞。正如我们前面所提到过的一样，Netty 完全是异步和事件驱动的

以下代码展示了一个 ChannelFuture 作为一个 I/O 操作的一部分返回的例子，这里，connect() 方法将会直接返回，而不会阻塞，该调用将会在后台完成，这究竟什么时候会发生则取决于若干的因素，但这个关注点已经从代码中抽象出来了，因为线程不用阻塞以等待对应的操作完成，所以它可以同时做其他的工作，从而更加有效地利用资源

```java
// 异步地建立连接
Channel channel = ...;
// Does not block
ChannelFuture future = channel.connect(new InetSocketAddress("192.168.0.1", 25));
```

以下代码展示了如何使用 ChannelFutureListener. 首先，要连接到远程节点上，然后，要注册一个新的 ChannelFutureListener 到对 connect() 方法的调用所返回的 ChannelFuture 上。当该监听器被通知连接已经建立的时候，要检查对应的状态，如果该操作是成功的，那么将数据写到该 Channel. 否则，要从 ChannelFuture 中检索对应的 Throwable

```java
Channel channel = ...;
// Does not block, 异步地连接到远程节点
ChannelFuture future = channel.connect(new InetSocketAddress("192.168.0.1", 25));

// 注册一个 ChannelFutureListener, 以便在操作完成时获得通知
future.addListener(new ChannelFutureListener() {
    @Override
    public void operationComplete(ChannelFuture future) {
        if (future.isSuccess()) {
            // 如果操作是成功的, 则创建一个 ByteBuf 以持有数据
            ByteBuf buffer = Unpooled.copiedBuffer("Hello", Charset.defaultCharset());
            // 将数据异步地发送到远程节点, 返回一个 ChannelFuture
            ChannelFuture wf = future.channel().writeAndFlush(buffer);
            // ...
        } else {
            // 如果发送错误, 则访问描述原因的 Throwable
            Throwable cause = future.cause();
            cause.printStackTrace();
        }
    }
});
```

需要注意的是，对错误的出来完全取决于你、目标，当然也包括目前任何对于特定类型的错误加以的限制。例如，如果连接失败，你可以尝试重新连接或者建立一个到另一个远程节点的连接

如果你把 ChannelFutureListener 看作是回调的一个更加精细的版本，那么你是对的。事实上，回调和 Future 是相互补充的机制，它们相互结合，构成了 Netty 本身的关键构件块之一

#### 2.4 事件和 ChannelHandler

Netty 使用不同的事件来通知我们状态的改变或者是操作的状态。这使得我们能够基于已经发生的事件来触发适当的动作，这些动作可能是：

- 记录日志
- 数据转换
- 流控制
- 应用程序逻辑

Netty 是一个网络编程框架，所以事件是按照它们与入站或出站数据流的相关性进行分类的。可能由入站数据或者相关的状态更改而触发的事件包括：

- 连接已被激活或者连接失活
- 数据读取
- 用户事件
- 错误事件

出站事件是未来将会触发的某个动作的操作结果，这些动作包括：

- 打开或者关闭到远程节点的连接
- 将数据写到或者冲刷到套接字

每个事件都可以被分发给 ChannelHandler 类中的某个用户实现的方法。这是一个很好的将事件驱动范式直接转换为应用程序构件块的例子。下图展示了一个事件是如何被一个这样的 ChannelHandler 链处理的：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220530124414841.png" alt="image-20220530124414841" style="zoom:80%;" />

Netty 的 ChannelHandler 为上图所示的处理器提供了基本的抽象，目前可以认为每个 ChannelHandler 的实例都类似于一种为了响应特定事件而被执行的回调

Netty 提供了大量预定义的可以开箱即用的 ChannelHandler 实现，包括用于各种协议 ( 如 HTTP 和 SSL/TLS ) 的 ChannelHandler

#### 2.5 将它们放在一起

##### 2.5.1 Future、回调和 ChannelHandler

Netty 的异步编程模型是建立在 Future 和回调的概念之上的，而将事件派发到 ChannelHandler 的方法则发生在更深的层次上，结合在一起，这些元素就提供了一个处理环境，使你的应用程序逻辑可以独立于任何网络操作相关的顾虑而独立地演变，这也是 Netty 的设计方式的一个关键目标

拦截操作以及高速地转换入站数据和出站数据，都只需要你提供回调或者利用操作所返回的 Future，这使得链接操作变得既简单又高效，并且促进了可重用的通用代码的编写

##### 2.5.2 选择器、事件和 EventLoop

Netty 通过触发事件将 Selector 从应用程序中抽象出来，消除了所有本来将需要手动编写的派发代码，在内部，将会为每个 Channel 分配一个 EventLoop，用以处理所有事件，包括：

- 注册感兴趣的事件
- 将事件派发给 ChannelHandler
- 安排进一步的动作

EventLoop 本身只由一个线程驱动，其处理了一个 Channel 的所有 I/O 事件，并且在该 EventLoop 的整个生命周期内都不会改变。这个简单而强大的设计消除了你可能有的在 ChannelHandler 实现中需要进行同步的任何顾虑，因此，你可以专注于提供正确的逻辑，用来在有感兴趣的数据要处理的时候执行
