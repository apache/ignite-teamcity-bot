package org.apache.ignite.ci;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by Дмитрий on 21.07.2017
 *
 * Use one instance per one file, class is statefull and not thread safe
 */
public class ThreadDumpSearch implements Function<File, File> {

    private boolean inThreadDump;

    @Override public File apply(File file) {
        try {
            Stream<String> lines = Files.lines(file.toPath(), StandardCharsets.UTF_8);
            lines.forEach(line -> {
                if (line.contains("Full thread dump ")) {
                    inThreadDump = true;
                }
                if (inThreadDump && line.startsWith("[")) {
                    inThreadDump = false;
                }
                if (inThreadDump) {
                    System.err.println(line);
                }
            });
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }
}
