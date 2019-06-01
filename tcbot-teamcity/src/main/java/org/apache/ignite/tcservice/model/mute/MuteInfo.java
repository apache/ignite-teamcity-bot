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

package org.apache.ignite.tcservice.model.mute;

import com.google.common.base.Objects;

import javax.annotation.Nonnull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * TeamCity mute.
 * <p>
 * See example of XML here
 * https://ci.ignite.apache.org/app/rest/mutes/
 */
@XmlRootElement(name = "mute")
@XmlAccessorType(XmlAccessType.FIELD)
public class MuteInfo implements Comparable<MuteInfo> {
    /** Id. */
    @XmlAttribute public int id;

    /** Assignment. */
    @XmlElement public MuteAssignment assignment;

    /** Scope. */
    @XmlElement public MuteScope scope;

    /** Target. */
    @XmlElement public MuteTarget target;

    /** Jira ticket status. TeamCity don't send it, we fill it when send mutes.html. */
    @XmlElement public String ticketStatus;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MuteInfo info = (MuteInfo)o;
        return id == info.id;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(id);
    }

    /** {@inheritDoc} */
    @Override public int compareTo(@Nonnull MuteInfo o) {
        return Integer.compare(id, o.id);
    }
}
