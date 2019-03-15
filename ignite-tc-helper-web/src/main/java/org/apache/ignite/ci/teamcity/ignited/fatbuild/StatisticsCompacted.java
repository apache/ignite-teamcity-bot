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
import org.apache.ignite.ci.tcmodel.conf.bt.Property;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.internal.util.GridLongList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsCompacted {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(StatisticsCompacted.class);

    private GridIntList keys;
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

            final String valStr = next.getValue();
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

    public Long buildDuration(IStringCompactor compactor) {
        final Integer buildDurationId = compactor.getStringIdIfPresent(Statistics.BUILD_DURATION);
        if (buildDurationId == null)
            return null;

        long val = findPropertyValue(buildDurationId);

        if (val < 0) return null;

        return val;
    }

    private long findPropertyValue(int propCode) {
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
