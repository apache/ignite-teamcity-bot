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

import com.google.common.base.Preconditions;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.observer.ObserverTask;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.teamcity.restcached.ITcServerFactory;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.apache.ignite.configuration.CacheConfiguration;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DiContextTest {
    /**
     *
     */
    @Test
    public void checkSingletons() {
        Injector injector = getInjector();

        validateInstanceCachedFor(injector, ITcServerProvider.class);
        validateInstanceCachedFor(injector, ITcServerFactory.class);
        validateInstanceCachedFor(injector, ObserverTask.class);
    }

    public Injector getInjector() {
        IgniteTcBotModule igniteTcBotModule = new IgniteTcBotModule();
        Ignite ignite = mock(Ignite.class);
        when(ignite.getOrCreateCache(anyString())).thenReturn(Mockito.mock(IgniteCache.class));
        when(ignite.getOrCreateCache(any(CacheConfiguration.class))).thenReturn(Mockito.mock(IgniteCache.class));

        igniteTcBotModule.setIgniteFut(CompletableFuture.completedFuture(ignite));

        return Guice.createInjector(igniteTcBotModule);
    }

    /**
     *
     */
    @Test
    public void checkPoolIsOne() {
        Injector injector = getInjector();

        TcUpdatePool pool = injector.getInstance(TcUpdatePool.class);
        Preconditions.checkState(pool == injector.getInstance((Class<?>) TcUpdatePool.class),
                "Instance not cached for type " + TcUpdatePool.class);

        pool.stop();
    }

    private <T> T validateInstanceCachedFor(Injector injector, Class<T> type) {
        final T firstInstance = injector.getInstance(type);
        Preconditions.checkState(firstInstance == injector.getInstance(type),
                "Instance not cached for type " + type);

        return firstInstance;
    }

    @Test
    public void testMonitoring() {
        IgniteTcBotModule igniteTcBotModule = new IgniteTcBotModule();
        final Injector injector = Guice.createInjector(igniteTcBotModule);
        final MonitorTest instance = injector.getInstance(MonitorTest.class);

        final String param = "parameter";
        instance.doSmth(param);
        instance.doSmth();
        final MonitoredTaskInterceptor interceptor = injector.getInstance(MonitoredTaskInterceptor.class);
        final Collection<MonitoredTaskInterceptor.Invocation> list = interceptor.getList();

        boolean foundPar = false, found = false;
        for (MonitoredTaskInterceptor.Invocation next : list) {
            final String fullParametrized = MonitorTest.TASK_NME + "." + param;
            if (fullParametrized.equals(next.name()))
                foundPar = true;

            if (MonitorTest.TASK_NME .equals(next.name()))
                found  = true;
        }

        assertTrue(foundPar);
        assertTrue(found);
    }

    public static class MonitorTest {

        public static final String TASK_NME = "test";

        @MonitoredTask(name = TASK_NME, nameExtArgIndex=0)
        public void doSmth(String parameter) {}


        @MonitoredTask(name = TASK_NME)
        public void doSmth() {}
    }

    @Test
    public void checkSingletonTcHelper() {
        Injector injector = getInjector();

        validateInstanceCachedFor(injector, ITcHelper.class);

        validateInstanceCachedFor(injector, BuildObserver.class).stop();
    }
}
