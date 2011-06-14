package com.github.eventsource.client.impl;

public interface ConnectionHandler {
    void setReconnectionTimeMillis(long reconnectionTimeMillis);
    void setLastEventId(String lastEventId);
}
