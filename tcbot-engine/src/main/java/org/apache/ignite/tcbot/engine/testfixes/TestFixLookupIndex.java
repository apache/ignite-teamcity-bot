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

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Response-local lookup for decorating suites and test failures with known fix refs.
 */
class TestFixLookupIndex {
    /** Suite-level lookup entity. */
    private static final String SUITE_ENTITY = "suite";

    /** Max refs shown for a suite/test. */
    private static final int MAX_REFS = 5;

    /** Matches by suite id and compacted test id lookup key. */
    private final Map<String, List<TestFixRefUi>> refsByKey = new HashMap<>();

    /** */
    public void add(String lookupKey, List<TestFixRefUi> refs) {
        if (Strings.isNullOrEmpty(lookupKey) || refs == null || refs.isEmpty())
            return;

        refsByKey.computeIfAbsent(lookupKey, unused -> new ArrayList<>()).addAll(refs);
    }

    /** */
    public void finish() {
        for (Map.Entry<String, List<TestFixRefUi>> entry : refsByKey.entrySet())
            entry.setValue(limit(uniqueRefs(entry.getValue())));
    }

    /** */
    public List<TestFixRefUi> findSuite(@Nullable String suiteId) {
        return find(suiteLookupKey(suiteId));
    }

    /** */
    public List<TestFixRefUi> findTest(@Nullable String suiteId, @Nullable Integer testNameId) {
        return find(testLookupKey(suiteId, testNameId));
    }

    /** */
    static String suiteLookupKey(@Nullable String suiteId) {
        return lookupKey(suiteId, SUITE_ENTITY);
    }

    /** */
    static String testLookupKey(@Nullable String suiteId, @Nullable Integer testNameId) {
        if (testNameId == null)
            return "";

        return lookupKey(suiteId, "test:" + testNameId);
    }

    /** */
    private List<TestFixRefUi> find(String lookupKey) {
        if (Strings.isNullOrEmpty(lookupKey))
            return new ArrayList<>();

        List<TestFixRefUi> found = refsByKey.get(lookupKey);

        return found == null ? new ArrayList<>() : new ArrayList<>(found);
    }

    /** */
    private static String lookupKey(@Nullable String suiteId, String entityKey) {
        if (Strings.isNullOrEmpty(suiteId))
            return "";

        return suiteId + "::" + entityKey;
    }

    /** */
    private static List<TestFixRefUi> uniqueRefs(List<TestFixRefUi> refs) {
        Map<String, TestFixRefUi> res = new LinkedHashMap<>();

        for (TestFixRefUi ref : refs)
            res.putIfAbsent(ref.sourceType + ":" + ref.text, ref);

        return new ArrayList<>(res.values());
    }

    /** */
    private static List<TestFixRefUi> limit(List<TestFixRefUi> refs) {
        if (refs.size() <= MAX_REFS)
            return refs;

        return new ArrayList<>(refs.subList(0, MAX_REFS));
    }
}
