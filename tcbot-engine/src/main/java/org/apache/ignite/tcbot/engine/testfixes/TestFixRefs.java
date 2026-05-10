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

package org.apache.ignite.tcbot.engine.testfixes;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.persistence.IVersionedEntity;
import org.apache.ignite.tcbot.persistence.Persisted;

/**
 * Reverse lookup from a suite/test spelling to source fix ids.
 */
@Persisted
public class TestFixRefs implements IVersionedEntity {
    /** Entity version. */
    private static final int LATEST_VERSION = 1;

    /** Entity version. */
    @SuppressWarnings("FieldCanBeLocal") private Integer _ver = LATEST_VERSION;

    /** Matched test or suite name. */
    public String entityName;

    /** Matched suite name, when known. */
    @Nullable public String suiteName;

    /** Matched suite id, when known. */
    @Nullable public String suiteId;

    /** Matched test name, when known. */
    @Nullable public String testName;

    /** Compacted test name id, when known. */
    @Nullable public Integer testNameId;

    /** Tracked branch used to resolve this match. */
    @Nullable public String trackedBranch;

    /** Current master TeamCity suite status URL. */
    @Nullable public String currentStatusUrl;

    /** Synthetic source ids. */
    public List<String> sourceIds = new ArrayList<>();

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver == null ? -1 : _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }
}
