package com.github.eventsource.client.impl;

public interface ConnectionHandler {
    void setReconnectionTime(long reconnectionTimeMillis);
    void setLastEventId(String lastEventId);
}
