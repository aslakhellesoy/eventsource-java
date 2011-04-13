package com.github.eventsource.client;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

public class DebugClient {
    public static void main(String[] args) throws InterruptedException {
        EventSource es = new EventSource(URI.create("http://localhost:8090/es"), new EventSourceHandler() {
            @Override
            public void onConnect() {
                System.out.println("CONNECTED");
            }

            @Override
            public void onMessage(String event, MessageEvent message) {
                System.out.println("event = " + event + ", message = " + message);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("ERROR");
                t.printStackTrace();
            }
        });

        es.connect();
        new CountDownLatch(1).await();
    }
}
