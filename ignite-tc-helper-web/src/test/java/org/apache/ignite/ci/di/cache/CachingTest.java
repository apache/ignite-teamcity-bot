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
package org.apache.ignite.ci.di.cache;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.common.interceptor.GuavaCachedModule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CachingTest {
    @Test
    public void testCacheWorks() {
        Injector injector = Guice.createInjector(new GuavaCachedModule());

        SomeWorker instance = injector.getInstance(SomeWorker.class);

        for (int i = 0; i < 200; i++)
            instance.doSmt();

        assertEquals(1, instance.doSmtMtdCalls.get());

        {
            for (int i = 0; i < 200; i++)
                instance.toString(i);

            assertEquals(200, instance.toStringMtdCalls.get());

            for (int i = 0; i < 200; i++)
                instance.toString(i);

            assertEquals(200, instance.toStringMtdCalls.get());

            for (int i = 0; i < 200; i++)
                instance.toString(-i - 1);

            assertEquals(400, instance.toStringMtdCalls.get());
        }

        for (int i = 0; i < 100; i++)
            instance.parseInt(Integer.toString(i % 10));

        assertEquals(10, instance.parseIntMtdCalls.get());

        for (int i = 0; i < 100; i++)
            instance.parseInt(Integer.toString(-(i % 10) - 1));

        assertEquals(110, instance.parseIntMtdCalls.get());
    }

    public static class SomeWorker {
        AtomicInteger doSmtMtdCalls = new AtomicInteger();
        AtomicInteger toStringMtdCalls = new AtomicInteger();
        AtomicInteger parseIntMtdCalls = new AtomicInteger();

        @GuavaCached
        public String doSmt() {
            doSmtMtdCalls.incrementAndGet();

            return "";
        }

        @GuavaCached(cacheNullRval = false)
        public String toString(int i) {
            toStringMtdCalls.incrementAndGet();
            if (i < 0)
                return null;
            return Integer.toString(i);
        }

        @GuavaCached(cacheNegativeNumbersRval = false)
        public int parseInt(String val) {
            parseIntMtdCalls.incrementAndGet();

            return Integer.parseInt(val);
        }
    }

}
