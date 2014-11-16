package com.github.eventsource.client;

import javax.net.ssl.SSLEngine;

public interface SSLEngineProvider {
    SSLEngine createSSLEngine();
}