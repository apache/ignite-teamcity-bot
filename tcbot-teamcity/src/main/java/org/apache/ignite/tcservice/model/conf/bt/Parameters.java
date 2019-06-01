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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Collection of parameters in build
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Parameters {
    @XmlElement(name = "property")
    List<Property> properties;

    public Parameters() {
    }

    public Parameters(List<Property> props) {
        this.properties = props == null ? null : new ArrayList<>(props);
    }

    /**
     * @param key Key of parameter.
     */
    @Nullable
    public String getParameter(String key) {
        if (properties == null)
            return null;

        final Optional<Property> any = properties.stream().filter(property ->
            Objects.equals(property.name, key)).findAny();
        return any.map(Property::value).orElse(null);
    }

    public List<Property> properties() {
        if (this.properties == null)
            return Collections.emptyList();

        return Collections.unmodifiableList(this.properties);
    }

    public boolean setParameter(String key, String value) {
        if (properties == null)
            return false;

        return properties.stream().filter(property ->
            Objects.equals(property.name, key)).map(property -> property.value = value).count() != 0;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof Parameters))
            return false;

        Parameters that = (Parameters)o;
        return Objects.equals(properties, that.properties);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(properties);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("properties", properties)
            .toString();
    }

    /**
     * @return {@code true} if this list contains no elements
     */
    public boolean isEmpty() {
        return properties == null || properties.isEmpty();
    }
}
