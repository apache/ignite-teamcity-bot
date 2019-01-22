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

package org.apache.ignite.ci;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.UserAndSessionsStorage;

/**
 * TC Bot implementation. To be migrated to smaller injected classes
 */
@Deprecated
public class TcHelper implements ITcHelper {
    /** Server authorizer credentials. */
    private ICredentialsProv serverAuthorizerCreds;

    @Inject private UserAndSessionsStorage userAndSessionsStorage;


    /** {@inheritDoc} */
    @Override public void setServerAuthorizerCreds(ICredentialsProv creds) {
        this.serverAuthorizerCreds = creds;
    }

    /** {@inheritDoc} */
    @Override public ICredentialsProv getServerAuthorizerCreds() {
        return serverAuthorizerCreds;
    }

    /** {@inheritDoc} */
    @Override public boolean isServerAuthorized() {
        return !Objects.isNull(serverAuthorizerCreds);
    }

    /** {@inheritDoc} */
    @Override public UserAndSessionsStorage users() {
        return userAndSessionsStorage;
    }

    @Override public String primaryServerId() {
        return "apache"; //todo remove
    }
}
