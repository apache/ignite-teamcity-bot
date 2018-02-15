package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;

public class MultTestFailureOccurrences implements ITestFailureOccurrences {
    List<TestOccurrence> occurrences = new ArrayList<>();

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

    private long getFailedButNotMutedCount() {
        return occurrences.stream().filter(TestOccurrence::isFailedButNotMuted).count();
    }

}
