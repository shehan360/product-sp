/*
 * Copyright (c) 2016, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.streamprocessor.core;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.deployment.engine.Artifact;
import org.wso2.carbon.deployment.engine.ArtifactType;
import org.wso2.carbon.deployment.engine.Deployer;
import org.wso2.carbon.deployment.engine.exception.CarbonDeploymentException;
import org.wso2.streamprocessor.core.internal.Constants;
import org.wso2.streamprocessor.core.internal.StreamProcessorDataHolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * {@code StreamProcessorDeployer} is responsible for all siddhiql file deployment tasks
 *
 * @since 1.0.0
 */


public class StreamProcessorDeployer implements Deployer {

    public static final String SIDDHIQL_FILES_DIRECTORY = "siddhiql-files";
    private static final Logger log = LoggerFactory.getLogger(StreamProcessorDeployer.class);
    private static final String FILE_EXTENSION = ".siddhiql";
    private ArtifactType artifactType = new ArtifactType<>("siddhiql");
    private URL directoryLocation;

    public static int deploySiddhiQLFile(File file) {
        InputStream inputStream = null;
        log.info("Deploy");

        try {
            inputStream = new FileInputStream(file);
            if (file.getName().endsWith(FILE_EXTENSION)) {
                String executionPlan = getStringFromInputStream(inputStream);
                StreamProcessorDataHolder.getStreamProcessorService().deployExecutionPlan(executionPlan);
            } else {
                if (Constants.RuntimeMode.RUN_FILE == StreamProcessorDataHolder.getInstance().getRuntimeMode()) {
                    log.error("Error: File extension not supported. Supported extensions {}.", FILE_EXTENSION);
                    StreamProcessorDataHolder.getInstance().setRuntimeMode(Constants.RuntimeMode.ERROR);
                }
                log.error("Error: File extension not supported. Support only {}.", FILE_EXTENSION);
                return 0;
            }
        } catch (Exception e) {
            log.error("Error while deploying SiddhiQL", e);
        }

        return 0;
    }

    public static void deploySiddhiQLFiles(File file) {
        if (file == null || !file.exists() || !file.isDirectory()) {
            // Can't continue as there is no directory to work with. if we get here, that means a bug in startup
            // script.
            log.error("Given working path {} is not a valid location. ", file == null ? null : file.getName());
        }
    }

    /**
     * Undeploy a service registered through a SiddhiQL file.
     *
     * @param fileName Name of the SiddhiQL file
     */
    private void undeploySiddhiQLFile(String fileName) {

    }



    @Activate
    protected void activate(BundleContext bundleContext) {
        // Nothing to do.
    }

    @Override
    public void init() {
        try {
            directoryLocation = new URL("file:" + SIDDHIQL_FILES_DIRECTORY);
        } catch (MalformedURLException e) {
            log.error("Error while initializing directoryLocation" + SIDDHIQL_FILES_DIRECTORY, e);
        }
    }

    @Override
    public Object deploy(Artifact artifact) throws CarbonDeploymentException {

        deploySiddhiQLFile(artifact.getFile());
        return artifact.getFile().getName();
    }

    @Override
    public void undeploy(Object key) throws CarbonDeploymentException {
        undeploySiddhiQLFile((String) key);
    }

    @Override
    public Object update(Artifact artifact) throws CarbonDeploymentException {

        log.info("Updating " + artifact.getName() + "...");
        undeploySiddhiQLFile(artifact.getName());
        deploySiddhiQLFile(artifact.getFile());
        return artifact.getName();
    }

    @Override
    public URL getLocation() {
        return directoryLocation;
    }

    @Override
    public ArtifactType getArtifactType() {
        return artifactType;
    }


    private static String getStringFromInputStream(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();

    }


}