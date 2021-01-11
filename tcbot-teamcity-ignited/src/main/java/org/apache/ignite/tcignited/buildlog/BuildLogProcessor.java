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
package org.apache.ignite.tcignited.buildlog;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class BuildLogProcessor implements IBuildLogProcessor {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BuildLogProcessor.class);

    @Inject
    private Provider<LogCheckTask> taskProvider;

    @Inject
    private BuildLogCheckResultDao logCheckResultDao;

    /** Non persistence cache for log check results. */
    private final Cache<Long, ILogCheckResult> logCheckResultCache
            = CacheBuilder.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .softValues()
            .build();


    @Override
    public ILogCheckResult analyzeBuildLog(ITeamcityIgnited teamcity, int buildId, boolean dumpLastTest) {
        long cacheKey = BuildLogCheckResultDao.getCacheKey(teamcity.serverCode(), buildId);

        try {
            return logCheckResultCache.get(cacheKey, () -> {
                LogCheckResultCompacted val = logCheckResultDao.get(teamcity.serverCode(), buildId);
                if (val != null)
                    return val;

                LogCheckResultCompacted logCheckResultCompacted;
                try {
                    logCheckResultCompacted = checkBuildLogNoCache(teamcity, buildId, dumpLastTest);
                } catch (Exception e)   {
                    final String msg = "Failed to process Build Log entry " + teamcity.serverCode() + "/" + buildId ;

                    System.err.println(msg + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());

                    logger.error(msg, e);

                    //protecting from retrying for invalid build log.
                    logCheckResultCompacted = new LogCheckResultCompacted();
                }

                try {
                    logCheckResultDao.put(teamcity.serverCode(), buildId, logCheckResultCompacted);
                }
                catch (Exception ex) {
                    logger.error("serverCode: " + teamcity.serverCode() + "; buildId: " + buildId +
                        "; logCheck: " + logCheckResultCompacted.toString());
                    throw ex;
                }

                return logCheckResultCompacted;
            });
        } catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }

    @Nullable
    @Override
    public String getThreadDumpCached(String serverCode, Integer buildId) {
        LogCheckResultCompacted logCheckResultCompacted = logCheckResultDao.get(serverCode, buildId);

        if(logCheckResultCompacted==null)
            return null;

        return logCheckResultCompacted.getLastThreadDump();
    }


    private LogCheckResultCompacted checkBuildLogNoCache(ITeamcityIgnited teamcity, int buildId, boolean dumpLastTest) throws IOException {
        File zipFile = teamcity.downloadAndCacheBuildLog(buildId);

        if (zipFile == null)
            return null;

        return runCheckForZippedLog(dumpLastTest, zipFile);
    }


    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    @Nullable
    protected LogCheckResultCompacted runCheckForZippedLog(boolean dumpLastTest, File zipFile) throws IOException {
        LogCheckTask task = taskProvider.get();


            //get the zip file content
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry ze = zis.getNextEntry();    //get the zipped file list entry

                while (ze != null) {
                    BuildLogStreamChecker checker = task.createChecker();
                    checker.apply(zis, zipFile);
                    LogCheckResultCompacted finalize = task.finalize(dumpLastTest);
                    if (finalize != null)
                        return finalize;

                    ze = zis.getNextEntry();
                }
                zis.closeEntry();
            }


        return null;
    }
}
