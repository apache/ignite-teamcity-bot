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

package org.apache.ignite.ci.analysis;

import java.util.concurrent.TimeUnit;
import org.apache.ignite.ci.db.Persisted;

/**
 * Wrapper for timestamped entry to be reloaded later.
 */
public class Expirable<D> {
    private final long ts;
    private final D data;

    public Expirable(D data) {
        this(System.currentTimeMillis(), data);
    }

    public Expirable(long ts, D data) {
        this.ts = ts;
        this.data = data;
    }

    public long getTs() {
        return ts;
    }

    public D getData() {
        return data;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - ts;
    }

    public boolean isAgeLessThanSecs(int seconds) {
        return getAgeMs() < TimeUnit.SECONDS.toMillis(seconds);
    }
}
