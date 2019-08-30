/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * TC Helper Config access, tracked branches, etc stuff.
 */
public class HelperConfig {
    public static final String CONFIG_FILE_NAME = "auth.properties";


    public static Properties loadAuthProperties(File workDir, String cfgFileName) {
        try {
            return loadAuthPropertiesX(workDir, cfgFileName);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties loadAuthPropertiesX(File workDir, String cfgFileName) throws IOException {
        File file = new File(workDir, cfgFileName);

        Preconditions.checkState(file.exists(),
            String.format("Config file %s is not found in work directory %s", cfgFileName, workDir.getAbsolutePath()));

        return loadProps(file);
    }

    private static Properties loadProps(File file) throws IOException {
        Properties props = new Properties();

        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
        }

        return props;
    }

    public static String prepareConfigName(String tcName) {
        return prefixedWithServerName(tcName, CONFIG_FILE_NAME);
    }

    private static String prefixedWithServerName(@Nullable String tcName, String name) {
        return isNullOrEmpty(tcName) ? name : (tcName + "." + name);
    }

}
