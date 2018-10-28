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
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.observer.BuildsInfo;
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
    private Provider<Ignite> igniteProvider;

    /** */
    private volatile Ignite ignite;

    /** */
    public Ignite getIgnite() {
        if (ignite != null)
            return ignite;

        final Ignite ignite = igniteProvider.get();

        this.ignite = ignite;

        return ignite;
    }

    /** */
    public void clear() {
        visas().clear();

        visas().close();

        ignite.destroyCache(VISAS_CACHE_NAME);
    }

    /** */
    private Cache<BuildsInfo, VisaRequest> visas() {
        return getIgnite().getOrCreateCache(TcHelperDb.getCacheV2TxConfig(VISAS_CACHE_NAME));
    }

    /** */
    public void put(VisaRequest visaRequest) {
        visas().put(visaRequest.getInfo(), visaRequest);
    }

    /** */
    @Nullable public VisaRequest get(BuildsInfo info) {
        return visas().get(info);
    }

    /** */
    public boolean updateVisaRequestResult(BuildsInfo info, Visa visa) {
        VisaRequest visaRequest = visas().get(info);

        if (Objects.isNull(visaRequest))
            return false;

        visaRequest.setResult(visa);

        put(visaRequest);

        return true;
    }

    /** */
    public Collection<VisaRequest> getVisas() {
        List<VisaRequest> res = new ArrayList<>();

        visas().forEach(entry -> res.add(entry.getValue()));

        return Collections.unmodifiableCollection(res);
    }
}
