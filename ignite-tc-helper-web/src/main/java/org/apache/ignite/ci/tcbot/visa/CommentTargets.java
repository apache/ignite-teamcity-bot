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

package org.apache.ignite.ci.tcbot.visa;

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Comment destination selector for TCBot analysis.
 */
public class CommentTargets {
    /** JIRA target. */
    public static final String JIRA = "JIRA";

    /** GitHub Pull Request target. */
    public static final String GITHUB = "GITHUB";

    /** Default target for old callers. */
    public static final String DFLT = JIRA;

    /** Allowed targets. */
    private static final Set<String> ALLOWED = new LinkedHashSet<>(Arrays.asList(JIRA, GITHUB));

    /**
     * @param targets Raw targets.
     */
    public static String normalize(@Nullable String targets) {
        if (Strings.isNullOrEmpty(targets))
            return DFLT;

        Set<String> normalizedTargets = Arrays.stream(targets.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toUpperCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String target : normalizedTargets) {
            if (!ALLOWED.contains(target))
                throw new IllegalArgumentException("Unknown comment target: " + target);
        }

        String normalized = normalizedTargets.stream().collect(Collectors.joining(","));

        return Strings.isNullOrEmpty(normalized) ? DFLT : normalized;
    }

    /**
     * @param targets Raw targets.
     */
    public static boolean jira(String targets) {
        return contains(targets, JIRA);
    }

    /**
     * @param targets Raw targets.
     */
    public static boolean github(String targets) {
        return contains(targets, GITHUB);
    }

    /**
     * @param targets Raw targets.
     * @param target Target.
     */
    private static boolean contains(String targets, String target) {
        return Arrays.stream(normalize(targets).split(","))
            .anyMatch(target::equals);
    }
}
