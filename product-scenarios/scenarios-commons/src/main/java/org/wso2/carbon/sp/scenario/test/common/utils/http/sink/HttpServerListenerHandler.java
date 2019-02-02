package org.wso2.carbon.sp.scenario.test.common.utils.http.sink;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

public class HttpServerListenerHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(HttpServerListenerHandler.class);
    private HttpServerListener serverListener;
    private HttpServer server;
    private int port;

    public HttpServerListenerHandler(int port) {
        this.serverListener = new HttpServerListener();
        this.port = port;
    }

    @Override
    public void run() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 5);
            server.createContext("/abc", serverListener);
            server.start();
        } catch (IOException e) {
            logger.error("Error in creating test server.", e);
        }
    }

    public void shutdown() {
        if (server != null) {
            logger.info("Shutting down");
            server.stop(1);
        }
    }

    public HttpServerListener getServerListener() {
        return serverListener;
    }
}
