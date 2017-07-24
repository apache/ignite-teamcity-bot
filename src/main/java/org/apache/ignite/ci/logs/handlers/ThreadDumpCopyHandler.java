package org.apache.ignite.ci.logs.handlers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.ignite.ci.logs.ILineHandler;

;

/**
 * Created by Дмитрий on 21.07.2017
 *
 * Use one instance per one file, class is statefull and not thread safe
 */
public class ThreadDumpCopyHandler implements ILineHandler {
    private static final String ENDL = String.format("%n");
    private FileWriter currentThDump;
    private int fileIdx = 0;

    @Override public void accept(String line, File fromLogFile) {
        try {
            acceptX(line, fromLogFile);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void acceptX(String line, File fromLogFile) throws IOException {
        if (currentThDump == null && line.contains("Full thread dump ")) {
            currentThDump = new FileWriter(new File(fromLogFile.getParentFile(), "ThreadDump" + fileIdx + ".log"));
            fileIdx++;
        }

        if (line.startsWith("["))
            closeCurrentIfNeed();

        if (currentThDump != null) {
            currentThDump.write(line);
            currentThDump.write(ENDL);
        }

    }

    private void closeCurrentIfNeed() throws IOException {
        if (currentThDump != null) {
            currentThDump.close();
            currentThDump = null;
        }
    }

    @Override public void close() throws Exception {
        closeCurrentIfNeed();
    }
}
