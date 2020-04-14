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
package org.apache.ignite.tcbot.engine.defect;

import java.util.Objects;
import org.apache.ignite.tcbot.persistence.Persisted;

@Persisted
public class DefectIssue {
    private int issueTypeCode;
    private int testOrSuiteName;

    private double flakyRate;

    public DefectIssue(int issueTypeCode, Integer testNameCid, double flakyRate) {
        this.issueTypeCode = issueTypeCode;
        testOrSuiteName = testNameCid;
        this.flakyRate = flakyRate;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DefectIssue issue = (DefectIssue)o;
        return issueTypeCode == issue.issueTypeCode &&
            testOrSuiteName == issue.testOrSuiteName;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(issueTypeCode, testOrSuiteName);
    }

    public int testNameCid() {
        return testOrSuiteName;
    }

    public int issueTypeCode() {
        return issueTypeCode;
    }

    public double getFlakyRate() {
        return flakyRate;
    }
}
