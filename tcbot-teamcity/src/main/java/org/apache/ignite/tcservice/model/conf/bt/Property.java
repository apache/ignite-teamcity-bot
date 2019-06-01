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

package org.apache.ignite.tcservice.model.conf.bt;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * Any property in builds or other configs.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Property {
    /** Parameter Name/Key. */
    @XmlAttribute String name;

    /** Parameter Value. */
    @XmlAttribute String value;

    /** Flag indicating that value is inherited from a template. */
    @XmlAttribute Boolean inherited;

    public Property() {
    }

    public Property(String name, String val) {
        this.name = name;
        this.value = val;
        this.inherited = null;
    }

    public Property(String name, String val, Boolean inherited) {
        this.name = name;
        this.value = val;
        this.inherited = inherited;
    }

    /**
     * @return {@link #value}
     */
    @Nullable public String value() {
        return value;
    }

    /**
     * @return {@link #name}
     */
    public String name() {
        return name;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof Property))
            return false;

        Property prop = (Property)o;

        return Objects.equals(name, prop.name) &&
            Objects.equals(value(), prop.value()) &&
            Objects.equals(inherited, prop.inherited);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(name, value(), inherited);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .add("value", value)
            .add("inherited", inherited)
            .toString();
    }
}
