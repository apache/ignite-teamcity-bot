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

package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcservice.model.result.ProgressInfo;

/** Compacted TeamCity running build progress. */
@Persisted
public class RunningInfoCompacted {
    /** */
    private int percentageComplete = -1;

    /** */
    private long elapsedSeconds = -1;

    /** */
    private long estimatedTotalSeconds = -1;

    /** */
    private long leftSeconds = -1;

    /** */
    private int currentStageText = -1;

    /** */
    @Nullable private Boolean outdated;

    /** */
    @Nullable private Boolean probablyHanging;

    /** */
    private int lastActivityTime = -1;

    /** */
    public RunningInfoCompacted() {
    }

    /**
     * @param compactor String compactor.
     * @param info Running info.
     */
    public RunningInfoCompacted(IStringCompactor compactor, ProgressInfo info) {
        percentageComplete = info.percentageComplete == null ? -1 : info.percentageComplete;
        elapsedSeconds = info.elapsedSeconds == null ? -1 : info.elapsedSeconds;
        estimatedTotalSeconds = info.estimatedTotalSeconds == null ? -1 : info.estimatedTotalSeconds;
        leftSeconds = info.leftSeconds == null ? -1 : info.leftSeconds;
        currentStageText = info.currentStageText == null ? -1 : compactor.getStringId(info.currentStageText);
        outdated = info.outdated;
        probablyHanging = info.probablyHanging;
        lastActivityTime = info.lastActivityTime == null ? -1 : compactor.getStringId(info.lastActivityTime);
    }

    /** */
    @Nullable public Integer percentageComplete() {
        return percentageComplete < 0 ? null : percentageComplete;
    }

    /** */
    @Nullable public Long elapsedSeconds() {
        return elapsedSeconds < 0 ? null : elapsedSeconds;
    }

    /** */
    @Nullable public Long estimatedTotalSeconds() {
        return estimatedTotalSeconds < 0 ? null : estimatedTotalSeconds;
    }

    /** */
    @Nullable public Long leftSeconds() {
        return leftSeconds < 0 ? null : leftSeconds;
    }

    /**
     * @param compactor String compactor.
     */
    @Nullable public String currentStageText(IStringCompactor compactor) {
        return currentStageText < 0 ? null : compactor.getStringFromId(currentStageText);
    }

    /** */
    @Nullable public Boolean probablyHanging() {
        return probablyHanging;
    }

    /**
     * @param compactor String compactor.
     */
    public ProgressInfo toProgressInfo(IStringCompactor compactor) {
        ProgressInfo res = new ProgressInfo();

        res.percentageComplete = percentageComplete();
        res.elapsedSeconds = elapsedSeconds();
        res.estimatedTotalSeconds = estimatedTotalSeconds();
        res.leftSeconds = leftSeconds();
        res.currentStageText = currentStageText(compactor);
        res.outdated = outdated;
        res.probablyHanging = probablyHanging;
        res.lastActivityTime = lastActivityTime < 0 ? null : compactor.getStringFromId(lastActivityTime);

        return res;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RunningInfoCompacted that = (RunningInfoCompacted)o;

        return percentageComplete == that.percentageComplete &&
            elapsedSeconds == that.elapsedSeconds &&
            estimatedTotalSeconds == that.estimatedTotalSeconds &&
            leftSeconds == that.leftSeconds &&
            currentStageText == that.currentStageText &&
            lastActivityTime == that.lastActivityTime &&
            Objects.equal(outdated, that.outdated) &&
            Objects.equal(probablyHanging, that.probablyHanging);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(percentageComplete, elapsedSeconds, estimatedTotalSeconds, leftSeconds,
            currentStageText, outdated, probablyHanging, lastActivityTime);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("percentageComplete", percentageComplete)
            .add("elapsedSeconds", elapsedSeconds)
            .add("estimatedTotalSeconds", estimatedTotalSeconds)
            .add("leftSeconds", leftSeconds)
            .add("currentStageText", currentStageText)
            .add("outdated", outdated)
            .add("probablyHanging", probablyHanging)
            .add("lastActivityTime", lastActivityTime)
            .toString();
    }
}
