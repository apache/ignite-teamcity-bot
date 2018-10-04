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

package org.apache.ignite.ci.web.model.chart;

import com.google.common.base.Preconditions;
import java.util.Set;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.web.IBackgroundUpdatable;

/**
 * 
 */
@Deprecated
public class TestsMetrics implements IBackgroundUpdatable {
    public ChartData<SuiteInBranch> failed = new ChartData<>();
    public ChartData<SuiteInBranch> notrun = new ChartData<>();
    public ChartData<SuiteInBranch> muted = new ChartData<>();
    public ChartData<SuiteInBranch> total = new ChartData<>();

    public boolean updateRequired;

    public void initBuilds(Set<SuiteInBranch> builds) {
        builds.forEach(branch -> {
            failed.addEmptyLine(branch, SuiteInBranch::getBranch);
            notrun.addEmptyLine(branch);
            muted.addEmptyLine(branch);
            total.addEmptyLine(branch);
        });
    }

    public int addAxisXLabel(String label) {
        int idx1 = failed.addAxisXLabel(label);
        int idx2 = notrun.addAxisXLabel(label);
        int idx3 = muted.addAxisXLabel(label);
        int idx4 = total.addAxisXLabel(label);
        Preconditions.checkState(idx1 == idx2);
        Preconditions.checkState(idx3 == idx4);
        Preconditions.checkState(idx1 == idx3);
        return idx1;
    }

    @Override public void setUpdateRequired(boolean update) {
        this.updateRequired = update;
    }
}
