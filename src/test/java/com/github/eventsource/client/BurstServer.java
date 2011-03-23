package com.github.eventsource.client;

import org.webbitserver.EventSourceConnection;
import org.webbitserver.EventSourceHandler;
import org.webbitserver.HttpControl;
import org.webbitserver.HttpHandler;
import org.webbitserver.HttpRequest;
import org.webbitserver.HttpResponse;
import org.webbitserver.netty.contrib.EventSourceMessage;

import java.io.IOException;
import java.nio.charset.Charset;

import static java.lang.Thread.sleep;
import static org.webbitserver.WebServers.createWebServer;

/**
 * A simple server that burts out the numbers 1-10 upon connection.
 */
public class BurstServer {
    public static void main(String[] args) throws IOException {
        createWebServer(8090)
                .add("/", new HtmlHandler())
                .add("/es", new BurstHandler())
                .start();
    }

    private static class BurstHandler implements EventSourceHandler {
        @Override
        public void onOpen(EventSourceConnection connection) throws Exception {
            System.out.println("connection = " + connection);
            sleep(200);
            for (int i = 0; i < 10; i++) {
                String event = new EventSourceMessage().data(String.valueOf(i)).end().toString();
                connection.send(event);
            }
            //connection.close();
        }

        @Override
        public void onClose(EventSourceConnection connection) throws Exception {
            System.out.println("DISCONNECTED");
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
                            "<html>" +
                            "  <head>" +
                            "    <script>\n" +
                            "      window.onload = function() {\n" +
                            "        console.log('HELLO');\n" +
                            "        var es = new EventSource('/es');\n" +
                            "        es.onmessage = function(e) {\n" +
                            "          console.log(e.data);\n" +
                            "        };\n" +
                            "      };\n" +
                            "    </script>\n" +
                            "  </head>\n" +
                            "  <body>\n" +
                            "    Check the firebug console.\n" +
                            "  </body>\n" +
                            "</html>"
                    ).end();
        }
    }
}
