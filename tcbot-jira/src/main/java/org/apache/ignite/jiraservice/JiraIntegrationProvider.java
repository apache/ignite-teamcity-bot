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

package org.apache.ignite.jiraservice;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Provider;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;

/**
 * Class for providing {@link IJiraIntegration} instance for specified Jira server. Instance for each server is cached
 * with defining instance expiration time.
 */
class JiraIntegrationProvider implements IJiraIntegrationProvider {
    /** */
    @Inject Provider<IJiraIntegration> factory;

    /** */
    private final Cache<String, IJiraIntegration> srvs
        = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(16, TimeUnit.MINUTES)
        .softValues()
        .build();

    /** */
    @Override public IJiraIntegration server(String srvCode) {
        try {
            return srvs.get(Strings.nullToEmpty(srvCode), () -> {
                IJiraIntegration jiraIntegration = factory.get();

                jiraIntegration.init(srvCode);

                return jiraIntegration;
            });
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }
}
