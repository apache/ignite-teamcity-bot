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

package org.apache.ignite.ci.tcmodel.mute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.result.tests.TestRef;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.typedef.F;

/**
 * @see MuteInfo
 */
public class MuteInfoCompacted {
    /** Mute id. */
    int id;

    /** Assignment. Mute date. */
    int muteDate;

    /** Assignment. Text. */
    int text;

    /** Scope. Project id. */
    int projectId;

    /** Scope. Project name. */
    int projectName;

    /** Target. Test ids. */
    long[] testIds;

    /** Target. Test names. */
    int[] testNames;

    /** Target. Test hrefs. */
    int[] testHrefs;

    /**
     * @param mute Mute to compact.
     * @param comp Comparator.
     */
    MuteInfoCompacted(MuteInfo mute, IStringCompactor comp) {
        id = mute.id;

        muteDate = comp.getStringId(mute.assignment.muteDate);
        text = comp.getStringId(mute.assignment.text);

        projectId = comp.getStringId(mute.scope.project.id);
        projectName = comp.getStringId(mute.scope.project.name);

        List<TestRef> tests = mute.target.tests;

        if (F.isEmpty(tests))
            return;

        testIds = new long[tests.size()];
        testNames = new int[tests.size()];
        testHrefs = new int[tests.size()];

        for (int i = 0; i < tests.size(); i++) {
            TestRef test = tests.get(i);

            testIds[i] = test.id;
            testNames[i] = comp.getStringId(test.name);
            testHrefs[i] = comp.getStringId(test.href);
        }
    }

    /**
     * @param comp Comparator.
     * @return Exctracted mute.
     */
    MuteInfo toMuteInfo(IStringCompactor comp) {
        MuteInfo mute = new MuteInfo();

        mute.id = id;

        mute.assignment = new MuteAssignment();
        mute.assignment.muteDate = comp.getStringFromId(muteDate);
        mute.assignment.text = comp.getStringFromId(text);

        mute.scope = new MuteScope();
        mute.scope.project = new Project();
        mute.scope.project.id = comp.getStringFromId(projectId);
        mute.scope.project.name = comp.getStringFromId(projectName);

        mute.target = new MuteTarget();
        mute.target.tests = new ArrayList<>();

        for (int i = 0; i < testIds.length; i++) {
            TestRef test = new TestRef();

            test.id = testIds[i];
            test.name = comp.getStringFromId(testNames[i]);
            test.href = comp.getStringFromId(testHrefs[i]);

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

        if (id != compacted.id)
            return false;
        if (muteDate != compacted.muteDate)
            return false;
        if (text != compacted.text)
            return false;
        if (projectId != compacted.projectId)
            return false;
        if (projectName != compacted.projectName)
            return false;
        if (!Arrays.equals(testIds, compacted.testIds))
            return false;
        if (!Arrays.equals(testNames, compacted.testNames))
            return false;

        return Arrays.equals(testHrefs, compacted.testHrefs);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = id;

        res = 31 * res + muteDate;
        res = 31 * res + text;
        res = 31 * res + projectId;
        res = 31 * res + projectName;
        res = 31 * res + Arrays.hashCode(testIds);
        res = 31 * res + Arrays.hashCode(testNames);
        res = 31 * res + Arrays.hashCode(testHrefs);

        return res;
    }
}
