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

import com.google.common.base.Throwables;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcservice.model.changes.Change;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcservice.ITeamcityConn;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.FileNotFoundException;

public class ChangeSync {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ChangeSync.class);

    /** Changes DAO. */
    @Inject private ChangeDao changeDao;

    @Inject private IStringCompactor compactor;

    public ChangeCompacted change(int srvId, int changeId, ITeamcityConn conn) {
        final ChangeCompacted load = changeDao.load(srvId, changeId);

        if (load != null && !load.isOutdatedEntityVersion())
            return load;

        return reloadChange(srvId, changeId, conn);
    }

    @Nonnull
    @AutoProfiling
    public ChangeCompacted reloadChange(int srvId, int changeId, ITeamcityConn conn) {
        Change change;
        try {
            change = conn.getChange(changeId);
        } catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                logger.info("Loading changeId [" + changeId + "] for server [" + conn.serverCode() + "] failed:" + e.getMessage(), e);

                change = new Change();
            } else if (Throwables.getRootCause(e) instanceof SAXParseException) {
                System.err.println("Change data seems to be invalid: [" + changeId + "]");

                //can re-queue this change without details
                change = new Change();
                change.id = Integer.toString(changeId);
            } else
                throw ExceptionUtil.propagateException(e);
        }

        final ChangeCompacted changeCompacted = new ChangeCompacted(compactor, change);

        changeDao.save(srvId, changeId, changeCompacted);

        return changeCompacted;
    }
}
