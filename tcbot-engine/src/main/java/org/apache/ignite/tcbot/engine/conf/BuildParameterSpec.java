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

package org.apache.ignite.tcbot.engine.conf;

import com.google.common.base.Strings;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.conf.IBuildParameterSpec;
import org.apache.ignite.tcbot.common.conf.IParameterValueSpec;

import java.util.*;

public class BuildParameterSpec implements IBuildParameterSpec {
    /** Parameter (property) Name. */
    private String name;

    /** Value to use for triggering build. */
    private String value;

    /** Use Random value, valid only for triggering parameters specifucation. */
    @Nullable private Boolean randomValue;

    /**
     * For triggering parameters: possble random values.
     * For filtering parameters: used as value for selection and displaying result.
     */
    @Nullable private List<ParameterValueSpec> selection = new ArrayList<>();

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BuildParameterSpec param = (BuildParameterSpec)o;
        return Objects.equals(name, param.name) &&
            Objects.equals(value, param.value) &&
            Objects.equals(randomValue, param.randomValue) &&
            Objects.equals(selection, param.selection);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(name, value, randomValue, selection);
    }

    /**
     * @return {@link #name}.
     */
    public String name() {
        return name;
    }

    /**
     * @return some valid value for property or null.
     */
    public Object generateValue() {
        if (!Boolean.TRUE.equals(randomValue))
            return value;

        if (selection == null || selection.isEmpty())
            return value;

        int idx = (int) (Math.random() * selection.size());

        ParameterValueSpec spec = selection.get(idx);

        return spec.value();
    }

    public boolean isFilled() {
        return !Strings.isNullOrEmpty(name);
    }

    public List<? extends IParameterValueSpec> selection() {
        return (selection == null || selection.isEmpty())
            ? Collections.emptyList()
            : Collections.unmodifiableList(selection);
    }
}
