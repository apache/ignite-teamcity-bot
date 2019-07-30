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
package org.apache.ignite.tcbot.common.interceptor;

import com.google.common.base.Stopwatch;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class AutoProfilingInterceptor implements MethodInterceptor {
    private final ConcurrentMap<String, Invocation> totalTime = new ConcurrentHashMap<>();

    public void reset() {
        totalTime.clear();
    }

    public static class Invocation {
        private final AtomicLong timeNanos = new AtomicLong();
        private final AtomicInteger callsCnt = new AtomicInteger();
        private String name;

        public Invocation(String name) {
            this.name = name;
        }

        public long addAndGet(long elapsed) {
            callsCnt.incrementAndGet();

            return timeNanos.addAndGet(elapsed);
        }

        public long getNanos() {
            return timeNanos.get();
        }

        public int getCount() {
            return callsCnt.get();
        }

        public String getName() {
            return name;
        }
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String cls = invocation.getMethod().getDeclaringClass().getSimpleName();
        String mtd = invocation.getMethod().getName();

        Stopwatch started = Stopwatch.createStarted();
        try {
            return invocation.proceed();
        }
        finally {
            long elapsed = started.elapsed(TimeUnit.NANOSECONDS);

            String fullKey = cls + "." + mtd;

            totalTime.computeIfAbsent(fullKey, Invocation::new).addAndGet(elapsed);
        }
    }

    public Collection<Invocation> getInvocations() {
        return Collections.unmodifiableCollection(totalTime.values());
    }
}
