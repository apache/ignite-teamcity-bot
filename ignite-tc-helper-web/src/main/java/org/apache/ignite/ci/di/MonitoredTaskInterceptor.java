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
package org.apache.ignite.ci.di;

import com.google.common.base.Strings;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.common.util.TimeUtil;
import org.jetbrains.annotations.NotNull;

public class MonitoredTaskInterceptor implements MethodInterceptor {
    private final ConcurrentMap<String, Invocation> totalTime = new ConcurrentSkipListMap<>();

    public static class Invocation {
        private final AtomicLong lastStartTs = new AtomicLong();
        private final AtomicLong lastEndTs = new AtomicLong();
        private final AtomicReference<Object> lastResult = new AtomicReference<>();

        private final AtomicInteger callsCnt = new AtomicInteger();
        private String name;

        Invocation(String name) {
            this.name = name;
        }

        void saveStart(long startTs) {
            callsCnt.incrementAndGet();

            lastStartTs.set(startTs);

            lastEndTs.set(0);
        }

        void saveEnd(long ts, Object res) {
            lastEndTs.set(ts);
            lastResult.set(res);
        }

        public String name() {
            return name;
        }

        public int count() {
            return callsCnt.get();
        }

        /**
         * @return time printable of last observed start time of the task.
         */
        public String start() {
            final long l = lastStartTs.get();
            return TimeUtil.timestampToDateTimePrintable(l);
        }

        /**
         * @return time printable of last observed end time of the task.
         */
        public String end() {
            return TimeUtil.timestampToDateTimePrintable(lastEndTs.get());
        }

        /**
         * @return printable task result to be displayed in cell.
         */
        public String result() {
            if (lastEndTs.get() == 0) {
                long time = System.currentTimeMillis() - lastStartTs.get();

                return ("(running for " + TimeUtil.millisToDurationPrintable(time) + ")");
            }

            return Objects.toString(lastResult.get());
        }
    }

    /** {@inheritDoc} */
    @Override public Object invoke(MethodInvocation invocation) throws Throwable {
        final long startTs = System.currentTimeMillis();

        String fullKey = taskName(invocation);

        final Invocation monitoredInvoke = totalTime.computeIfAbsent(fullKey, Invocation::new);

        monitoredInvoke.saveStart(startTs);

        Object res = null;
        try {
            res = invocation.proceed();

            return res;
        }
        catch (Throwable t) {
            res = t;

            throw t;
        }
        finally {
            monitoredInvoke.saveEnd(System.currentTimeMillis(), res);
        }
    }

    @NotNull
    private String taskName(MethodInvocation invocation) {
        final Method invocationMtd = invocation.getMethod();
        final String cls = invocationMtd.getDeclaringClass().getSimpleName();
        final String mtd = invocationMtd.getName();

        StringBuilder fullKey = new StringBuilder();

        final MonitoredTask annotation = invocationMtd.getAnnotation(MonitoredTask.class);
        if (annotation != null) {
            String activityName = annotation.name();

            if (!Strings.isNullOrEmpty(activityName))
                fullKey.append(activityName);
            else
                fullKey.append(cls).append(".").append(mtd);

            final Object[] arguments = invocation.getArguments();

            final int idx = annotation.nameExtArgIndex();
            if (arguments != null && idx >= 0 && idx < arguments.length)
                fullKey.append(".").append(arguments[idx]);

            int[] ints = annotation.nameExtArgsIndexes();

            for (int i = 0; i < ints.length; i++) {
                int argIdx = ints[i];
                if (arguments != null && argIdx >= 0 && argIdx < arguments.length) {
                    if (i == 0)
                        fullKey.append(":");
                    else
                        fullKey.append(", ");

                    fullKey.append(arguments[argIdx]);
                }
            }

        }
        else
            fullKey.append(cls).append(".").append(mtd);

        return fullKey.toString();
    }

    public Collection<Invocation> getList() {
        return Collections.unmodifiableCollection(totalTime.values());
    }
}
