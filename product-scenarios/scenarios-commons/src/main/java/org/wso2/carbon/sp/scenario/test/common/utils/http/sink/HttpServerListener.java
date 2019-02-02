package org.wso2.carbon.sp.scenario.test.common.utils.http.sink;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HttpServerListener implements HttpHandler {
    private AtomicBoolean isEventArrived = new AtomicBoolean(false);
    private List data;
    private Headers headers;
    private static final Logger logger = Logger.getLogger(HttpServerListener.class);

    public HttpServerListener() {
        this.data = new ArrayList();
    }

    @Override
    public void handle(HttpExchange event) throws IOException {
        //Get the paramString form the request
        String line;
        headers = event.getRequestHeaders();
        InputStream inputStream = event.getRequestBody();
        // initiating
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        line = bufferedReader.readLine();
        this.data.add(line);
        byte[] response = line.getBytes();
        event.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
        event.getResponseBody().write(response);
        event.close();
        isEventArrived.set(true);
    }

    public List getData() {
        isEventArrived = new AtomicBoolean(false);
        return data;
    }

    public Headers getHeaders() {
        return headers;
    }

    public boolean isMessageArrive() {
        return isEventArrived.get();
    }

}
