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
package org.apache.ignite.githubignited;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.githubservice.IGitHubConnectionProvider;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;

class GitHubIgnitedProvImpl implements IGitHubConnIgnitedProvider {
    @Inject IGitHubConnectionProvider pureConnProv;
    @Inject Provider<GitHubConnIgnitedImpl> ignitedProvider;

    private final Cache<String, IGitHubConnIgnited> srvs
        = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(60, TimeUnit.MINUTES)
        //.softValues()
        .build();

    /** {@inheritDoc} */
    @Override public IGitHubConnIgnited server(String srvCode) {
        try {
            return srvs.get(Strings.nullToEmpty(srvCode), () -> {
                IGitHubConnection srv = pureConnProv.server(srvCode);

                Preconditions.checkState(srv.config().code().equals(srvCode));

                GitHubConnIgnitedImpl ignited = ignitedProvider.get();
                ignited.init(srv);

                Preconditions.checkState(ignited.config().code().equals(srvCode));
                return ignited;
            });
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }

}
