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

package org.apache.ignite.tcignited.buildlog;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

/**
 * Use one instance per one stream, class is statefull and not thread safe
 */
class BuildLogStreamChecker {
    private final List<ILineHandler> lineHandlersList;

    BuildLogStreamChecker(ILineHandler... lineHandlers) {
        lineHandlersList = Arrays.asList(lineHandlers);
    }

    void apply(ZipInputStream zipInputStream, File zipFile) {
        Reader reader = new InputStreamReader(zipInputStream, StandardCharsets.UTF_8);

        try (Stream<String> lines = new BufferedReader(reader).lines()) {
            lines.forEach(line -> lineHandlersList.forEach(h -> h.accept(line, zipFile)));
        }
        finally {
            lineHandlersList.forEach(this::closeSilent);
        }
    }

    private void closeSilent(ILineHandler handler) {
        try {
            handler.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
