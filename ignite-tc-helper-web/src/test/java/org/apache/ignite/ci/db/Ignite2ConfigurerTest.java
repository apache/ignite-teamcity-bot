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

package org.apache.ignite.ci.db;

import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** */
public class Ignite2ConfigurerTest {
    /** */
    @Test
    public void inMemoryModeRequiresIntegrationTestProfile() {
        withIgniteInMemoryProperties("true", null, () -> {
            try {
                Ignite2Configurer.getDataRegionConfiguration();

                fail("In-memory Ignite storage must be rejected outside integration-test profile.");
            }
            catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains(Ignite2Configurer.IN_MEMORY_PROPERTY));
            }
        });
    }

    /** */
    @Test
    public void inMemoryModeIsAllowedForIntegrationTestProfile() {
        withIgniteInMemoryProperties("true", Ignite2Configurer.INTEGRATION_TEST_PROFILE, () -> {
            DataRegionConfiguration region = Ignite2Configurer.getDataRegionConfiguration();

            assertFalse(region.isPersistenceEnabled());
            assertEquals(WALMode.NONE, Ignite2Configurer.getDataStorageConfiguration(region).getWalMode());
        });
    }

    /** */
    @Test
    public void persistentModeIsDefault() {
        withIgniteInMemoryProperties(null, null, () -> {
            DataRegionConfiguration region = Ignite2Configurer.getDataRegionConfiguration();

            assertTrue(region.isPersistenceEnabled());
            assertEquals(WALMode.LOG_ONLY, Ignite2Configurer.getDataStorageConfiguration(region).getWalMode());
        });
    }

    /** */
    private static void withIgniteInMemoryProperties(String inMemory, String profile, Runnable action) {
        String oldInMemory = System.getProperty(Ignite2Configurer.IN_MEMORY_PROPERTY);
        String oldProfile = System.getProperty(Ignite2Configurer.PROFILE_PROPERTY);

        try {
            setOrClear(Ignite2Configurer.IN_MEMORY_PROPERTY, inMemory);
            setOrClear(Ignite2Configurer.PROFILE_PROPERTY, profile);

            action.run();
        }
        finally {
            setOrClear(Ignite2Configurer.IN_MEMORY_PROPERTY, oldInMemory);
            setOrClear(Ignite2Configurer.PROFILE_PROPERTY, oldProfile);
        }
    }

    /** */
    private static void setOrClear(String name, String value) {
        if (value == null)
            System.clearProperty(name);
        else
            System.setProperty(name, value);
    }
}
