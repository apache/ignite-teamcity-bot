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
package org.apache.ignite.ci.teamcity.ignited;

import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;

/**
 *
 */
@Persisted
public class FatBuildCompacted extends BuildRefCompacted implements IVersionedEntity {
    /** Latest version. */
    private static final int LATEST_VERSION = 0;

    /** Entity fields version. */
    private short _ver;

    /** Start date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long startDate;

    /** Finish date. The number of milliseconds since January 1, 1970, 00:00:00 GMT */
    private long finishDate;

    private int projectId = -1;
    private int name = -1;

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    /**
     * Default constructor.
     */
    public FatBuildCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param ref Reference.
     */
    public FatBuildCompacted(IStringCompactor compactor, Build ref) {
        super(compactor, ref);

        startDate = ref.getStartDate() == null ? -1L : ref.getStartDate().getTime();
        finishDate = ref.getFinishDate() == null ? -1L : ref.getFinishDate().getTime();

        BuildType type = ref.getBuildType();
        if (type != null) {
            projectId = compactor.getStringId(type.getProjectId());
            name = compactor.getStringId(type.getName());
        }
    }

    /**
     * @param compactor Compacter.
     */
    public Build toBuild(IStringCompactor compactor) {
        Build res = new Build();

        fillBuildRefFields(compactor, res);

        fillBuildFields(compactor, res);

        return res;
    }

    /**
     * @param compactor Compactor.
     * @param res Response.
     */
    private void fillBuildFields(IStringCompactor compactor, Build res) {
        if (startDate > 0)
            res.setStartDateTs(startDate);

        if (finishDate > 0)
            res.setFinishDateTs(finishDate);

        BuildType type = new BuildType();
        type.id(res.buildTypeId());
        type.name(compactor.getStringFromId(name));
        type.projectId(compactor.getStringFromId(projectId));
        res.setBuildType(type);

        res.href = "/app/rest/latest/builds/id:" + id();
    }

}
