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
package org.apache.ignite.tcbot.engine.defect;

import java.util.Arrays;
import org.apache.ignite.tcbot.persistence.Persisted;

@Persisted
public class CommitCompacted implements Comparable<CommitCompacted> {
    /** Sha of the commit. */
    private byte[] data;

    public CommitCompacted(byte[] data) {
        this.data = data;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CommitCompacted commit = (CommitCompacted)o;
        return Arrays.equals(data, commit.data);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Arrays.hashCode(data);
    }

    /** {@inheritDoc} */
    @Override public int compareTo(CommitCompacted o) {
        return compare(data, o.data);
    }

    public static int compare(byte[] a, byte[] b) {
        if (a == b)
            return 0;
        if (a == null || b == null)
            return a == null ? -1 : 1;

        int i = mismatch(a, b,
            Math.min(a.length, b.length));
        if (i >= 0)
            return Byte.compare(a[i], b[i]);

        return a.length - b.length;
    }

    public static int mismatch(byte[] a,
        byte[] b,
        int len) {

        int i = 0;
        for (; i < len; i++) {
            if (a[i] != b[i])
                return i;
        }
        return -1;
    }

}
