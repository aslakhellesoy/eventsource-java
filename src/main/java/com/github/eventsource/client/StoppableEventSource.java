package com.github.eventsource.client;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StoppableEventSource extends EventSource {
    static final String TAG = "StoppableEventSource";

    private final ExecutorService executorService;

//    private StoppableEventSource(Executor executor, long reconnectionTimeMillis, final URI uri, final SSLEngineProvider sslEngineProvider, EventSourceHandler eventSourceHandler) {
//        super(executor, reconnectionTimeMillis, uri, sslEngineProvider, eventSourceHandler);
//    }
//
//    private StoppableEventSource(Executor executor, long reconnectionTimeMillis, final URI uri, EventSourceHandler eventSourceHandler) {
//        this(executor, reconnectionTimeMillis, uri, null, eventSourceHandler);
//    }

    private StoppableEventSource(ExecutorService executorService, long reconnectionTimeMillis, final URI uri, final SSLEngineProvider sslEngineProvider, EventSourceHandler eventSourceHandler) {
        super(executorService, reconnectionTimeMillis, uri, sslEngineProvider, eventSourceHandler);
        this.executorService = executorService;
    }

    public StoppableEventSource(URI uri, SSLEngineProvider sslEngineProvider, EventSourceHandler eventSourceHandler) {
        this(Executors.newSingleThreadExecutor(), DEFAULT_RECONNECTION_TIME_MILLIS, uri, sslEngineProvider, eventSourceHandler);
    }

    public StoppableEventSource(String uri, SSLEngineProvider sslEngineProvider, EventSourceHandler eventSourceHandler) {
        this(URI.create(uri), sslEngineProvider, eventSourceHandler);
    }

    public StoppableEventSource(String uri, EventSourceHandler eventSourceHandler) {
        this(uri, null, eventSourceHandler);
    }

    public StoppableEventSource(URI uri, EventSourceHandler eventSourceHandler) {
        this(uri, null, eventSourceHandler);
    }

    @Override
    protected void finalize() {
        System.out.println("finalize");
    }

    public void shutdownNow() {
        shutdownAndAwaitTermination(1, TimeUnit.SECONDS);
    }

    public void shutdownAndAwaitTermination(final long timeout, final TimeUnit unit) {
        close();
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(timeout, unit)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(timeout, unit)) {
                    System.err.println("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    public boolean isTerminated() {
        return executorService.isTerminated();
    }
}
