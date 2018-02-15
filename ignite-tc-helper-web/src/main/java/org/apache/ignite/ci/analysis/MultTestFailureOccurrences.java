package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;

public class MultTestFailureOccurrences implements ITestFailureOccurrences {
    private final List<TestOccurrence> occurrences = new CopyOnWriteArrayList<>();

    public MultTestFailureOccurrences() {

    }

    @Override public String getName() {
        return occurrences.isEmpty() ? "" : occurrences.iterator().next().name;
    }

    @Override public boolean isInvestigated() {
        return occurrences.stream().anyMatch(TestOccurrence::isInvestigated);
    }

    @Override public Stream<String> getOccurrenceIds() {
        return occurrences.stream().map(TestOccurrence::getId);
    }

    public boolean hasFailedButNotMuted() {
        return getFailedButNotMutedCount() > 0;
    }

    private int getFailedButNotMutedCount() {
        return (int)occurrences.stream()
            .filter(Objects::nonNull)
            .filter(TestOccurrence::isFailedButNotMuted).count();
    }

    public int occurrencesCount() {
        return (int)getOccurrenceIds().count();
    }

    @Override public int failuresCount() {
        return getFailedButNotMutedCount();
    }

    public void add(TestOccurrence next) {
        if (next.id == null)
            return;

        occurrences.add(next);
    }
}
