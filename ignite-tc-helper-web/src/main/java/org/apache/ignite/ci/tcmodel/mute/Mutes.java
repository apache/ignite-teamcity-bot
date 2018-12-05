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

package org.apache.ignite.ci.tcmodel.mute;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.tcmodel.result.AbstractRef;

/**
 * Mute entities from TeamCity. On TeamCity this object represent page with several mutes.
 * <p>
 * But we unite mutes into single object to store in mute cache.
 * <p>
 * See https://ci.ignite.apache.org/app/rest/mutes/
 */
@XmlRootElement(name = "mutes")
@XmlAccessorType(XmlAccessType.FIELD)
public class Mutes extends AbstractRef {
    /** Mutes. */
    @XmlElement(name = "mute")
    private Set<MuteInfo> mutes;

    /** Next page url. */
    @XmlAttribute private String nextHref;

    /**
     * Default constructor. Need for xml loader.
     */
    public Mutes() {}

    /**
     * @param mutes List.
     */
    public Mutes(Set<MuteInfo> mutes) {
        this.mutes = new HashSet<>(mutes);
    }

    /**
     * @return Mute set. Return empty set if no mutes presented.
     */
    public Set<MuteInfo> getMutesNonNull() {
        return mutes == null ? Collections.emptySet() : mutes;
    }

    /**
     * @param infos Mutes.
     * @return {@code True} if this set changed as a result of the call.
     */
    public boolean add(Collection<MuteInfo> infos) {
        if (mutes == null)
            mutes = new HashSet<>();

        return mutes.addAll(infos);
    }

    /**
     * @return Next page url.
     */
    public String nextHref() {
        return nextHref;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        Mutes mutes1 = (Mutes)o;

        if (mutes != null ? !mutes.equals(mutes1.mutes) : mutes1.mutes != null)
            return false;

        return nextHref != null ? nextHref.equals(mutes1.nextHref) : mutes1.nextHref == null;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = mutes != null ? mutes.hashCode() : 0;
        res = 31 * res + (nextHref != null ? nextHref.hashCode() : 0);
        return res;
    }
}
