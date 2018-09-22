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
import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.web.rest.exception.ServiceStartingException;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class IgniteTcBotModule extends AbstractModule {
    private Future<Ignite> igniteFuture;

    /** {@inheritDoc} */
    @Override
    protected void configure() {
        configProfiling();
        configTaskMonitor();

        bind(Ignite.class).toProvider((Provider<Ignite>) () -> {
            try {
                return igniteFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new ServiceStartingException(e);
            } catch (Exception e) {
                e.printStackTrace();

                throw ExceptionUtil.propagateException(e);
            }
        });

        //Simple connection
        bind(ITeamcity.class).to(IgniteTeamcityHelper.class);
        //With REST persistence
        bind(IAnalyticsEnabledTeamcity.class).to(IgnitePersistentTeamcity.class);
        bind(IServerProv.class).toInstance(
                new MyIServerProv()
        );
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

    public void setIgniteFuture(Future<Ignite> igniteFuture) {
        this.igniteFuture = igniteFuture;
    }

    private static class MyIServerProv implements IServerProv {
        @Inject
        Provider<IAnalyticsEnabledTeamcity> tcPersistProv;

        @Inject
        Provider<ITeamcity> tcConnProv;

        @Override
        public IAnalyticsEnabledTeamcity createServer(String serverId) {
            ITeamcity tcConn = tcConnProv.get();
            tcConn.init(Strings.emptyToNull(serverId));

            IAnalyticsEnabledTeamcity instance = tcPersistProv.get();
            instance.init(tcConn);

            return instance;
        }
    }
}
