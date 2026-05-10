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
package org.apache.ignite.tcbot.persistence.scheduler;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Registry of admin-triggerable maintenance actions owned by concrete services.
 */
public class MaintenanceActionRegistry {
    /** Actions by name. */
    private final ConcurrentMap<String, Action> actions = new ConcurrentHashMap<>();

    /**
     * @param name Action name.
     * @param description Action description.
     * @param action Action body.
     */
    public void register(String name, String description, Callable<String> action) {
        actions.put(name, new Action(name, description, action));
    }

    /**
     * @return Action list.
     */
    public List<MaintenanceActionInfo> actions() {
        return actions.values().stream()
            .map(action -> new MaintenanceActionInfo(action.name, action.description))
            .sorted(Comparator.comparing(info -> info.name))
            .collect(Collectors.toList());
    }

    /**
     * @param name Action name.
     * @return {@code true} if action is registered.
     */
    public boolean hasAction(@Nullable String name) {
        return name != null && actions.containsKey(name);
    }

    /**
     * @param name Action name.
     * @return Action result.
     */
    public String run(String name) throws Exception {
        Action action = actions.get(name);

        if (action == null)
            throw new IllegalArgumentException("Maintenance action is not registered: " + name);

        return action.action.call();
    }

    /** Registered action. */
    private static class Action {
        /** Action name. */
        private final String name;

        /** Action description. */
        private final String description;

        /** Action body. */
        private final Callable<String> action;

        /**
         * @param name Action name.
         * @param description Action description.
         * @param action Action body.
         */
        private Action(String name, String description, Callable<String> action) {
            this.name = name;
            this.description = description;
            this.action = action;
        }
    }
}
