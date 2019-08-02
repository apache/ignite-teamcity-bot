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

import java.util.ArrayList;
import java.util.stream.Collectors;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.InMemoryStringCompactor;
import org.apache.ignite.tcignited.build.TestCompactedV2;
import org.apache.ignite.tcignited.buildlog.ILogProductSpecific;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;
import org.apache.ignite.tcservice.model.result.tests.TestRef;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestCompactedV2Test {
    @Test
    public void testTestFlags() {
        TestCompactedV2 t = new TestCompactedV2();
        assertNull(t.getCurrInvestigatedFlag());

        t.setCurrentlyInvestigated(true);
        assertTrue(t.isInvestigated());

        System.out.println(t);
        t.setIgnored(true);
        assertTrue(t.isIgnoredTest());

        t.setIgnored(false);
        assertFalse(t.isIgnoredTest());

        System.out.println(t);
    }

    @Test
    public void testCovertIsToNewModel() {
        FatBuildCompacted buildCompacted = new FatBuildCompacted();
        ILogProductSpecific logSpec = Mockito.mock(ILogProductSpecific.class);
        assertFalse(buildCompacted.migrateTests(logSpec));

        IStringCompactor c = new InMemoryStringCompactor();
        ArrayList<TestOccurrenceFull> page = new ArrayList<>();
        TestOccurrenceFull occ = new TestOccurrenceFull();
        occ.test = new TestRef();
        occ.test.name = "Hello, World";
        occ.name = occ.test.name;
        page.add(occ);
        buildCompacted.addTests(c, page, logSpec);

        assertEquals(1, buildCompacted.getAllTests().count());

        assertFalse(buildCompacted.migrateTests(logSpec));
    }


    @Test
    public void testMigration() {
        FatBuildCompacted buildCompacted = new FatBuildCompacted();
        ILogProductSpecific logSpec = Mockito.mock(ILogProductSpecific.class);
        assertFalse(buildCompacted.migrateTests(logSpec));

        IStringCompactor c = new InMemoryStringCompactor();
        ArrayList<TestOccurrenceFull> page = new ArrayList<>();
        TestOccurrenceFull occ = new TestOccurrenceFull();
        occ.test = new TestRef();
        occ.test.name = "Hello, World";
        occ.name = occ.test.name;
        page.add(occ);
        for (TestOccurrenceFull next : page) {
            TestCompacted compacted = new TestCompacted(c, next);

            buildCompacted.oldTestsFmtAdd(compacted);
        }

        assertEquals(1, buildCompacted.getAllTests().count());

        assertTrue(buildCompacted.migrateTests(logSpec));

        assertEquals(1, buildCompacted.getAllTests().count());

        System.out.println(buildCompacted.getAllTests().collect(Collectors.toList()));

        assertFalse(buildCompacted.migrateTests(logSpec));
    }
}
