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
import org.apache.ignite.tcservice.model.vcs.Revision;
import org.apache.ignite.tcservice.model.vcs.VcsRootInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compacted version of {@link Revision} and its aggregated classes fields.
 */
@Persisted
public class RevisionCompacted implements IVersionedEntity {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(FatBuildDao.class);

    /** Latest version. */
    private static final int LATEST_VERSION = 1;

    /** Entity fields version. */
    @SuppressWarnings("FieldCanBeLocal")
    private short _ver = LATEST_VERSION;

    /** Version: For Git revision, 20 bytes. */
    @Nullable private byte[] version;

    /** VCS branch (compactor style compacted) Name. */
    private int vcsBranchName = -1;

    /** Vcs root (compactor style compacted) ID. */
    private int vcsRootId = -1;

    /** Vcs root instance id. */
    private int vcsRootInstanceId = -1;

    /**
     * @param compactor Compactor.
     * @param revision Revision.
     */
    public RevisionCompacted(IStringCompactor compactor, Revision revision) {
        VcsRootInstance vcsRootInstance = revision.vcsRootInstance();
        if (vcsRootInstance != null) {
            vcsRootId = compactor.getStringId(vcsRootInstance.vcsRootId());
            vcsRootInstanceId = vcsRootInstance.id() != null ? vcsRootInstance.id() : -1;
        }
        vcsBranchName = compactor.getStringId(revision.vcsBranchName());

        String ver = revision.version();

        if (!Strings.isNullOrEmpty(ver) && !"N/A".equals(ver)) {
            try {
                this.version = DatatypeConverter.parseHexBinary(ver);
            }
            catch (Exception e) {
                logger.error("TC Change version parse failed " + ver + ":" + e.getMessage(), e);
            }
        }

        vcsBranchName = compactor.getStringId(revision.vcsBranchName());

    }

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public String vcsBranchName(IStringCompactor compactor) {
        return compactor.getStringFromId(vcsBranchName);
    }

    public String vcsRootId(IStringCompactor compactor) {
        return compactor.getStringFromId(vcsRootId);
    }

    public Integer vcsRootInstanceId() {
        return vcsRootInstanceId < 0 ? null : vcsRootInstanceId;
    }

    /**
     *
     */
    public String commitFullVersion() {
        if (version == null)
            return "";

        return DatatypeConverter.printHexBinary(version).toLowerCase();
    }

    /**
     *
     */
    public byte[] revision() {
        return version;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RevisionCompacted compacted = (RevisionCompacted)o;
        return _ver == compacted._ver &&
            vcsBranchName == compacted.vcsBranchName &&
            vcsRootId == compacted.vcsRootId &&
            vcsRootInstanceId == compacted.vcsRootInstanceId &&
            Arrays.equals(version, compacted.version);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = Objects.hash(_ver, vcsBranchName, vcsRootId, vcsRootInstanceId);
        res = 31 * res + Arrays.hashCode(version);
        return res;
    }
}
