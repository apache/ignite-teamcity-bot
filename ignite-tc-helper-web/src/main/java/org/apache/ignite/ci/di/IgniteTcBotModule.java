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
import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import com.google.inject.matcher.Matchers;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.*;
import org.apache.ignite.ci.db.Ignite1Init;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.observer.ObserverTask;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.apache.ignite.ci.web.rest.exception.ServiceStartingException;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.*;

public class IgniteTcBotModule extends AbstractModule {
    /** Ignite future. */
    private Future<Ignite> igniteFut;

    /** {@inheritDoc} */
    @Override protected void configure() {
        configProfiling();
        configTaskMonitor();

        bind(Ignite.class).toProvider((Provider<Ignite>) () -> {
            Preconditions.checkNotNull(igniteFut, "Ignite future is not yet initialized");

            try {
                return igniteFut.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new ServiceStartingException(e);
            } catch (Exception e) {
                e.printStackTrace();

                throw ExceptionUtil.propagateException(e);
            }
        });

        //Simple connection
        bind(ITeamcity.class).to(IgniteTeamcityConnection.class);
        //With REST persistence
        bind(IAnalyticsEnabledTeamcity.class).to(IgnitePersistentTeamcity.class);
        bind(ITcServerFactory.class).to(InitializingServerFactory.class).in(new SingletonScope());
        bind(ITcServerProvider.class).to(TcServerCachingProvider.class).in(new SingletonScope());
        bind(TcUpdatePool.class).in(new SingletonScope());
       //todo bind(IssueDetector.clas)
        bind(ObserverTask.class).in(new SingletonScope());
        bind(BuildObserver.class).in(new SingletonScope());
        bind(ITcHelper.class).to(TcHelper.class).in(new SingletonScope());

        bind(IJiraIntegration.class).to(Jira.class).in(new SingletonScope());
    }

    //todo fallback to TC big class
    private static class Jira implements IJiraIntegration {
        @Inject ITcHelper helper;

        @Override public boolean notifyJira(String srvId, ICredentialsProv prov, String buildTypeId, String branchForTc,
            String ticket) {
            return helper.notifyJira(srvId, prov, buildTypeId, branchForTc, ticket);
        }
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

    private static class InitializingServerFactory implements ITcServerFactory {
        @Inject
        Provider<IAnalyticsEnabledTeamcity> tcPersistProv;

        @Inject
        Provider<ITeamcity> tcConnProv;

        @Inject
        private TcUpdatePool tcUpdatePool;

        /** {@inheritDoc} */
        @Override public IAnalyticsEnabledTeamcity createServer(String srvId) {
            ITeamcity tcConn = tcConnProv.get();
            tcConn.init(Strings.emptyToNull(srvId));

            IAnalyticsEnabledTeamcity instance = tcPersistProv.get();
            instance.init(tcConn);

            instance.setExecutor(tcUpdatePool.getService());

            return instance;
        }
    }
}
