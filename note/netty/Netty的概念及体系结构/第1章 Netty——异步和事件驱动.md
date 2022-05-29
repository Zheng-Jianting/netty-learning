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
// y
Channel channel = ...;
// Does not block
ChannelFuture future = channel.connect(new InetSocketAddress("192.168.0.1", 25));
```

