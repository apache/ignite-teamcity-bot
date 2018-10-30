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
import java.util.List;
import java.util.Objects;
import javax.cache.Cache;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.VisaRequest;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class VisasHistoryStorage {
    /** */
    private static final String VISAS_CACHE_NAME = "visasCache";

    /** */
    @Inject
    private Ignite ignite;

    /** */
    public void clear() {
        visas().clear();
    }

    /** */
    private Cache<Date, VisaRequest> visas() {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(VISAS_CACHE_NAME));
    }

    /** */
    public void put(VisaRequest visaRequest) {
        visas().put(visaRequest.getInfo().date, visaRequest);
    }

    /** */
    @Nullable public VisaRequest get(Date date) {
        return visas().get(date);
    }

    /** */
    public boolean updateVisaRequestResult(Date date, Visa visa) {
        VisaRequest req = visas().get(date);

        if (Objects.isNull(req))
            return false;

        req.setResult(visa);

        put(req);

        return true;
    }

    /** */
    public Collection<VisaRequest> getVisas() {
        List<VisaRequest> res = new ArrayList<>();

        visas().forEach(entry -> res.add(entry.getValue()));

        return Collections.unmodifiableCollection(res);
    }
}
