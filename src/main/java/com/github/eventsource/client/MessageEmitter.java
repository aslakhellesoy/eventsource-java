package com.github.eventsource.client;

interface MessageEmitter {
    void emitMessage(String event, final MessageEvent message);

    void setReconnectionTime(long reconnectionTimeMillis);
}
