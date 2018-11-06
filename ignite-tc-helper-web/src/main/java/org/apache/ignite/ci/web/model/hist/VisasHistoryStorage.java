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
import org.apache.ignite.ci.util.Compactor;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.VisaRequest;

/**
 *
 */
public class VisasHistoryStorage {
    /** */
    private static final String VISAS_CACHE_NAME = "visasCompactCache15";

    /** */
    @Inject
    private Compactor compactor;

    /** */
    @Inject
    private Ignite ignite;

    /** */
    public void clear() {
        visas().clear();
    }

    /** */
    private Cache<Object, Map<Date, Object>> visas() {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(VISAS_CACHE_NAME));
    }

    /** */
    public void put(VisaRequest visaReq) {
        ContributionKey key = new ContributionKey(
            visaReq.getInfo().srvId,
            visaReq.getInfo().ticket,
            visaReq.getInfo().branchForTc);

        Object compactKey = compactor.marshall(key);

        Map<Date, Object> contributionVisas = visas().get(compactKey);

        if (contributionVisas == null)
            contributionVisas = new HashMap<>();

        contributionVisas.put(visaReq.getInfo().date, compactor.marshall(visaReq));

        visas().put(compactKey, contributionVisas);
    }

    /** */
    public VisaRequest getVisaReq(ContributionKey key, Date date) {
        Map<Date, Object> reqs = visas().get(compactor.marshall(key));

        if (Objects.isNull(reqs))
            return null;

        return compactor.unMarshall(reqs.get(date), VisaRequest.class);
    }

    /** */
    public boolean updateVisaRequestRes(ContributionKey key, Date date, Visa visa) {
        VisaRequest req = getVisaReq(key, date);

        if (req == null)
            return false;

        req.setRes(visa);

        put(req);

        return true;
    }

    /** */
    public Collection<VisaRequest> getVisas() {
        List<VisaRequest> res = new ArrayList<>();

        visas().forEach(entry -> res.addAll(entry.getValue().values().stream()
            .map(v -> compactor.unMarshall(v, VisaRequest.class))
            .collect(Collectors.toList())));

        return Collections.unmodifiableCollection(res);
    }
}
