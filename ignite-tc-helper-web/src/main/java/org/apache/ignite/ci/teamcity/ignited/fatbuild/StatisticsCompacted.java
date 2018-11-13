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
import org.apache.ignite.ci.tcmodel.conf.bt.Property;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.internal.util.GridLongList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StatisticsCompacted {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(StatisticsCompacted.class);

    private GridIntList keys;
    private GridLongList values;

    public StatisticsCompacted() {
    }

    public StatisticsCompacted(IStringCompactor compactor, Statistics statistics) {
        final List<Property> properties = statistics.properties();
        final int size = properties.size();
        keys = new GridIntList(size);
        values = new GridLongList(size);
        int idx = 0;
        for (Property next : properties) {
            final String name = next.name();
            if (Strings.isNullOrEmpty(name))
                continue;

            final String value = next.getValue();
            if (Strings.isNullOrEmpty(value))
                continue;
            final long val;
            try {
                val = Long.parseLong(value);
            } catch (Exception e) {
                logger.error("Statistics value is not numeric " + name + " skipped " + e.getMessage(), e);
                continue;
            }

            final int stringId = compactor.getStringId(name);

            keys.add(stringId);
            values.add(val);
            idx++;
        }
    }

    public Long buildDuration(IStringCompactor compactor) {
        final Integer buildDurationId = compactor.getStringIdIfPresent(Statistics.BUILD_DURATION);
        if (buildDurationId == null)
            return null;

        long value = findPropertyValue(buildDurationId);

        if (value < 0) return null;

        return value;
    }

    private long findPropertyValue(int propertyCode) {
        final int size = keys.size();

        long value = -1;
        for (int i = 0; i < size; i++) {
            final int nameid = keys.get(i);

            if (nameid != propertyCode)
                continue;

            if (i >= values.size())
                break;

            value = values.get(i);
        }

        return value;
    }
}
