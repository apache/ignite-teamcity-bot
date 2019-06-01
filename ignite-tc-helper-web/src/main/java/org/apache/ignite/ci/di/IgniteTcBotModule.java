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
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import com.google.inject.matcher.Matchers;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.Ignite1Init;
import org.apache.ignite.ci.di.cache.GuavaCachedModule;
import org.apache.ignite.ci.di.scheduler.SchedulerModule;
import org.apache.ignite.ci.github.ignited.GitHubIgnitedModule;
import org.apache.ignite.ci.jira.ignited.JiraIgnitedModule;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.observer.ObserverTask;
import org.apache.ignite.ci.tcbot.TcBotBusinessServicesModule;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedModule;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.apache.ignite.ci.web.rest.exception.ServiceStartingException;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;

/**
 *
 */
public class IgniteTcBotModule extends AbstractModule {
    /** Ignite future. */
    private Future<Ignite> igniteFut;

    /** {@inheritDoc} */
    @Override protected void configure() {
        install(new GuavaCachedModule());
        configProfiling();
        configTaskMonitor();

        bind(Ignite.class).toProvider((Provider<Ignite>)() -> {
            Preconditions.checkNotNull(igniteFut, "Ignite future is not yet initialized");

            try {
                return igniteFut.get(10, TimeUnit.SECONDS);
            }
            catch (TimeoutException e) {
                throw new ServiceStartingException(e);
            }
            catch (Exception e) {
                e.printStackTrace();

                throw ExceptionUtil.propagateException(e);
            }
        });

        bind(TcUpdatePool.class).in(new SingletonScope());
        bind(IssueDetector.class).in(new SingletonScope());
        bind(ObserverTask.class).in(new SingletonScope());
        bind(BuildObserver.class).in(new SingletonScope());
        bind(VisasHistoryStorage.class).in(new SingletonScope());

        install(new TeamcityIgnitedModule());
        install(new JiraIgnitedModule());
        install(new GitHubIgnitedModule());
        install(new SchedulerModule());
        install(new TcBotBusinessServicesModule());
    }

    private void configProfiling() {
        AutoProfilingInterceptor profilingInterceptor = new AutoProfilingInterceptor();

        bindInterceptor(Matchers.any(),
            Matchers.annotatedWith(AutoProfiling.class),
            profilingInterceptor);

        bind(AutoProfilingInterceptor.class).toInstance(profilingInterceptor);
    }

    private void configTaskMonitor() {
        MonitoredTaskInterceptor profilingInterceptor = new MonitoredTaskInterceptor();

        bindInterceptor(Matchers.any(),
            Matchers.annotatedWith(MonitoredTask.class),
            profilingInterceptor);

        bind(MonitoredTaskInterceptor.class).toInstance(profilingInterceptor);
    }

    public void setIgniteFut(Future<Ignite> igniteFut) {
        this.igniteFut = igniteFut;
    }

    public Injector startIgniteInit(Injector injector) {
        final Ignite1Init instance = injector.getInstance(Ignite1Init.class);
        final Future<Ignite> submit = instance.getIgniteFuture();
        setIgniteFut(submit);

        return injector;
    }

}
