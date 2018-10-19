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

import java.util.Collection;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteSet;
import org.apache.ignite.ci.observer.BuildsInfo;
import org.apache.ignite.configuration.CollectionConfiguration;

/**
 *
 */
public class VisasHistoryStorage {
    /** */
    private static final String VISAS_SET_NAME = "visasSet";

    /** */
    @Inject
    private Ignite ignite;

    /** */
    private IgniteSet<BuildsInfo> builds() {
        return ignite.set(VISAS_SET_NAME, new CollectionConfiguration().setBackups(1));
    }

    /** */
    public void putVisa(BuildsInfo buildsInfo) {
        builds().add(buildsInfo);
    }

    /** */
    public Collection<BuildsInfo> getVisas() {
        return Collections.unmodifiableCollection(builds());
    }
}
