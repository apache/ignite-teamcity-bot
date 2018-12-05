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

import java.util.HashSet;
import java.util.Set;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 * @see Mutes
 */
public class MutesCompacted {
    /** Mutes. */
    private Set<MuteInfoCompacted> mutes;

    /**
     * @param mutes Mutes.
     * @param comp Comparator.
     */
    public MutesCompacted(Mutes mutes, IStringCompactor comp) {
        this(mutes.getMutesNonNull(), comp);
    }

    /**
     * @param infos Mute infos.
     * @param comp Comparator.
     */
    public MutesCompacted(Set<MuteInfo> infos, IStringCompactor comp) {
        mutes = new HashSet<>(U.capacity(infos.size()));

        for (MuteInfo info : infos)
            mutes.add(new MuteInfoCompacted(info, comp));
    }

    /**
     * @param comp Comparator.
     */
    Mutes toMutes(IStringCompactor comp) {
        Set<MuteInfo> infos = new HashSet<>();

        for (MuteInfoCompacted mute : mutes)
            infos.add(mute.toMuteInfo(comp));

        return new Mutes(infos);
    }
}
