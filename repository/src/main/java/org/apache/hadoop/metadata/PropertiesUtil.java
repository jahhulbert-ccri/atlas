/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.metadata;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class PropertiesUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PropertiesUtil.class);

    private static final String APPLICATION_PROPERTIES = "application.properties";

    public static final PropertiesConfiguration getApplicationProperties() throws MetadataException {
        String confLocation = System.getProperty("metadata.conf");
        URL url;
        try {
            if (confLocation == null) {
                url = PropertiesUtil.class.getResource("/" + APPLICATION_PROPERTIES);
            } else {
                url = new File(confLocation, APPLICATION_PROPERTIES).toURI().toURL();
            }
            LOG.info("Loading {} from {}", APPLICATION_PROPERTIES, url);
            return new PropertiesConfiguration(url);
        } catch (Exception e) {
            throw new MetadataException("Failed to load application properties", e);
        }
    }
}