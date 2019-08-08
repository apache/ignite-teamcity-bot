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

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.tcbot.common.TcBotConst;

/**
 * Abstract in memory summary of suite or test execution history
 */
public abstract class AbstractRunHist implements IRunHistory {

    public abstract Iterable<Invocation> invocations();


    public Stream<Invocation> getInvocations() {
        return StreamSupport.stream(invocations().spliterator(), false);
    }

    /** {@inheritDoc} */
    @Override public boolean isFlaky() {
        return getStatusChangesWithoutCodeModification() >= TcBotConst.FLAKYNESS_STATUS_CHANGE_BORDER;
    }

    /** {@inheritDoc} */
    @Override public final String getFlakyComments() {
        int statusChange = getStatusChangesWithoutCodeModification();

        if (statusChange < TcBotConst.FLAKYNESS_STATUS_CHANGE_BORDER)
            return null;

        return "Test seems to be flaky: " +
            "changed its status [" + statusChange + "/" + getInvocations().count() + "] without code modifications";
    }

    public int getStatusChangesWithoutCodeModification() {
        int statusChange = 0;

        Invocation prev = null;

        for (Invocation cur : invocations()) {
            if (cur == null)
                continue;

            if (cur.status() == InvocationData.MISSING)
                continue;

            //todo here all previous MISSING invocations status could be checked
            // (using outside build history)
            if (prev != null) {
                if (prev.status() != cur.status()
                    && cur.changesState() == ChangesState.NONE
                    && prev.changesState() != ChangesState.UNKNOWN)
                    statusChange++;
            }

            prev = cur;
        }
        return statusChange;
    }

}
