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
 * Representation of {@link ContributionKey} with compacted properties for more effective cache storing.
 */
public class CompactContributionKey {
    /** */
    public final int srvId;

    /** */
    public final int branchForTc;

    /** */
    public CompactContributionKey(ContributionKey key, IStringCompactor strCompactor) {
        this.branchForTc = strCompactor.getStringId(key.branchForTc);
        this.srvId = strCompactor.getStringId(key.srvId);
    }

    /** */
    public ContributionKey toContributionKey(IStringCompactor strCompactor) {
        return new ContributionKey(this, strCompactor);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof CompactContributionKey))
            return false;

        CompactContributionKey key = (CompactContributionKey)o;

        return Objects.equals(srvId, key.srvId) &&
            Objects.equals(branchForTc, key.branchForTc);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(srvId, branchForTc);
    }
}
