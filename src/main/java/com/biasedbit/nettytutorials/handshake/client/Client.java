package com.biasedbit.nettytutorials.handshake.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author <a href="mailto:bruno@biasedbit.com">Bruno de Carvalho</a>
 */
public class Client {

    // internal vars ----------------------------------------------------------

    private final ClientListener listener;
    private ClientBootstrap bootstrap;
    private Channel connector;
    private final URI uri;

    // constructors -----------------------------------------------------------

    public Client(ClientListener listener, URI uri) {
        this.listener = listener;
        this.uri = uri;
    }

    // public methods ---------------------------------------------------------

    public boolean start() {
        // Standard netty bootstrapping stuff.
        Executor bossPool = Executors.newCachedThreadPool();
        Executor workerPool = Executors.newCachedThreadPool();
        ChannelFactory factory = new NioClientSocketChannelFactory(bossPool, workerPool);
        this.bootstrap = new ClientBootstrap(factory);

        this.bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ClientHandshakeHandler handshakeHandler = new ClientHandshakeHandler(5000, uri);

                return Channels.pipeline(
                        new HttpResponseDecoder(),
                        new HttpRequestEncoder(),
                        handshakeHandler);
//                        new ClientHandler(listener));
            }
        });

        ChannelFuture future = this.bootstrap.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
        if (!future.awaitUninterruptibly().isSuccess()) {
            System.out.println("--- CLIENT - Failed to connect to server at localhost:12345.");
            this.bootstrap.releaseExternalResources();
            return false;
        }

        this.connector = future.getChannel();
        return this.connector.isConnected();
    }

    public void stop() {
        if (this.connector != null) {
            this.connector.close().awaitUninterruptibly();
        }
        this.bootstrap.releaseExternalResources();
        System.out.println("--- CLIENT - Stopped.");
    }
}
