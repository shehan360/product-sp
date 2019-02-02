/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.sp.scenario;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.carbon.sp.scenario.test.common.utils.http.sink.HttpServerListenerHandler;
import org.wso2.carbon.sp.scenario.test.common.utils.http.source.HttpTestUtil;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ReceiveAPostStream {
    public static final String WORKER_IP = "localhost";
    public static final int WORKER_PORT = 9443;

    public static final String HTTP_SOURCE_IP = "localhost";
    public static final int HTTP_SOURCE_PORT = 8280;

    public static final String HTTP_SINK_IP = "localhost";
    public static final int HTTP_SINK_PORT = 8005;

    @Test
    public void receiveAPostStream() throws Exception {

        URI workerBaseURI = URI.create(String.format("https://%s:%d", WORKER_IP, WORKER_PORT));
        String path = "/siddhi-apps";
        String contentType = "text/plain";
        String method = "POST";
        String inStreamDefinition = "@App:name('TestSiddhiApp')" +
                "@source(type='http', @map(type='xml') )" +
                "define stream inputStream (name string, amount double, timestamp long);";

        String outStreamDefinition = String.format("@sink(type='http',publisher.url='http://%s:%d/abc',method='POST'," +
                "headers='Content-Type:xml',@map(type='xml', @payload('<name>{{name}}</name><amount>{{amount}}rs" +
                "</amount>'))) define stream outputStream (name string, amount double);", HTTP_SINK_IP, HTTP_SINK_PORT);

        String query = ("@info(name = 'query') "
                + "from inputStream#log('AA') "
                + "select name, amount "
                + "insert into outputStream;"
        );

        HttpTestUtil util = new HttpTestUtil();
        int response = util.httpsRequest(inStreamDefinition + outStreamDefinition + query, workerBaseURI, path,
                true, false, method, contentType, "admin", "admin");
        Assert.assertEquals(response, 201, "Failed to deploy Siddhi App");
        Thread.sleep(5000);
        URI sourceURI = URI.create(String.format("http://%s:%d", HTTP_SOURCE_IP, HTTP_SOURCE_PORT));

        List<String> expected = new ArrayList<>(2);

        Random rand = new Random();
        HttpServerListenerHandler lst = new HttpServerListenerHandler(HTTP_SINK_PORT);
        lst.run();

        for (int i = 0; i < 20; i++) {
            String name = "John" + rand.nextInt(10);
            int amount = rand.nextInt(99);

            String event = "<events>"
                    + "<event>"
                    + "<name>" + name + "</name>"
                    + "<amount>" + amount + "</amount>"
                    + "<timestamp>" + System.currentTimeMillis() + "</timestamp>"
                    + "</event>"
                    + "</events>";
            util.httpRequest(event, sourceURI, "/TestSiddhiApp/inputStream", false, true,
                    "POST", "text/xml", "", "");
            String expectedEvent = "<name>" + name + "</name>"
                    + "<amount>" + amount + "rs</amount>";
            expected.add(expectedEvent);
            Thread.sleep(20);
        }
        Thread.sleep(100);
        int responseDel = util.httpsRequest(null, workerBaseURI, path + "/TestSiddhiApp", true,
                false, "DELETE", "text/plain", "admin", "admin");
        Assert.assertEquals(responseDel, 200, "Failed to delete Siddhi App");
        Assert.assertEquals(lst.getServerListener().getData(), expected, "Expected data not matched!");

    }

}
