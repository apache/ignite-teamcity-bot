package org.apache.ignite.ci.logs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by dpavlov on 24.07.2017.
 *
 * Use one instance per one file, class is statefull and not thread safe
 */
public class LogsAnalyzer implements Function<File, File> {

    private final List<ILineHandler> lineHandlersList;

    public LogsAnalyzer(ILineHandler... lineHandlers) {
        lineHandlersList = Arrays.asList(lineHandlers);
    }

    @Override public File apply(File file) {
        try (Stream<String> lines = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
            lines.forEach(line -> lineHandlersList.forEach(h -> h.accept(line, file)));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lineHandlersList.forEach(this::closeSilent);
        }
        return file;
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
