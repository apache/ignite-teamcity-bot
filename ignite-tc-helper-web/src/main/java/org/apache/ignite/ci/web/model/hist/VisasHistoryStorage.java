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

package org.apache.ignite.ci.web.model.hist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.web.model.CompactContributionKey;
import org.apache.ignite.ci.web.model.CompactVisaRequest;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.VisaRequest;

/**
 *
 */
public class VisasHistoryStorage {
    /** */
    private static final String VISAS_CACHE_NAME = "compactVisasHistoryCacheV2";

    /** */
    @Inject
    private IStringCompactor strCompactor;

    /** */
    @Inject
    private Ignite ignite;

    /** */
    public void clear() {
        visas().clear();
    }

    /** */
    private Cache<CompactContributionKey, List<CompactVisaRequest>> visas() {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(VISAS_CACHE_NAME));
    }

    /** */
    public void put(VisaRequest visaReq) {
        CompactVisaRequest compactVisaReq = new CompactVisaRequest(visaReq, strCompactor);

        CompactContributionKey key = new CompactContributionKey(new ContributionKey(
            visaReq.getInfo().srvId,
            visaReq.getInfo().branchForTc), strCompactor);

        visas().invoke(key, (entry, arguments) -> {
            List<CompactVisaRequest> contributionVisas = entry.getValue();

            if (contributionVisas == null)
                contributionVisas = new ArrayList<>();

            contributionVisas.add(compactVisaReq);

            entry.setValue(contributionVisas);

            return contributionVisas;
        });
    }

    /** */
    public List<VisaRequest> getVisaRequests(ContributionKey key) {
        return visas().get(new CompactContributionKey(key, strCompactor)).stream()
            .map(compactVisaReq -> compactVisaReq.toVisaRequest(strCompactor))
            .collect(Collectors.toList());
    }

    /** */
    public VisaRequest getLastVisaRequest(ContributionKey key) {
        List<VisaRequest> reqs = getVisaRequests(key);

        if (Objects.isNull(reqs))
            return null;

        return reqs.get(reqs.size() - 1);
    }

    /** */
    public boolean updateLastVisaRequest(ContributionKey key, Consumer<VisaRequest> updater) {
        CompactContributionKey compactKey = new CompactContributionKey(key, strCompactor);

        if (!visas().containsKey(compactKey))
            return false;

        visas().invoke(compactKey, (entry, arguments) -> {
            List<CompactVisaRequest> compactReqs = entry.getValue();

            int lastIdx = compactReqs.size() - 1;

            VisaRequest req = compactReqs.get(lastIdx).toVisaRequest(strCompactor);

            updater.accept(req);

            compactReqs.set(lastIdx, new CompactVisaRequest(req, strCompactor));

            entry.setValue(compactReqs);

            return compactReqs;
        });

        return true;
    }

    /** */
    public Collection<VisaRequest> getLastVisas() {
        List<VisaRequest> res = new ArrayList<>();

        visas().forEach(entry -> {
            int lastIdx = entry.getValue().size() - 1;

            res.add(entry.getValue().get(lastIdx).toVisaRequest(strCompactor));
        });

        return Collections.unmodifiableCollection(res);
    }

    /** */
    public Collection<VisaRequest> getVisas() {
        List<VisaRequest> res = new ArrayList<>();

        visas().forEach(entry -> res.addAll(entry.getValue().stream()
            .map(v -> v.toVisaRequest(strCompactor))
            .collect(Collectors.toList())));

        return Collections.unmodifiableCollection(res);
    }
}
