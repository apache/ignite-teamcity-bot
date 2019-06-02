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

package org.apache.ignite.ci.web.model;

import java.util.Objects;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

/**
 * Class which is used as a key to determine Server and TC branch info.
 */
public class ContributionKey {
    /** */
    public final String srvId;

    /** */
    public final String branchForTc;

    /** */
    public ContributionKey(String srvId, String branchForTc) {
        this.branchForTc = branchForTc;
        this.srvId = srvId;
    }

    /** */
    public ContributionKey(CompactContributionKey key, IStringCompactor strCompactor) {
        this.branchForTc = strCompactor.getStringFromId(key.branchForTc);
        this.srvId = strCompactor.getStringFromId(key.srvId);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "{srv: " + this.srvId + " branch: " + this.branchForTc + '}';
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof ContributionKey))
            return false;

        ContributionKey key = (ContributionKey)o;

        return Objects.equals(srvId, key.srvId) &&
            Objects.equals(branchForTc, key.branchForTc);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(srvId, branchForTc);
    }
}
