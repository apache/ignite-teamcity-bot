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

package org.apache.ignite.ci.analysis;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;

public class MultTestFailureOccurrences implements ITestFailures {
    private final List<TestOccurrenceFull> occurrences = new CopyOnWriteArrayList<>();

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

    public long getAvgDurationMs() {
        //todo avg
        Stream<Long> stream = occurrences.stream().map(TestOccurrence::getDuration);
        return stream.findAny().orElse(0L);
    }

    @Override public Iterable<TestOccurrenceFull> getOccurrences() {
        return occurrences;
    }

    public void add(TestOccurrenceFull next) {
        if (next.getId() == null)
            return;

        occurrences.add(next);
    }
}
