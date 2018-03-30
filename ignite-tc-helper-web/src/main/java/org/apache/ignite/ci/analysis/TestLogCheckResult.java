package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.apache.ignite.ci.db.Persisted;

@Persisted
public class TestLogCheckResult {
    @Nullable List<String> warns;

    int cntLines = 0;
    int cntBytes = 0;

    public void addWarning(String line) {
        if (warns == null)
            warns = new ArrayList<>();

        warns.add(line);
    }

    @NotNull public List<String> getWarns() {
        return warns == null ? Collections.emptyList() : warns;
    }

    public boolean hasWarns() {
        return warns != null && !warns.isEmpty();
    }

    public void addLineStat(String line) {
        int i = line.length() + 1; //here suppose UTF-8, 1 byte per char; 1 newline char
        cntLines++;
        cntBytes += i;
    }

    public int getLogSizeBytes() {
        return cntBytes;
    }
}
