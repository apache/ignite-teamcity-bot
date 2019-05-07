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
import java.util.function.BiConsumer;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.conf.bt.Property;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.GridIntList;

/**
 * Properties (Build parameters) compacted value for storing in TC Bot DB
 */
@Persisted
public class PropertiesCompacted {
    /** Property Keys (Names), int value is coming from the compatcor. */
    private GridIntList keys;

    /** Property Values, int value is coming from the compatcor. */
    private GridIntList values;

    public PropertiesCompacted() {
    }

    public PropertiesCompacted(IStringCompactor compactor, List<Property> props) {
        int size = props.size();
        keys = new GridIntList(size);
        values = new GridIntList(size);

        for (Property next : props) {
            String name = next.name();
            if (Strings.isNullOrEmpty(name))
                continue;

            String valStr = next.value();

            keys.add(compactor.getStringId(name));
            values.add(compactor.getStringId(valStr));
        }
    }

    private long findPropertyValue(int propCode) {
        if (keys == null)
            return -1L;

        int size = keys.size();

        for (int i = 0; i < size; i++) {
            int nameid = keys.get(i);

            if (nameid == propCode)
                return i < values.size() ? values.get(i) : -1;
        }

        return -1L;
    }

    public void forEach(IStringCompactor compactor, BiConsumer<String, String> consumer) {
        int size = keys.size();

        for (int i = 0; i < size; i++) {
            int nameid = keys.get(i);

            if (i >= values.size())
                break;

            int valId = values.get(i);

            consumer.accept(compactor.getStringFromId(nameid), compactor.getStringFromId(valId));
        }

    }
}
