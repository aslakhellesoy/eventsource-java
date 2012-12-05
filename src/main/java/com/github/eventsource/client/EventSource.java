package com.github.eventsource.client;

import com.github.eventsource.client.impl.AsyncEventSourceHandler;
import com.github.eventsource.client.impl.netty.EventSourceChannelHandler;
import org.jboss.netty.channel.ChannelFuture;

import java.net.URI;
import java.util.concurrent.Executor;

public class EventSource {
    public static final long DEFAULT_RECONNECTION_TIME_MILLIS = 2000;

    private final EventSourceChannelHandler clientHandler;

    /**
     * Creates a new <a href="http://dev.w3.org/html5/eventsource/">EventSource</a> client. The client will reconnect on
     * lost connections automatically, unless the connection is closed explicitly by a call to
     * {@link com.github.eventsource.client.EventSource#close()}.
     *
     * For sample usage, see examples at <a href="https://github.com/aslakhellesoy/eventsource-java/tree/master/src/test/java/com/github/eventsource/client">GitHub</a>.
     *
     * @param eventSourceClient      EventSourceClient to start event source at
     * @param reconnectionTimeMillis delay before a reconnect is made - in the event of a lost connection
     * @param uri where to connect
     * @param eventSourceHandler receives events
     * @see #close()
     */
    public EventSource(EventSourceClient eventSourceClient, long reconnectionTimeMillis, final URI uri, EventSourceHandler eventSourceHandler) {
        clientHandler = new EventSourceChannelHandler(new AsyncEventSourceHandler(eventSourceClient.getEventExecutor(), eventSourceHandler), reconnectionTimeMillis, eventSourceClient, uri);
    }

    public EventSource(Executor eventExecutor, long reconnectionTimeMillis, URI uri, EventSourceHandler eventSourceHandler) {
        this(new EventSourceClient(eventExecutor), reconnectionTimeMillis, uri, eventSourceHandler);
    }

    public EventSource(String uri, EventSourceHandler eventSourceHandler) {
        this(URI.create(uri), eventSourceHandler);
    }

    public EventSource(URI uri, EventSourceHandler eventSourceHandler) {
        this(new EventSourceClient(), DEFAULT_RECONNECTION_TIME_MILLIS, uri, eventSourceHandler);
    }

    public ChannelFuture connect() {
        return clientHandler.connect();
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
