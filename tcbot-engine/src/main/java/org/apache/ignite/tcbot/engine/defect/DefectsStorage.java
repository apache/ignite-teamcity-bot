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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

@NotThreadSafe
public class DefectsStorage {
    /** Bot detected defects. */
    public static final String BOT_DETECTED_DEFECTS = "botDetectedDefects";
    /** Bot detected defects sequence. */
    public static final String BOT_DETECTED_DEFECTS_SEQ = "botDetectedDefectsSeq";

    @Inject
    private Provider<Ignite> igniteProvider;
    @Inject
    private ChangeDao changeDao;


    public DefectsStorage() {
    }

    private IgniteAtomicSequence sequence() {
        return getIgnite().atomicSequence(BOT_DETECTED_DEFECTS_SEQ, 0, true);
    }

    private IgniteCache<Integer, DefectCompacted> cache() {
        return botDetectedIssuesCache(getIgnite());
    }

    private Ignite getIgnite() {
        return igniteProvider.get();
    }

    public static IgniteCache<Integer, DefectCompacted> botDetectedIssuesCache(Ignite ignite) {
        CacheConfiguration<Integer, DefectCompacted> ccfg = CacheConfigs.getCacheV2TxConfig(BOT_DETECTED_DEFECTS);

        ccfg.setQueryEntities(Collections.singletonList(new QueryEntity(Integer.class, DefectCompacted.class)));

        return ignite.getOrCreateCache(ccfg);
    }

    public DefectCompacted merge(
        int tcSrvCodeCid,
        final int srvId,
        FatBuildCompacted fatBuild,
        BiFunction<Integer, DefectCompacted, DefectCompacted> function) {

        IgniteCache<Integer, DefectCompacted> cache = cache();

        try (QueryCursor<Cache.Entry<Integer, DefectCompacted>> qry = cache.query(new ScanQuery<Integer, DefectCompacted>()
            .setFilter((k, v) -> v.tcSrvId() == srvId))) {
            //here we ignore if issue was resolved or not because defect can be already resolved,
            // and if this(resolved) defect contains same build ID, as we've used earlier, no reason to open new defect for it.
            for (Cache.Entry<Integer, DefectCompacted> next : qry) {
                DefectCompacted openDefect = next.getValue();

                if (openDefect.hasBuild(fatBuild.id()))
                    return processExisting(function, cache, next.getKey(), openDefect);
            }
        }

        int[] changes = fatBuild.changes();
        Map<Integer, ChangeCompacted> changeList = changeDao.getAll(srvId, changes);

        List<CommitCompacted> commitsToUse = changeList
            .values()
            .stream()
            .map(ChangeCompacted::commitVersion)
            .map(CommitCompacted::new)
            .sorted(CommitCompacted::compareTo)
            .collect(Collectors.toList());

        try (QueryCursor<Cache.Entry<Integer, DefectCompacted>> qry = cache.query(new ScanQuery<Integer, DefectCompacted>()
            .setFilter((k, v) -> v.resolvedByUsernameId() < 1 && v.tcSrvId() == srvId))) {
            for (Cache.Entry<Integer, DefectCompacted> next : qry) {
                DefectCompacted openDefect = next.getValue();

                if (openDefect.sameCommits(commitsToUse))
                    return processExisting(function, cache, next.getKey(), openDefect);
            }
        }

        int id = (int)sequence().incrementAndGet();
        if (id == 0)
            id = (int)sequence().incrementAndGet();

        DefectCompacted defect = new DefectCompacted(id)
            .commits(commitsToUse)
            .changeMap(changeList)
            .tcBranch(fatBuild.branchName())
            .tcSrvId(srvId)
            .tcSrvCodeCid(tcSrvCodeCid);

        DefectCompacted defectT = function.apply(id, defect);

        boolean putSuccess = cache.putIfAbsent(id, defectT);

        return defectT;
    }

    public DefectCompacted processExisting(BiFunction<Integer, DefectCompacted, DefectCompacted> function,
                                           IgniteCache<Integer, DefectCompacted> cache, Integer id, DefectCompacted openDefect) {
        DefectCompacted defect = function.apply(id, openDefect);

        defect.id(id);

        DefectCompacted existingDefect = load(id);

        if (existingDefect == null || !existingDefect.equals(defect))
            save(defect);

        return defect;
    }

    public List<DefectCompacted> loadAllDefects() {
        List<DefectCompacted> res = new ArrayList<>();
        try (QueryCursor<Cache.Entry<Integer, DefectCompacted>> qry = cache().query(new ScanQuery<Integer, DefectCompacted>()
                .setFilter((k, v) -> v.resolvedByUsernameId() < 1))) {
            for (Cache.Entry<Integer, DefectCompacted> next : qry) {
                DefectCompacted openDefect = next.getValue();
                openDefect.id(next.getKey());
                res.add(openDefect);
            }
        }
        return res;
    }

    public DefectCompacted load(Integer defectId) {
        DefectCompacted defectCompacted = cache().get(defectId);
        if (defectCompacted != null && defectCompacted.id() == 0)
            defectCompacted.id(defectId);

        return defectCompacted;
    }

    public void save(DefectCompacted defect) {
        Preconditions.checkState(defect.id() != 0);
        cache().put(defect.id(), defect);
    }
}
