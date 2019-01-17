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

import com.google.common.base.Preconditions;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.pure.IGitHubConnection;

/**
 *
 */
public interface IGitHubConnIgnited {
    /**
     * @return list of open pull requests
     */
    public List<PullRequest> getPullRequests();

    /** */
    public PullRequest getPullRequest(int prNum);

    /**
     * Send POST request with given body.
     *
     * @param url Url.
     * @param body Request body.
     * @return {@code True} - if GitHub was notified. {@code False} - otherwise.
     */
    void notifyGit(String url, String body);
}
