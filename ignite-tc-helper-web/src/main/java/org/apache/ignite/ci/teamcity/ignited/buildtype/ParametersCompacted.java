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

package org.apache.ignite.ci.teamcity.ignited.buildtype;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.ignite.ci.tcmodel.conf.bt.Parameters;
import org.apache.ignite.ci.tcmodel.conf.bt.Property;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.GridIntList;

public class ParametersCompacted {
    private GridIntList keys;
    private GridIntList values;

    /**
     * Default constructor.
     */
    public ParametersCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param ref Reference.
     */
    public ParametersCompacted(IStringCompactor compactor, List<Property> ref) {
        final int size = ref.size();
        keys = new GridIntList(size);
        values = new GridIntList(size);
        for (Property next : ref) {
            final String name = next.name();
            if (Strings.isNullOrEmpty(name))
                continue;

            final String value = next.getValue();
            if (Strings.isNullOrEmpty(value))
                continue;
            final int val = compactor.getStringId(value);
            final int stringId = compactor.getStringId(name);

            keys.add(stringId);
            values.add(val);
        }
    }

    public Parameters toParameters(IStringCompactor compactor) {
        List<Property> properties = null;

        if (keys.size() > 0) {
            properties = new ArrayList<>();

            final int size = keys.size();

            for (int i = 0; i < size && i < values.size(); i++) {
                final int nameid = keys.get(i);
                String name = compactor.getStringFromId(nameid);
                String value = compactor.getStringFromId(values.get(i));

                properties.add(new Property(name, value));
            }
        }

        return new Parameters(properties);
    }

    public int findPropertyStringId(int propertyCode) {
        int value = -1;

        if (keys != null) {
            final int size = keys.size();

            for (int i = 0; i < size; i++) {
                final int nameid = keys.get(i);

                if (nameid != propertyCode)
                    continue;

                if (i >= values.size())
                    break;

                value = values.get(i);
            }
        }

        return value;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof ParametersCompacted))
            return false;

        ParametersCompacted compacted = (ParametersCompacted)o;

        return Objects.equals(keys, compacted.keys) &&
            Objects.equals(values, compacted.values);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(keys, values);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("keys", keys)
            .add("values", values)
            .toString();
    }
}
