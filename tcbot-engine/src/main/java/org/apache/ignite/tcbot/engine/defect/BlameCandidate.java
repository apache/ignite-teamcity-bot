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
package org.apache.ignite.tcbot.engine.defect;

import java.util.Objects;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.IVersionedEntity;
import org.apache.ignite.tcbot.persistence.Persisted;

@Persisted
public class BlameCandidate  implements IVersionedEntity {
    /** Latest version. */
    private static final int LATEST_VERSION = 2;

    /** Entity fields version. */
    @SuppressWarnings("FieldCanBeLocal")
    private short _ver = LATEST_VERSION;

    private int vcsUsername = IStringCompactor.STRING_NULL;
    /**
     * Tc helper username/login, optional - it may be missed because TC user is not detected/user not present in TC
     * Bot.
     */
    private int tcHelperUsername = IStringCompactor.STRING_NULL;
    /** Full display name for user, may be taken from Teamcity change, or prefferred from the TC Bot user. */
    private int fullDisplayName = IStringCompactor.STRING_NULL;


    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public void vcsUsername(int username) {
        vcsUsername = username;
    }

    public String vcsUsername(IStringCompactor compactor) {
        return compactor.getStringFromId(vcsUsername);
    }

    public String fullDisplayName(IStringCompactor compactor) {
        return compactor.getStringFromId(fullDisplayName);
    }

    public void tcHelperUserUsername(int tcHelperUsername) {
        this.tcHelperUsername = tcHelperUsername;
    }

    public void fullDisplayName(int fullDisplayName) {
        this.fullDisplayName = fullDisplayName;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BlameCandidate candidate = (BlameCandidate)o;
        return _ver == candidate._ver &&
            vcsUsername == candidate.vcsUsername &&
            tcHelperUsername == candidate.tcHelperUsername &&
            fullDisplayName == candidate.fullDisplayName;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(_ver, vcsUsername, tcHelperUsername, fullDisplayName);
    }
}
