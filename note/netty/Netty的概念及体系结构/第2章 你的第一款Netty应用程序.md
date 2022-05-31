## 第 2 章  你的第一款 Netty 应用程序

### 1. Netty 客户端 / 服务器概览

下图从高层次上展示了一个你将要编写的 Echo 客户端和服务器应用程序，理论上，所能支持的客户端数量仅受限于系统的可用资源 ( 以及所使用的 JDK 版本可能会施加的限制 )

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220530225136805.png" alt="image-20220530225136805" style="zoom:80%;" />

Echo 客户端和服务器之间的交互是非常简单的，在客户端建立一个连接之后，它会向服务器发送一个或多个消息，反过来，服务器又会将每个消息回送给客户端。虽然它本身看起来好像用处不大，但它充分体现了客户端 / 服务器系统中典型的请求 - 响应交互模式

### 2. 编写 Echo 服务器

所有的 Netty 服务器都需要以下两部分：

- 至少一个 ChannelHandler —— 该组件实现了服务器对从客户端接收的数据的处理，即它的业务逻辑
- 引导 —— 这是配置服务器的启动代码。至少，它会将服务器绑定到它要监听连接请求的端口上

#### 2.1 ChannelHandler 和业务逻辑

ChannelHandler 是一个接口族的父接口，它的实现负责接收并响应事件通知。因为 Echo 服务器会响应传入的消息，所以它需要实现 ChannelInboundHandler 接口，用来定义响应入站事件的方法。这个简单的应用程序只需要用到少量的这些方法，所以继承 ChannelInboundHandlerAdapter 类也就足够了，它提供了 ChannelInboundHandler 的默认实现

我们感兴趣的方法是：

- channelRead() —— 对于每个传入的消息都要调用
- channelReadComplete() —— 通知 ChannelInboundHandler 最后一次对 channelRead() 的调用是当前批量读取中的最后一条消息
- exceptionCaught() —— 在读取操作期间，有异常抛出时会调用

```java
package com.zhengjianting.nia.chapter2.echoserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable // 标示一个 ChannelHandler 可以被多个 Channel 安全地共享
public class EchoServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf) msg;
        System.out.println("Server received: " + in.toString(CharsetUtil.UTF_8));
        ctx.write(in); // 将接收到的消息写给发送者, 而不冲刷出站消息
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // 将未决消息冲刷到远程节点, 并且关闭该 Channel
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace(); // 打印异常栈跟踪
        ctx.close(); // 关闭该 Channel
    }
}
```

ChannelInboundHandlerAdapter 有一个直观的 API，并且它的每个方法都可以被重写以挂钩到事件生命周期的恰当点上。因为需要处理所有接收到的数据，所以你重写了 channelRead() 方法。在这个服务器应用程序中，你将数据简单地回送给了远程节点

重写 exceptionCaugh() 方法允许你对 Throwable 的任何子类型做出反应，在这里你记录了异常并关闭了连接。虽然一个更加完善的应用程序也许会尝试从异常中恢复，但在这个场景下，只是通过简单地关闭连接来通知远程节点发生了错误

```tex
如果不捕获异常, 会发生什么呢
   每个 Channel 都拥有一个与之相关联的 ChannelPipeline, 其持有一个 ChannelHandler 的实例链。在默认的情况下, ChannelHandler 会把对它的方法的调用转发给链中的下一个 ChannelHandler。因此, 如果 exceptionCaught() 方法没有被该链中的某处实现, 那么所接收的异常将会被传递到 ChannelPipeline 的尾端并被记录。为此, 你的应用程序应该提供至少有一个实现了 exceptionCaught() 方法的 ChannelHandler
```

除了 ChannelInboundHandlerAdapter 之外，还有很多需要学习的 ChannelHandler 的子类型和实现，我们将在第 6 章和第 7 章中对它们进行详细的阐述。目前，请记住下面这些关键点：

