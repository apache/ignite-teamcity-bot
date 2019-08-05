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
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcservice.model.conf.bt.Parameters;
import org.apache.ignite.tcservice.model.conf.bt.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Properties (Build/Build Type parameters) compacted value for storing in TC Bot DB
 */
@Persisted
public class ParametersCompacted {
    /** Property Keys (Names), int value is coming from the compatcor. */
    private GridIntList keys;

    /** Property Values, int value is coming from the compatcor. */
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
            String name = next.name();
            if (Strings.isNullOrEmpty(name))
                continue;

            String strVal = next.value();
            if (Strings.isNullOrEmpty(strVal))
                continue;

            int val = compactor.getStringId(strVal);
            int strId = compactor.getStringId(name);

            keys.add(strId);
            values.add(val);
        }
    }

    public ParametersCompacted(Map<Integer, Integer> parms) {
        keys = new GridIntList(parms.size());
        values = new GridIntList(parms.size());

        parms.forEach((strId, val) -> {
            keys.add(strId);
            values.add(val);
        });
    }

    public Parameters toParameters(IStringCompactor compactor) {
        List<Property> props = null;

        if (!keys.isEmpty()) {
            props = new ArrayList<>();

            final int size = keys.size();

            for (int i = 0; i < size && i < values.size(); i++) {
                final int nameid = keys.get(i);
                String name = compactor.getStringFromId(nameid);
                String val = compactor.getStringFromId(values.get(i));

                props.add(new Property(name, val));
            }
        }

        return new Parameters(props);
    }

    public int findPropertyStringId(int propCode) {
        if (keys == null)
            return -1;

        final int size = keys.size();

        for (int i = 0; i < size; i++) {
            int nameid = keys.get(i);

            if (nameid == propCode)
                return i < values.size() ? values.get(i) : -1;
        }

        return -1;
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


    public void forEach(IStringCompactor compactor, BiConsumer<String, String> consumer) {
        forEach((k,v)-> consumer.accept(compactor.getStringFromId(k), compactor.getStringFromId(v)));
    }

    public void forEach(BiConsumer<Integer, Integer> consumer) {
        int size = keys.size();

        for (int i = 0; i < size; i++) {
            int nameid = keys.get(i);

            if (i >= values.size())
                break;

            int valId = values.get(i);

            consumer.accept(nameid, valId);
        }
    }

    @Nullable
    public String getProperty(IStringCompactor compactor, String parmKey) {
        Integer present = compactor.getStringIdIfPresent(parmKey);
        if (present == null)
            return null;

        int propStrId = findPropertyStringId(present);
        if (propStrId < 0)
            return null;

        return compactor.getStringFromId(propStrId);
    }
}
