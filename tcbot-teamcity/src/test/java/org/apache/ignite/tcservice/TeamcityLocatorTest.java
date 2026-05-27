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

package org.apache.ignite.tcservice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** */
public class TeamcityLocatorTest {
    /** */
    @Test
    public void valueEscapesLocatorSpecialCharsAndUrlEncodes() {
        assertEquals("pull%2F12006%2Fhead", TeamcityLocator.value("pull/12006/head"));
        assertEquals("feature%5C%2C+with+space%5C%28x%5C%29%5C%3Ay%5C%5Cz",
            TeamcityLocator.value("feature, with space(x):y\\z"));
    }

    /** */
    @Test
    public void buildsByTypeAndBranchKeepsLocatorSyntaxOutsideValues() {
        assertEquals("app/rest/latest/builds?locator=defaultFilter:false,buildType:(id:Suite%5C%28A%5C%29)," +
                "branch:feature%5C%2C+with+space,count:20",
            TeamcityLocator.buildsByTypeAndBranch("Suite(A)", "feature, with space", 20));
    }
}
