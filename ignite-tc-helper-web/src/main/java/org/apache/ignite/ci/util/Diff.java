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

package org.apache.ignite.ci.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Show diff between two <b>sorted</b> collections.
 *
 * @param <T> Type of the elements.
 */
public class Diff<T extends Comparable<T>> {
    /** Elements existing only in modified collection. */
    private final List<T> added = new ArrayList<>();

    /** Elements existing only in original collection. */
    private final List<T> rmvd = new ArrayList<>();

    /** Elements existing in both collections. */
    private final List<T> same = new ArrayList<>();

    /**
     * @param c1 First collection to compare.
     * @param c2 Seconds collection to compare.
     */
    public Diff(Collection<T> c1, Collection<T> c2) {
        if (c1.isEmpty()) {
            added.addAll(c2);

            return;
        }

        if (c2.isEmpty()) {
            rmvd.addAll(c1);

            return;
        }

        Iterator<T> oldIter = c1.iterator();
        Iterator<T> newIter = c2.iterator();

        int cmp = 0;

        T e1 = null;
        T e2 = null;

        while (oldIter.hasNext() || newIter.hasNext()) {
            if (oldIter.hasNext() && cmp <= 0)
                e1 = oldIter.next();

            if (newIter.hasNext() && cmp >= 0)
                e2 = newIter.next();

            assert e1 != null;
            assert e2 != null;

            cmp = e1.compareTo(e2);

            if (cmp < 0)
                rmvd.add(e1);
            else if (cmp > 0)
                added.add(e2);
            else
                same.add(e1);

            if (!oldIter.hasNext()) {
                if (cmp < 0)
                    added.add(e2);

                while (newIter.hasNext())
                    added.add(newIter.next());
            }

            if (!newIter.hasNext()) {
                if (cmp > 0)
                    rmvd.add(e1);

                while (oldIter.hasNext())
                    rmvd.add(oldIter.next());
            }
        }
    }

    /**
     * @return Elements existing only in original collection.
     */
    public List<T> added() {
        return added;
    }

    /**
     * @return Elements existing only in modified collection.
     */
    public List<T> removed() {
        return rmvd;
    }

    /**
     * @return Elements existing only in both collections.
     */
    public List<T> same() {
        return same;
    }

}
