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

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.xml.bind.DatatypeConverter;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.IVersionedEntity;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcservice.model.changes.Change;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Persisted
public class ChangeCompacted implements IVersionedEntity {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(FatBuildDao.class);

    /** Latest version. */
    private static final int LATEST_VERSION = 4;

    /** Entity fields version. */
    @SuppressWarnings("FieldCanBeLocal")
    private short _ver = LATEST_VERSION;

    /**
     * Change Id, -1 if it is null.
     */
    private int id = -1;
    private int vcsUsername = -1;
    private int tcUserId = -1;
    private int tcUserUsername = -1;
    private int tcUserFullname = -1;

    /** Version: For Git revision, 20 bytes. */
    @Nullable private byte[] version;

    /** Date timestamp. */
    private long date;

    public ChangeCompacted(IStringCompactor compactor, Change change) {
        try {
            id = Integer.parseInt(change.id);
        }
        catch (NumberFormatException e) {
            logger.error("Change ID parse failed " + change.id + ":" + e.getMessage(), e);
        }
        vcsUsername = compactor.getStringId(change.username);

        if (change.user != null) {
            try {
                tcUserId = Integer.parseInt(change.user.id);
            }
            catch (NumberFormatException e) {
                logger.error("TC User id parse failed " + change.user.id + ":" + e.getMessage(), e);
            }
            tcUserUsername = compactor.getStringId(change.user.username);
            tcUserFullname = compactor.getStringId(change.user.name);
        }

        if (!Strings.isNullOrEmpty(change.version)) {
            try {
                version = DatatypeConverter.parseHexBinary(change.version);
            }
            catch (Exception e) {
                logger.error("TC Change version parse failed " + change.version + ":" + e.getMessage(), e);
            }
        }

        try {
            Long date = change.getDateTs();

            this.date = date == null ? -1L : date;
        }
        catch (Exception e) {
            logger.error("TC Change date parse failed " + change.date + ":" + e.getMessage(), e);
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

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChangeCompacted compacted = (ChangeCompacted)o;
        return _ver == compacted._ver &&
            id == compacted.id &&
            vcsUsername == compacted.vcsUsername &&
            tcUserId == compacted.tcUserId &&
            tcUserUsername == compacted.tcUserUsername &&
            tcUserFullname == compacted.tcUserFullname &&
            date == compacted.date &&
            Arrays.equals(version, compacted.version);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = Objects.hash(_ver, id, vcsUsername, tcUserId, tcUserUsername, tcUserFullname, date);
        res = 31 * res + Arrays.hashCode(version);
        return res;
    }

    /**
     *
     */
    public String commitFullVersion() {
        return DatatypeConverter.printHexBinary(version).toLowerCase();
    }

    /**
     *
     */
    public byte[] commitVersion() {
        return version;
    }

    public int vcsUsername() {
        return vcsUsername;
    }

    public int tcUserUsername() {
        return tcUserUsername;
    }
}
