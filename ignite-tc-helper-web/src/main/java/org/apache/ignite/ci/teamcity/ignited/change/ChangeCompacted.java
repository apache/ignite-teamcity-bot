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
package org.apache.ignite.ci.teamcity.ignited.change;

import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Persisted
public class ChangeCompacted implements IVersionedEntity {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(FatBuildDao.class);

    /** Latest version. */
    private static final int LATEST_VERSION = 1;

    /** Entity fields version. */
    private short _ver = LATEST_VERSION;

    /**
     * Change Id, -1 if it is null.
     */
    private int id = -1;
    private int vcsUsername = -1;
    private int tcUserId = -1;
    private int tcUserUsername = -1;
    private int tcUserFullname = -1;

    public ChangeCompacted(IStringCompactor compactor, Change change) {
        id = compactor.getStringId(change.id);
        vcsUsername = compactor.getStringId(change.username);

        if (change.user != null) {
            try {
                tcUserId = Integer.parseInt(change.user.id);
            } catch (NumberFormatException e) {
                logger.error("TC User id parse failed " + change.user.id, e);
            }
            tcUserUsername = compactor.getStringId(change.user.username);
            tcUserFullname = compactor.getStringId(change.user.name);
        }

    }

    public int id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public boolean hasUsername() {
        return vcsUsername>0;
    }

    public String vcsUsername(IStringCompactor compactor) {
        return compactor.getStringFromId(vcsUsername);
    }

    public String tcUserFullName(IStringCompactor compactor) {
        return compactor.getStringFromId(tcUserFullname);
    }
}
