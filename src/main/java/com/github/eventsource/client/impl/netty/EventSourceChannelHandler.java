package com.github.eventsource.client.impl.netty;

import com.github.eventsource.client.EventSourceClient;
import com.github.eventsource.client.EventSourceException;
import com.github.eventsource.client.EventSourceHandler;
import com.github.eventsource.client.impl.ConnectionHandler;
import com.github.eventsource.client.impl.EventStreamParser;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventSourceChannelHandler extends SimpleChannelUpstreamHandler implements ConnectionHandler {
    private static final Pattern STATUS_PATTERN = Pattern.compile("HTTP/1.1 (\\d+) (.*)");
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("Content-Type: text/event-stream;?.*");

    private final EventSourceHandler eventSourceHandler;
    private final EventSourceClient client;
    private final URI uri;
    private final EventStreamParser messageDispatcher;

    private final Timer timer = new HashedWheelTimer();
    private Channel channel;
    private boolean reconnectOnClose = true;
    private long reconnectionTimeMillis;
    private String lastEventId;
    private boolean eventStreamOk;
    private boolean headerDone;
    private Integer status;
    private AtomicBoolean reconnecting = new AtomicBoolean(false);

    public EventSourceChannelHandler(EventSourceHandler eventSourceHandler, long reconnectionTimeMillis, EventSourceClient client, URI uri) {
        this.eventSourceHandler = eventSourceHandler;
        this.reconnectionTimeMillis = reconnectionTimeMillis;
        this.client = client;
        this.uri = uri;
        this.messageDispatcher = new EventStreamParser(uri.toString(), eventSourceHandler, this);
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
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channel = null;
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (reconnectOnClose) {
            reconnect();
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        String line = (String) e.getMessage();
        if (status == null) {
            Matcher statusMatcher = STATUS_PATTERN.matcher(line);
            if (statusMatcher.matches()) {
                status = Integer.parseInt(statusMatcher.group(1));
                if (status != 200) {
                    eventSourceHandler.onError(new EventSourceException("Bad status from " + uri + ": " + status));
                    reconnect();
                }
                return;
            } else {
                eventSourceHandler.onError(new EventSourceException("Not HTTP? " + uri + ": " + line));
                reconnect();
            }
        }
        if (!headerDone) {
            if (CONTENT_TYPE_PATTERN.matcher(line).matches()) {
                eventStreamOk = true;
            }
            if (line.isEmpty()) {
                headerDone = true;
                if (eventStreamOk) {
                    eventSourceHandler.onConnect();
                } else {
                    eventSourceHandler.onError(new EventSourceException("Not event stream: " + uri + " (expected Content-Type: text/event-stream"));
                    reconnect();
                }
            }
        } else {
            messageDispatcher.line(line);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Throwable error = e.getCause();
        if(error instanceof ConnectException) {
            error = new EventSourceException("Failed to connect to " + uri, error);
        }
        eventSourceHandler.onError(error);
        ctx.getChannel().close();
    }

    public void setReconnectionTimeMillis(long reconnectionTimeMillis) {
        this.reconnectionTimeMillis = reconnectionTimeMillis;
    }

    @Override
    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }

    public EventSourceChannelHandler close() {
        reconnectOnClose = false;
        if (channel != null) {
            channel.close();
        }
        return this;
    }

    public ChannelFuture connect() {
        return client.connect(getConnectAddress(), this);
    }

    public EventSourceChannelHandler join() throws InterruptedException {
        if (channel != null) {
            channel.getCloseFuture().await();
        }
        return this;
    }

    private void reconnect() {
        if(!reconnecting.get()) {
            reconnecting.set(true);
            timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    reconnecting.set(false);
                    connect().await();
                }
            }, reconnectionTimeMillis, TimeUnit.MILLISECONDS);
        }
    }

    public InetSocketAddress getConnectAddress() {
        return new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort());
    }
}