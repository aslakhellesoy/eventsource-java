package com.github.eventsource.client.impl;

import com.github.eventsource.client.EventSourceHandler;
import com.github.eventsource.client.MessageEvent;

import java.util.concurrent.Executor;

public class AsyncEventSourceHandler implements EventSourceHandler {
    private final Executor executor;
    private final EventSourceHandler eventSourceHandler;

    public AsyncEventSourceHandler(Executor executor, EventSourceHandler eventSourceHandler) {
        this.executor = executor;
        this.eventSourceHandler = eventSourceHandler;
    }

    @Override
    public void onConnect() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    eventSourceHandler.onConnect();
                } catch (Exception e) {
                    onError(e);
                }
            }
        });
    }

    @Override
    public void onMessage(final String event, final MessageEvent message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    eventSourceHandler.onMessage(event, message);
                } catch (Exception e) {
                    onError(e);
                }
            }
        });
    }

    @Override
    public void onError(final Throwable error) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    eventSourceHandler.onError(error);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onClosed(final boolean willReconnect) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    eventSourceHandler.onClosed(willReconnect);
                } catch (Exception e) {
                    onError(e);
                }
            }
        });
    }
}
