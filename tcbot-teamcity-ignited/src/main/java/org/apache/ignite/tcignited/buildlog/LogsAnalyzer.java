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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.concurrent.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use one instance per one file, class is stateful.
 */
@NotThreadSafe
public class LogsAnalyzer implements Function<File, File> {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(LogsAnalyzer.class);

    /** Line handlers list. */
    private final List<ILineHandler> lineHandlersList;

    /**
     * @param lineHandlers Line handlers.
     */
    public LogsAnalyzer(ILineHandler... lineHandlers) {
        lineHandlersList = Arrays.asList(lineHandlers);
    }

    /** {@inheritDoc} */
    @Override public File apply(File file) {
        try (Stream<String> lines = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
            lines.forEach(line -> lineHandlersList.forEach(h -> h.accept(line, file)));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        finally {
            lineHandlersList.forEach(this::closeSilent);
        }
        return file;
    }

    private void closeSilent(ILineHandler hnd) {
        try {
            hnd.close();
        }
        catch (Exception e) {
            e.printStackTrace();
            logger.error("Problem with line handler release: " + e.getMessage(), e);
        }
    }

}
