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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.web.model.CompactContributionKey;
import org.apache.ignite.ci.web.model.CompactVisaRequest;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.VisaRequest;

/**
 *
 */
public class VisasHistoryStorage {
    /** */
    private static final String VISAS_CACHE_NAME = "visasCompactCache";

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
    private Cache<CompactContributionKey, Map<Date, CompactVisaRequest>> visas() {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(VISAS_CACHE_NAME));
    }

    /** */
    public void put(VisaRequest visaReq) {
        CompactVisaRequest compactVisaReq = new CompactVisaRequest(visaReq, strCompactor);

        CompactContributionKey key = new CompactContributionKey(new ContributionKey(
            visaReq.getInfo().srvId,
            visaReq.getInfo().ticket,
            visaReq.getInfo().branchForTc), strCompactor);

        Map<Date, CompactVisaRequest> contributionVisas = visas().get(key);

        if (contributionVisas == null)
            contributionVisas = new HashMap<>();

        contributionVisas.put(compactVisaReq.compactInfo.date, compactVisaReq);

        visas().put(key, contributionVisas);
    }

    /** */
    public VisaRequest getVisaReq(ContributionKey key, Date date) {
        Map<Date, CompactVisaRequest> reqs = visas().get(new CompactContributionKey(key, strCompactor));

        if (Objects.isNull(reqs))
            return null;

        return reqs.get(date).toVisaRequest(strCompactor);
    }

    /** */
    public boolean updateVisaRequestRes(ContributionKey key, Date date, Visa visa) {
        VisaRequest req = getVisaReq(key, date);

        if (req == null)
            return false;

        req.setResult(visa);

        put(req);

        return true;
    }

    /** */
    public Collection<VisaRequest> getVisas() {
        List<VisaRequest> res = new ArrayList<>();

        visas().forEach(entry -> res.addAll(entry.getValue().values().stream()
            .map(v -> v.toVisaRequest(strCompactor))
            .collect(Collectors.toList())));

        return Collections.unmodifiableCollection(res);
    }
}
