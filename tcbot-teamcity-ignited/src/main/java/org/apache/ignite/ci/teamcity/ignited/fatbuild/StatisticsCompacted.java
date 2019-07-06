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

package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.Strings;
import java.util.List;

import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcservice.model.conf.bt.Property;
import org.apache.ignite.tcservice.model.result.stat.Statistics;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.internal.util.GridLongList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Statistics values to be saved in compacted form.
 */
@Persisted
public class StatisticsCompacted {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(StatisticsCompacted.class);

    /** Statistics Keys (Names), int value is coming from the compatcor. */
    private GridIntList keys;

    /** Statistics Parsed Values as long. */
    private GridLongList values;

    public StatisticsCompacted() {
    }

    public StatisticsCompacted(IStringCompactor compactor, Statistics statistics) {
        final List<Property> props = statistics.properties();
        final int size = props.size();
        keys = new GridIntList(size);
        values = new GridLongList(size);

        for (Property next : props) {
            final String name = next.name();
            if (Strings.isNullOrEmpty(name))
                continue;

            final String valStr = next.value();
            if (Strings.isNullOrEmpty(valStr))
                continue;

            final long val;
            try {
                val = Long.parseLong(valStr);
            } catch (Exception e) {
                logger.error("Statistics value is not numeric " + name + " skipped " + e.getMessage(), e);
                continue;
            }

            final int strId = compactor.getStringId(name);

            keys.add(strId);
            values.add(val);
        }
    }

    /**
     * Provides build duration in millis or null.
     *
     * @param compactor Compactor.
     * @return see {@link Statistics#BUILD_DURATION}
     */
    public Long buildDuration(IStringCompactor compactor) {
        Integer buildDurationId = compactor.getStringIdIfPresent(Statistics.BUILD_DURATION);

        return statisticValue(buildDurationId);
    }

    @Nullable public Long statisticValue(@Nullable Integer propCode) {
        if (propCode == null)
            return null;

        long val = findPropertyValue(propCode);

        return val >= 0 ? val : null;
    }

    /**
     * @param compactor Compactor.
     * @return see {@link Statistics#BUILD_DURATION}
     */
    public Long buildDurationNetTime(IStringCompactor compactor) {
        Integer buildDurationNetId = compactor.getStringIdIfPresent(Statistics.BUILD_DURATION_NET_TIME);

        return statisticValue(buildDurationNetId);
    }

    /**
     * @param compactor Compactor.
     * @return see {@link Statistics#ARTIFACTS_PUBLISHING_DURATION}
     */
    public Long artifcactPublishingDuration(IStringCompactor compactor) {
        Integer buildDurationNetId = compactor.getStringIdIfPresent(Statistics.ARTIFACTS_PUBLISHING_DURATION);

        return statisticValue(buildDurationNetId);
    }

    /**
     * @param compactor Compactor.
     * @return see {@link Statistics#DEPENDECIES_RESOLVING_DURATION}
     */
    public Long dependeciesResolvingDuration(IStringCompactor compactor) {
        Integer buildDurationNetId = compactor.getStringIdIfPresent(Statistics.DEPENDECIES_RESOLVING_DURATION);

        return statisticValue(buildDurationNetId);
    }

    /**
     * @param compactor Compactor.
     * @return source update duration in millis.{@link Statistics#SOURCES_UPDATE_DURATION}
     */
    public Long sourceUpdateDuration(IStringCompactor compactor) {
        Integer buildDurationNetId = compactor.getStringIdIfPresent(Statistics.SOURCES_UPDATE_DURATION);

        return statisticValue(buildDurationNetId);
    }

    public long findPropertyValue(int propCode) {
        if (keys == null)
            return -1L;

        final int size = keys.size();

        for (int i = 0; i < size; i++) {
            final int nameid = keys.get(i);

            if (nameid == propCode)
                return i < values.size() ? values.get(i) : -1;
        }

        return -1L;
    }
}
