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

package org.apache.ignite.ci.web.model.current;

import com.google.common.base.Objects;
import javax.annotation.Nullable;

public class FailureSummary {
    /** Registered number of failures from TC helper DB */
    @Nullable public Integer failures;

    /** Registered number of runs from TC helper DB */
    @Nullable public Integer runs;

    /** Registered percent of fails from TC helper DB, comma is always used as separator char. */
    @Nullable public String failureRate;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FailureSummary summary = (FailureSummary)o;
        return Objects.equal(failures, summary.failures) &&
            Objects.equal(runs, summary.runs) &&
            Objects.equal(failureRate, summary.failureRate);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(failures, runs, failureRate);
    }
}
