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

package org.apache.ignite.tcignited.history;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistKey;

/**
 * In memory replacement of invocation history (RunHist/RunStat).
 */
public class RunHistCompacted extends AbstractRunHist {
    /** Data. */
    private InvocationData data = new InvocationData();

    public RunHistCompacted() {
    }

    public RunHistCompacted(RunHistKey ignored) {

    }

    /** {@inheritDoc} */
    @Nullable
    @Override public List<Integer> getLatestRunResults() {
        return data.getLatestRuns();
    }

    /** {@inheritDoc} */
    @Override public Stream<Invocation> getInvocations() {
        return data.invocations();
    }

    /** {@inheritDoc} */
    @Override public Iterable<Invocation> invocations() {
        return data.invocationsIterable();
    }

    public Set<Integer> buildIds() {
        return data.buildIdsMapping().keySet();
    }

    public Map<Integer, Integer> buildIdsMapping() {
        return data.buildIdsMapping();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        Stream<CharSequence> stream = data.getLatestRuns().stream().filter(Objects::nonNull).map(Object::toString);
        String join = String.join("", stream::iterator);

        return MoreObjects.toStringHelper(this)
            .add("runs", join)
            .add("failRate", getFailPercentPrintable())
            .add("data", data)
            .toString();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RunHistCompacted compacted = (RunHistCompacted)o;
        return Objects.equals(data, compacted.data);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(data);
    }

    /**
     * @param v Invocation.
     */
    public void addInvocation(Invocation v) {
        data.add(v);
    }

    public void sort() {
        data.sort();
    }

    public RunHistCompacted filterSuiteInvByParms(Map<Integer, Integer> requireParameters) {
        RunHistCompacted cp = new RunHistCompacted();

        getInvocations()
            .filter(invocation -> invocation.containsParameterValue(requireParameters))
            .forEach(invocation -> cp.data.add(invocation));

        return cp;
    }

    /**
     * @param idx Index.
     */
    public Invocation getInvocationAt(int idx) {
        return data.getInvocationAt(idx);
    }
}
