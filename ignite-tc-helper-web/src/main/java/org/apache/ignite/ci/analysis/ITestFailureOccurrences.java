package org.apache.ignite.ci.analysis;

import java.util.stream.Stream;

public interface ITestFailureOccurrences {
    String getName();

    boolean isInvestigated();

    Stream<String> getOccurrenceIds();

    public int occurrencesCount();

    public int failuresCount();
}
