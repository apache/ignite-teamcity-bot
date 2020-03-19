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
package org.apache.ignite.tcbot.engine.ui;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.history.IRunHistory;

import static org.apache.ignite.tcbot.engine.ui.DsSuiteUi.buildWebLinkToBuild;

public class ShortSuiteUi1 extends DsHistoryStatUi {
    /** Suite Name */
    public String name;

    public List<ShortTestUi> tests = new ArrayList<>();

    public Collection<? extends ShortTestUi> tests() {
        return tests;
    }

    public ShortSuiteUi1 tests(List<ShortTestUi> tests) {
        this.tests = tests;

        return this;
    }

    public ShortSuiteUi1 initFrom(@Nonnull MultBuildRunCtx suite) {
        name = suite.suiteName();

        return this;
    }
}
