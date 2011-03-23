package com.github.eventsource.client;

public interface EventSourceClientHandler {
    void onConnect();

    void onMessage(String event, MessageEvent message);

    void onDisconnect();

    void onError(Throwable t);
}
