package com.github.eventsource.client;

public interface EventSourceHandler {
    void onConnect() throws Exception;
    void onMessage(String event, MessageEvent message) throws Exception;
    void onError(Throwable t);
}
