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
package org.apache.ignite.tcbot.persistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.GuardedBy;

/**
 * In-memory string compactor analogue without using Ignite.
 */
public class InMemoryStringCompactor implements IStringCompactor {
    /** Id to string mapping. */
    @GuardedBy("this")
    private final Map<Integer, String> idToStr = new ConcurrentHashMap<>();

    /** String to ID mapping. */
    @GuardedBy("this")
    private final Map<String, Integer> strToId = new ConcurrentHashMap<>();

    /** ID generator Sequence. */
    @GuardedBy("this")
    private final AtomicInteger seq = new AtomicInteger();

    /** {@inheritDoc} */
    @Override public int getStringId(String val) {
        if (val == null)
            return -1;
        Integer id = strToId.get(val);

        if (id == null) {
            synchronized (this) {
                id = strToId.get(val);
                if (id == null)
                    id = save(val);
            }
        }

        return id;
    }

    private int save(String val) {
        int newId = seq.incrementAndGet();

        strToId.put(val, newId);
        idToStr.put(newId, val);

        return newId;
    }

    /** {@inheritDoc} */
    @Override public String getStringFromId(int id) {
        String val = idToStr.get(id);

        if (val == null) {
            synchronized (this) {
                val = idToStr.get(id);

            }
        }

        return val;
    }

    /** {@inheritDoc} */
    @Override public Integer getStringIdIfPresent(String val) {
        if (val == null)
            return -1;

        Integer id = strToId.get(val);

        if (id == null) {
            synchronized (this) {
                id = strToId.get(val);
            }
        }

        return id;
    }
}
