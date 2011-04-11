package com.github.eventsource.client;

import com.github.eventsource.client.impl.AsyncEventSourceHandler;
import com.github.eventsource.client.impl.netty.EventSourceChannelHandler;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.string.StringDecoder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EventSource  {
    public static final long DEFAULT_RECONNECTION_TIME_MILLIS = 2000;

    public static final int CONNECTING = 0;
    public static final int OPEN = 1;
    public static final int CLOSED = 2;

    private final ClientBootstrap bootstrap;
    private final EventSourceChannelHandler clientHandler;

    private int readyState;

    /**
     * Creates a new <a href="http://dev.w3.org/html5/eventsource/">EventSource</a> client. The client will reconnect on 
     * lost connections automatically, unless the connection is closed explicitly by a call to 
     * {@link com.github.eventsource.client.EventSource#close()}.
     *
     * For sample usage, see examples at <a href="https://github.com/aslakhellesoy/eventsource-java/tree/master/src/test/java/com/github/eventsource/client">GitHub</a>.
     * 
     * @param executor the executor that will receive events
     * @param reconnectionTimeMillis delay before a reconnect is made - in the event of a lost connection
     * @param uri where to connect
     * @param eventSourceHandler receives events
     * @see #close()
     */
    public EventSource(Executor executor, long reconnectionTimeMillis, final URI uri, EventSourceHandler eventSourceHandler) {
        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newSingleThreadExecutor(),
                        Executors.newSingleThreadExecutor()));
        bootstrap.setOption("remoteAddress", new InetSocketAddress(uri.getHost(), uri.getPort()));

        clientHandler = new EventSourceChannelHandler(new AsyncEventSourceHandler(executor, eventSourceHandler), reconnectionTimeMillis, bootstrap, uri);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("line", new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()));
                pipeline.addLast("string", new StringDecoder());

                pipeline.addLast("encoder", new HttpRequestEncoder());
                pipeline.addLast("es-handler", clientHandler);
                return pipeline;
            }
        });
    }

    public EventSource(String uri, EventSourceHandler eventSourceHandler) {
        this(URI.create(uri), eventSourceHandler);
    }

    public EventSource(URI uri, EventSourceHandler eventSourceHandler) {
        this(Executors.newSingleThreadExecutor(), DEFAULT_RECONNECTION_TIME_MILLIS, uri, eventSourceHandler);
    }

    public ChannelFuture connect() {
        readyState = CONNECTING;
        return bootstrap.connect();
    }

    /**
     * Close the connection
     *
     * @return self
     */
    public EventSource close() {
        clientHandler.close();
        return this;
    }

    /**
     * Wait until the connection is closed
     *
     * @return self
     * @throws InterruptedException if waiting was interrupted
     */
    public EventSource join() throws InterruptedException {
        clientHandler.join();
        return this;
    }
}
