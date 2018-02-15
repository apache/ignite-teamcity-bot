package org.apache.ignite.ci.analysis;

import java.util.stream.Stream;

public interface ITestFailureOccurrences {
    String getName();

    boolean isInvestigated();

    default int occurrencesCount() {
       return (int)getOccurrenceIds().count();
    }

    Stream<String> getOccurrenceIds();
}
