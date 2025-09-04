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

package org.apache.ignite.migrate;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.slf4j.Logger;

/**
 * Performs recursive transformation of values, replacing legacy GridIntList with the new type.
 * Guarded by a MAX_DEPTH to avoid accidental cycles.
 */
public final class Transformer {
    /** CLI verbose flag. */
    private final boolean verbose;

    /**
     * Fully-qualified name of the legacy type to replace.
     */
    private static final String OLD_KEYS_TYPE = "org.apache.ignite.internal.util.GridIntList";

    /**
     * Fully-qualified name of the new type.
     */
    private static final String NEW_KEYS_TYPE = "org.apache.ignite.tcbot.common.util.GridIntList";

    /**
     * Recursion guard to prevent accidental cycles.
     */
    private static final int MAX_DEPTH = 32;

    /**
     * Logger.
     */
    private static final Logger log = GridIntListMigrator.GetMigratorLogger();

    /** Cached constructor of NEW_KEYS_TYPE(int[]) to avoid excessive reflection. */
    private final Constructor<?> newKeysCachedConstruct;

    Transformer(boolean verb) {
        verbose = verb;
        newKeysCachedConstruct = findNewKeysConstruct();
    }

    /**
     * Recursively transforms a value.
     * <p>
     * Rules:
     * - BinaryObject of OLD_KEYS_TYPE -> build NEW_KEYS_TYPE or fallback to int[].
     * - Java object of OLD_KEYS_TYPE -> same replacement.
     * - BinaryObject (other type) -> rebuild only if some field changed.
     * - List/Set/Map/Object[] -> rebuild container only if any element changed.
     * - Primitives/String/int[] -> unchanged.
     *
     * @param v     value to transform
     * @param depth recursion depth (fixed guard inside)
     * @return transform result with possibly new value and 'changed' flag
     */
    TransformResult transform(Object v, int depth) {
        if (v == null)
            return TransformResult.same(null);

        // Strict migration interruption
        if (depth > MAX_DEPTH) {
            String errMsg = String.format("Max depth %d reached; value type=%s, aborting migration.", MAX_DEPTH, v.getClass());

            throw new IllegalStateException(errMsg);
        }

        // Binary legacy type
        if (v instanceof BinaryObject) {
            BinaryObject bo = (BinaryObject)v;
            String typeName = bo.type().typeName();

            if (typeName.equals(OLD_KEYS_TYPE)) {
                int[] ints = extractInts(bo);
                Object newKeys = buildNewKeys(ints);

                return TransformResult.changed(newKeys);
            }

            // Generic BinaryObject
            BinaryObjectBuilder bb = bo.toBuilder();
            boolean anyChanged = false;

            for (String oldChildField : bo.type().fieldNames()) {
                // Transform nested fields
                TransformResult childRes = transform(bo.field(oldChildField), depth + 1);

                if (childRes.changed) {
                    bb.setField(oldChildField, childRes.val);

                    anyChanged = true;
                }
            }

            // Rebuild only if some field changed
            return anyChanged ? TransformResult.changed(bb.build()) : TransformResult.same(v);
        }

        // Java legacy type
        if (v instanceof org.apache.ignite.internal.util.GridIntList) {
            org.apache.ignite.internal.util.GridIntList g = (org.apache.ignite.internal.util.GridIntList)v;
            int[] ints = new int[g.size()];

            for (int i = 0; i < ints.length; i++)
                ints[i] = g.get(i);

            Object newKeys = buildNewKeys(ints);

            return TransformResult.changed(newKeys);
        }

        // Already new type
        if (v.getClass().getName().equals(NEW_KEYS_TYPE))
            return TransformResult.same(v);

        // Collections
        if (v instanceof List) {
            List<?> src = (List<?>)v;
            boolean anyChanged = false;

            List<Object> out = new ArrayList<>(src.size());

            for (Object listElem : src) {
                TransformResult tr = transform(listElem, depth + 1);

                out.add(tr.val);
                anyChanged |= tr.changed;
            }

            return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
        }

        if (v instanceof Set) {
            Set<?> src = (Set<?>)v;
            boolean anyChanged = false;

            LinkedHashSet<Object> out = new LinkedHashSet<>(Math.max(16, (int)Math.ceil(src.size() / 0.75)));

            for (Object el : src) {
                TransformResult tr = transform(el, depth + 1);

                out.add(tr.val);
                anyChanged |= tr.changed;
            }

            return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
        }

        if (v instanceof Map) {
            Map<?, ?> src = (Map<?, ?>)v;
            boolean anyChanged = false;

            LinkedHashMap<Object, Object> out = new LinkedHashMap<>(Math.max(16, (int)Math.ceil(src.size() / 0.75)));

            for (Map.Entry<?, ?> en : src.entrySet()) {
                TransformResult k = transform(en.getKey(), depth + 1);
                TransformResult val = transform(en.getValue(), depth + 1);

                out.put(k.val, val.val);
                anyChanged |= k.changed || val.changed;
            }

            return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
        }

        // Object[] arrays (non-primitive)
        if (v.getClass().isArray() && !v.getClass().getComponentType().isPrimitive()) {
            Object[] arr = (Object[])v;
            Object[] out = new Object[arr.length];

            boolean anyChanged = false;

            for (int i = 0; i < arr.length; i++) {
                TransformResult tr = transform(arr[i], depth + 1);

                out[i] = tr.val;
                anyChanged |= tr.changed;
            }

            return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
        }

        // Primitives, String, int[] etc.: unchanged
        return TransformResult.same(v);
    }

    /**
     * Extracts int[] from legacy GridIntList BinaryObject.
     */
    private int[] extractInts(BinaryObject oldKeys) {
        try {
            Object obj = oldKeys.deserialize();

            if (obj instanceof org.apache.ignite.internal.util.GridIntList) {
                org.apache.ignite.internal.util.GridIntList g = (org.apache.ignite.internal.util.GridIntList)obj;

                return g.array();
            }
        }
        catch (Throwable t) {
            if (verbose)
                log.info("Deserialize fallback: {}", t.toString());
        }

        // Hardcode ("arr") based on GridIntList fields
        Collection<String> childFields = oldKeys.type().fieldNames();
        if (childFields.contains("arr")) {
            int[] arr = oldKeys.field("arr");

            if (arr != null)
                return arr;
        }

        log.warn("Can't extract ints from {} fields={}", oldKeys.type().typeName(), childFields);

        return new int[0]; // best effort fallback
    }

    /**
     * Builds an instance of the new GridIntList (org.apache.ignite.tcbot.common.util.GridIntList),
     * or falls back to int[] if the class is not on the classpath.
     */
    private Object buildNewKeys(int[] ints) {
        if (newKeysCachedConstruct != null) {
            try {
                return newKeysCachedConstruct.newInstance((Object)ints);
            }
            catch (ReflectiveOperationException ignored) {
                // fall through to fallback
            }
        }

        if (verbose)
            log.warn("NEW_KEYS_TYPE {} is not available; falling back to raw int[]", NEW_KEYS_TYPE);

        return ints; // best effort fallback
    }

    /**
     * Resolves constructor NEW_KEYS_TYPE(int[]) once to avoid reflection per entry.
     */
    private Constructor<?> findNewKeysConstruct() {
        try {
            Class<?> cls = Class.forName(NEW_KEYS_TYPE);

            return cls.getConstructor(int[].class);
        }
        catch (Throwable t) {
            if (verbose) {
                log.warn("New keys type {} is not on classpath, will fallback to int[] (cause: {})",
                    NEW_KEYS_TYPE, t.toString());
            }

            return null;
        }
    }
}
