package com.github.eventsource.client;

import java.net.URI;

public class BurstClient {
    public static void main(String[] args) throws InterruptedException {
        EventSource es = new EventSource(URI.create("http://localhost:8090/es"), new EventSourceHandler() {
            @Override
            public void onConnect() {
                System.out.println("CONNECTED");
            }

            @Override
            public void onMessage(String event, MessageEvent message) {
                System.out.println("message = " + message.data);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("ON ERROR");
                t.printStackTrace();
            }
        });
        es.connect();
        es.close().join();
    }
}
