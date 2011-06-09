package com.github.eventsource.client;

import org.webbitserver.*;
import org.webbitserver.EventSourceHandler;
import org.webbitserver.netty.contrib.EventSourceMessage;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static java.lang.Thread.sleep;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.webbitserver.WebServers.createWebServer;

/**
 * A simple server that burts out the numbers 1-10 upon connection.
 */
public class TimeServer {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        ExecutorService webThread = newSingleThreadExecutor();
        TimeHandler timeHandler = new TimeHandler(webThread);
        createWebServer(webThread, 8090)
                .add("/", new HtmlHandler())
                .add("/es", timeHandler)
                .start();
        System.out.println("Server running on http://localhost:8090");
        timeHandler.start();
    }

    private static class TimeHandler implements EventSourceHandler {
        private Set<EventSourceConnection> connections = new HashSet<EventSourceConnection>();
        private final ExecutorService webThread;

        public TimeHandler(ExecutorService webThread) {
            this.webThread = webThread;
        }

        private void broadcast(EventSourceMessage message) {
            for (EventSourceConnection connection : connections) {
                System.out.println(message.build());
                connection.send(message);
            }
        }

        @Override
        public void onOpen(EventSourceConnection connection) throws Exception {
            System.out.println("OPEN - HEADERS = " + connection.httpRequest().allHeaders());
            connections.add(connection);
        }

        @Override
        public void onClose(EventSourceConnection connection) throws Exception {
            System.out.println("DISCONNECTED");
            connections.remove(connection);
        }

        public void start() throws InterruptedException, ExecutionException {
            while (true) {
                sleep(1000);
                webThread.submit(new Runnable() {
                    @Override
                    public void run() {
                        Date date = new Date();
                        EventSourceMessage message = new EventSourceMessage()
                                .data(date.toString())
                                .id(date.getTime())
// Chrome/FF doesn't fire events if the event field is set (?)
//                                .event("event-" + date.getTime())
                                ;
                        broadcast(message);
                    }
                }).get();
            }
        }
    }

    private static class HtmlHandler implements HttpHandler {
        @Override
        public void handleHttpRequest(HttpRequest request, HttpResponse response, HttpControl control) throws Exception {
            response
                    .header("Content-Type", "text/html")
                    .charset(Charset.forName("UTF-8"))
                    .content("" +
                            "<!DOCTYPE html>\n" +
                            "<html>\n" +
                            "  <head>\n" +
                            "    <script>\n" +
                            "      function logText(msg) {\n" +
                            "        var textArea = document.getElementById('log');\n" +
                            "        textArea.value = textArea.value + msg + '\\n';\n" +
                            "        textArea.scrollTop = textArea.scrollHeight; // scroll into view\n" +
                            "      }\n" +
                            "\n" +
                            "      window.onload = function() {\n" +
                            "        var es = new EventSource(document.location + 'es');\n" +
                            "        es.onopen = function() {\n" +
                            "          logText('OPEN');\n" +
                            "        };\n" +
                            "        es.onmessage = function(e) {\n" +
                            "          logText('MESSAGE:' + e.data);\n" +
                            "        };\n" +
                            "        es.onerror = function(e) {\n" +
                            "          logText('ERROR');\n" +
                            "        };\n" +
                            "      };\n" +
                            "    </script>\n" +
                            "  </head>\n" +
                            "  <body>\n" +
                            "    <textarea id=\"log\" rows=\"40\" cols=\"70\"></textarea>\n" +
                            "  </body>\n" +
                            "</html>"
                    ).end();
        }
    }
}
