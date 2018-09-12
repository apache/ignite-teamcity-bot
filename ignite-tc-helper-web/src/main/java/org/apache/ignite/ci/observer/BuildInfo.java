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

package org.apache.ignite.ci.observer;

import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;

/**
 *
 */
class BuildInfo {
    /** Build. */
    final Build build;

    /** Server id. */
    final String srvId;

    /** */
    final ICredentialsProv prov;

    /** JIRA ticket name. */
    final String ticket;

    /**
     * @param build Build.
     * @param srvId Server id.
     * @param prov Credentials.
     * @param ticket JIRA ticket name.
     */
    BuildInfo(Build build, String srvId, ICredentialsProv prov, String ticket) {
        this.build = build;
        this.srvId = srvId;
        this.prov = prov;
        this.ticket = ticket;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        BuildInfo info = (BuildInfo)o;

        if (!build.equals(info.build))
            return false;
        if (!srvId.equals(info.srvId))
            return false;
        if (!prov.equals(info.prov))
            return false;

        return ticket.equals(info.ticket);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = build.hashCode();

        res = 31 * res + srvId.hashCode();
        res = 31 * res + prov.hashCode();
        res = 31 * res + ticket.hashCode();

        return res;
    }
}