- 针对不同类型的事件来调用 ChannelHandler
- 应用程序通过实现或者扩展 ChannelHandler 来挂钩到事件的生命周期，并且提供自定义的应用程序逻辑
- 在架构上，ChannelHandler 有助于保持业务逻辑与网络处理代码的分离。这简化了开发过程，因为代码必须不断地演化以响应不断变化的需求

#### 2.2 引导服务器

在讨论过由 EchoServerHandler 实现的核心业务逻辑之后，我们现在可以探讨引导服务器本身的过程了，具体涉及以下内容：

- 绑定到服务器将在其上监听并接受传入连接请求的端口
- 配置 Channel，以将有关的入站消息通知给 EchoServerHandler 实例

```java
package com.zhengjianting.nia.chapter2.echoserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

public class EchoServer {
    private final int port;

    public EchoServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: " + EchoServer.class.getSimpleName() + " <port>");
        }
        int port = Integer.parseInt(args[0]);
        new EchoServer(port).start();
    }

    public void start() throws Exception {
        final EchoServerHandler serverHandler = new EchoServerHandler();
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 添加一个 EchoServerHandler 到子 Channel 的 ChannelPipeline
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(serverHandler); // EchoServerHandler 被标注为 @Sharable, 所以我们可以总是使用相同的实例
                        }
                    });
            ChannelFuture f = b.bind().sync();
            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }
}
```

引导过程中所需要的步骤如下：

- 创建一个 ServerBootstrap 的实例以引导和绑定服务器
- 创建并分配一个 NioEventLoopGroup 实例以进行事件的处理，如接受新连接以及读/写数据
- 指定服务器绑定的本地的 InetSocketAddress
- 使用一个 EchoServerHandler 的实例初始化每一个新的 Channel
- 调用 ServerBootstrap.bind() 方法以绑定服务器

### 3. 编写 Echo 客户端

Echo 客户端将会：

- 连接到服务器
- 发送一个或者多个消息
- 对于每个消息，等待并接收从服务器发回的相同的消息
- 关闭连接

编写客户端所涉及的两个主要代码部分也是业务逻辑和引导，和你在服务器中看到的一样

#### 3.1 通过 ChannelHandler 实现客户端逻辑

如同服务器，客户端将拥有一个用来处理数据的 ChannelInboundHandler. 在这个场景下，你将扩展 SimpleChannelInboundHandler 类以处理所有必须的任务，这要求重写下面的方法：

- channelActive() —— 在到服务器的连接已经建立之后将被调用
- channelRead0() —— 当从服务器接收到一条消息时被调用
- exceptionCaugut() —— 在处理过程中引发异常时被调用

```java
package com.zhengjianting.nia.chapter2.echoclient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable // 标记该类的实例可以被多个 Channel 共享
public class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
    // 当被通知 Channel 是活跃的时候, 发送一条消息
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(Unpooled.copiedBuffer("Netty rocks!", CharsetUtil.UTF_8));
    }

    // 记录已接收消息的转储
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
        System.out.println("Client received: " + byteBuf.toString(CharsetUtil.UTF_8));
    }

    // 在发生错误时, 记录错误并关闭 Channel
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
```

首先，你重写了 channelActive() 方法，其将在一个连接建立时被调用。这确保了数据将会被尽可能快地写入服务器，其在这个场景下是一个编码了字符串 "Netty rocks!" 的字节缓冲区

接下来，你重写了 channelRead0() 方法，每当接收数据时，都会调用这个方法。需要注意的是，由服务器发送的消息可能会被分块接收。也就是说，如果服务器发送了 5 字节，那么不能保证这 5 字节会被一次性接收。即使是对于这么少量的数据，channelRead0() 方法也可能会被调用两次，第一个使用一个持有 3 字节的 ByteBuf ( Netty 的字节容器 )，第二次使用一个持有 2 字节的 ByteBuf。作为一个面向流的协议，TCP 保证了字节数组将会按照服务器发送它们的顺序被接收

重写的第三个方法是 exceptionCaught()，如同代码所示，记录 Throwable，关闭 Channel，在这个场景下，终止到服务器的连接