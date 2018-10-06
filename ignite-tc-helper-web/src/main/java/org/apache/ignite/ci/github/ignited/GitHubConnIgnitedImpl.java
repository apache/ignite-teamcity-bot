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
package org.apache.ignite.ci.github.ignited;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.NotNull;

class GitHubConnIgnitedImpl implements IGitHubConnIgnited {
    private String id;
    private IGitHubConnection conn;

    @Inject Provider<Ignite> igniteProvider;

    /** Data loaded. */
    private final Cache<String, List<PullRequest>> prListCache
        = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .softValues()
        .build();

    public void init(String srvId, IGitHubConnection conn) {
        id = srvId;
        this.conn = conn;
    }

    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCache8PartsConfig(String name) {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(name);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 8));

        return ccfg;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<PullRequest> getPullRequests() {
        try {
            return prListCache.get("", conn::getPullRequests);
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }
}
