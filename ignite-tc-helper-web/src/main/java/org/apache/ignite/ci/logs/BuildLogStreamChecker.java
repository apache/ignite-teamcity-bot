package org.apache.ignite.ci.logs;

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
public class BuildLogStreamChecker {

    private final List<ILineHandler> lineHandlersList;

    public BuildLogStreamChecker(ILineHandler... lineHandlers) {
        lineHandlersList = Arrays.asList(lineHandlers);
    }

    public void apply(ZipInputStream zipInputStream, File zipFile) {
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
