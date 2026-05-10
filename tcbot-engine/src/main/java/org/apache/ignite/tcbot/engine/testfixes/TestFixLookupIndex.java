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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * In-memory lookup for decorating suites and test failures with known fix refs.
 */
class TestFixLookupIndex {
    /** Max refs shown for a suite/test. */
    private static final int MAX_REFS = 5;

    /** Matches by normalized test/suite spellings. */
    private final Map<String, List<TestFixRefUi>> refsByName = new HashMap<>();

    /** */
    public void add(String name, TestFixRefUi ref) {
        if (Strings.isNullOrEmpty(name) || ref == null)
            return;

        for (String key : lookupKeys(name))
            refsByName.computeIfAbsent(key, unused -> new ArrayList<>()).add(ref);
    }

    /** */
    public void finish() {
        for (Map.Entry<String, List<TestFixRefUi>> entry : refsByName.entrySet())
            entry.setValue(limit(uniqueRefs(entry.getValue())));
    }

    /** */
    public List<TestFixRefUi> find(String name) {
        if (Strings.isNullOrEmpty(name))
            return new ArrayList<>();

        List<TestFixRefUi> refs = new ArrayList<>();

        for (String key : lookupKeys(name)) {
            List<TestFixRefUi> found = refsByName.get(key);

            if (found != null)
                refs.addAll(found);
        }

        return limit(uniqueRefs(refs));
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

    /** */
    private static Set<String> lookupKeys(String name) {
        Set<String> res = new LinkedHashSet<>();

        addKeys(res, name);

        int runCfgSep = name.indexOf("::");

        if (runCfgSep >= 0)
            addKeys(res, name.substring(runCfgSep + 2));

        return res;
    }

    /** */
    private static void addKeys(Set<String> res, String name) {
        String normalized = normalize(name);

        if (normalized.isEmpty())
            return;

        res.add(normalized);

        String dotForm = normalized.replace('#', '.');

        res.add(dotForm);

        String classAndMethod = classAndMethod(dotForm);

        if (!classAndMethod.isEmpty())
            res.add(classAndMethod);
    }

    /** */
    private static String classAndMethod(String name) {
        String[] parts = name.split("\\.");

        if (parts.length < 2)
            return "";

        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    /** */
    private static String normalize(String name) {
        return Strings.nullToEmpty(name).trim().toLowerCase(Locale.ROOT);
    }
}
