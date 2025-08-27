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

package src.main.java.migrate;

/**
 * Result of a recursive transformation: value and flag indicating changes.
 */
public final class TransformResult {
    /** Value. */
    final Object val;

    /** Changed. */
    final boolean changed;

    private TransformResult(Object val, boolean changed) {
        this.val = val;
        this.changed = changed;
    }

    static TransformResult same(Object v) {
        return new TransformResult(v, false);
    }

    static TransformResult changed(Object v) {
        return new TransformResult(v, true);
    }
}