package com.github.eventsource.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.string.StringDecoder;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EventSourceClient {
    private final ClientBootstrap bootstrap;
    private final Executor eventExecutor;

    private final HashMap<Channel, ChannelUpstreamHandler> handlerMap = new HashMap<Channel, ChannelUpstreamHandler>();

    public EventSourceClient() {
        this(Executors.newSingleThreadExecutor());
    }

    public EventSourceClient(Executor eventExecutor) {
        this.eventExecutor = eventExecutor;
        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newSingleThreadExecutor(),
                        Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("line", new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()));
                pipeline.addLast("string", new StringDecoder());

                pipeline.addLast("encoder", new HttpRequestEncoder());
                pipeline.addLast("es-handler", new Handler());
                return pipeline;
            }
        });
    }

    public ChannelFuture connect(InetSocketAddress address, ChannelUpstreamHandler handler) {
        synchronized (handlerMap) {
            ChannelFuture channelFuture = bootstrap.connect(address);
            handlerMap.put(channelFuture.getChannel(), handler);
            return channelFuture;
        }
    }

    private class Handler extends SimpleChannelUpstreamHandler {
        @Override
        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
            final ChannelUpstreamHandler handler;
            synchronized (handlerMap) {
                handler = handlerMap.get(ctx.getChannel());
            }
            if (handler == null) {
                super.handleUpstream(ctx, e);

                if (e instanceof ChannelStateEvent && ((ChannelStateEvent) e).getState() == ChannelState.OPEN) {
                    return; //Do nothing, this one will not be dispatched to handler, but it's ok
                }

                System.err.println("Something wrong with dispatching");
            } else {
                handler.handleUpstream(ctx, e);

                if (e instanceof ChannelStateEvent) {
                    ChannelStateEvent stateEvent = (ChannelStateEvent) e;
                    if (stateEvent.getState() == ChannelState.BOUND && stateEvent.getValue() == null) {
                        synchronized (handlerMap) {
                            handlerMap.remove(ctx.getChannel());
                        }
                    }
                }
            }
        }
    }

    public Executor getEventExecutor() {
        return eventExecutor;
    }

    public void shutdown() {
        bootstrap.releaseExternalResources();
    }
}
