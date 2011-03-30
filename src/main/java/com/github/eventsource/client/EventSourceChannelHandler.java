package com.github.eventsource.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class EventSourceChannelHandler extends SimpleChannelUpstreamHandler implements MessageEmitter {
    private static final Pattern STATUS_PATTERN = Pattern.compile("HTTP/1.1 (\\d+) (.*)");
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("Content-Type: text/event-stream");
    
    private final Executor executor;
    private final ClientBootstrap bootstrap;
    private final URI uri;
    private final EventSourceClientHandler eventSourceHandler;
    private final MessageDispatcher messageDispatcher;
    private final Timer timer = new HashedWheelTimer();

    private Channel channel;
    private boolean connecting = false;
    private boolean reconnectOnClose = true;
    private long reconnectionTimeMillis;
    private String lastEventId;
    private boolean eventStreamOk;
    private boolean headerDone;
    private Integer status;

    public EventSourceChannelHandler(Executor executor, long reconnectionTimeMillis, ClientBootstrap bootstrap, URI uri, EventSourceClientHandler eventSourceHandler) {
        this.executor = executor;
        this.reconnectionTimeMillis = reconnectionTimeMillis;
        this.bootstrap = bootstrap;
        this.uri = uri;
        this.eventSourceHandler = eventSourceHandler;
        this.messageDispatcher = new MessageDispatcher(this, uri.toString());
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        super.handleUpstream(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toString());
        request.addHeader(Names.ACCEPT, "text/event-stream");
        request.addHeader(Names.HOST, uri.getHost());
        request.addHeader(Names.ORIGIN, "http://" + uri.getHost());
        request.addHeader(Names.CACHE_CONTROL, "no-cache");
        if (lastEventId != null) {
            request.addHeader("Last-Event-ID", lastEventId);
        }
        e.getChannel().write(request);
        channel = e.getChannel();
        connecting = false;
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        emitDisconnect();
        channel = null;
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (!connecting && reconnectOnClose) {
            connecting = true;
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    bootstrap.connect().await();
                }
            }, reconnectionTimeMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        String line = (String) e.getMessage();
        if(status == null) {
            Matcher statusMatcher = STATUS_PATTERN.matcher(line);
            if(statusMatcher.matches()) {
                status = Integer.parseInt(statusMatcher.group(1));
                if(status != 200) {
                    throw new RuntimeException("Bad status: " + status);
                }
                return;
            } else {
                throw new RuntimeException("Not HTTP? " + line);
            }
        }
        if(!headerDone) {
            if(CONTENT_TYPE_PATTERN.matcher(line).matches()) {
                eventStreamOk = true;
            }
            if(line.isEmpty()) {
                headerDone = true;
                if(eventStreamOk) {
                    emitConnect();
                } else {
                    throw new RuntimeException("Not event stream");
                }
            }
        } else {
            messageDispatcher.line(line);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Throwable error = e.getCause();
        emitError(error);
        ctx.getChannel().close();
    }

    @Override
    public void setReconnectionTime(long reconnectionTimeMillis) {
        this.reconnectionTimeMillis = reconnectionTimeMillis;
    }

    public EventSourceChannelHandler close() {
        reconnectOnClose = false;
        if (channel != null) {
            channel.close();
        }
        return this;
    }

    public EventSourceChannelHandler join() throws InterruptedException {
        if (channel != null) {
            channel.getCloseFuture().await();
        }
        return this;
    }

    private void emitConnect() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                eventSourceHandler.onConnect();
            }
        });
    }

    private void emitDisconnect() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                eventSourceHandler.onDisconnect();
            }
        });
    }

    private void emitError(final Throwable error) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                eventSourceHandler.onError(error);
            }
        });
    }

    public void emitMessage(final String event, final com.github.eventsource.client.MessageEvent message) {
        if (message.lastEventId != null) {
            lastEventId = message.lastEventId;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                eventSourceHandler.onMessage(event, message);
            }
        });
    }
}