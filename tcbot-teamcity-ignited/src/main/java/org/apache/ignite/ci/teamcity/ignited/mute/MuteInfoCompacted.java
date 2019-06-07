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

package org.apache.ignite.ci.teamcity.ignited.mute;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;

import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcservice.model.mute.MuteAssignment;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.model.mute.MuteTarget;
import org.apache.ignite.tcservice.model.result.tests.TestRef;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.common.util.TimeUtil;
import org.apache.ignite.internal.util.typedef.F;

/**
 * @see MuteInfo
 */
@Persisted
public class MuteInfoCompacted {
    /** Mute id. */
    int id;

    /** Assignment. Mute date. */
    long muteDate;

    /** Assignment. Text. */
    int text;

    /** Scope. */
    MuteScopeCompacted scope;

    /** Target. Test ids. */
    long[] testIds;

    /** Target. Test names. */
    int[] testNames;

    /**
     * @param mute Mute to compact.
     * @param comp Compactor.
     */
    public MuteInfoCompacted(MuteInfo mute, IStringCompactor comp) {
        id = mute.id;

        muteDate = TimeUtil.tcSimpleDateToTimestamp(mute.assignment.muteDate);
        text = comp.getStringId(mute.assignment.text);

        scope = new MuteScopeCompacted(mute.scope, comp);

        List<TestRef> tests = mute.target.tests;

        if (F.isEmpty(tests))
            return;

        testIds = new long[tests.size()];
        testNames = new int[tests.size()];

        for (int i = 0; i < tests.size(); i++) {
            TestRef test = tests.get(i);

            testIds[i] = Long.valueOf(test.id);
            testNames[i] = comp.getStringId(test.name);
        }
    }

    /**
     * @param comp Compactor.
     * @return Extracted mute.
     */
    public MuteInfo toMuteInfo(IStringCompactor comp) {
        MuteInfo mute = new MuteInfo();

        mute.id = id;

        mute.assignment = new MuteAssignment();
        mute.assignment.muteDate = TimeUtil.timestampToTcSimpleDate(muteDate);
        mute.assignment.text = comp.getStringFromId(text);

        mute.scope = scope.toMuteScope(comp);

        mute.target = new MuteTarget();
        mute.target.tests = new ArrayList<>();

        if (testIds == null)
            return mute;

        for (int i = 0; i < testIds.length; i++) {
            TestRef test = new TestRef();

            test.id = String.valueOf(testIds[i]);
            test.name = comp.getStringFromId(testNames[i]);
            test.href = "/app/rest/tests/id:" + id;

            mute.target.tests.add(test);
        }

        return mute;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MuteInfoCompacted compacted = (MuteInfoCompacted)o;
        return id == compacted.id;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(id);
    }

    public int id() {
        return id;
    }
}
