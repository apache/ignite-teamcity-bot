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

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.tcservice.model.result.AbstractRef;

/**
 * Mute entities from TeamCity. On TeamCity this object represent page with several mutes.
 * <p>
 * See example of XML here
 * https://ci.ignite.apache.org/app/rest/mutes/
 */
@XmlRootElement(name = "mutes")
@XmlAccessorType(XmlAccessType.FIELD)
public class Mutes extends AbstractRef {
    /** Mutes. */
    @XmlElement(name = "mute")
    private SortedSet<MuteInfo> mutes;

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
        this.mutes = new TreeSet<>(mutes);
    }

    /**
     * @return Mute set. Return empty set if no mutes presented.
     */
    public SortedSet<MuteInfo> getMutesNonNull() {
        return mutes == null ? Collections.emptySortedSet() : mutes;
    }

    /**
     * @return Next page url.
     */
    public String nextHref() {
        return nextHref;
    }
}
