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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.tcbot.common.util.TimeUtil;

/**
 * Mute additional information. Contains mute date and it's comment.
 */
@XmlRootElement(name = "assignment")
public class MuteAssignment {
    /** Mute date. */
    @XmlElement(name = "timestamp") public String muteDate;

    /** Timestamp. */
    private long ts;

    /** Mute comment. */
    @XmlElement public String text;

    /**
     * @return Timestamp for mute date.
     */
    public long timestamp() {
        if (ts == 0)
            ts = TimeUtil.tcSimpleDateToTimestamp(muteDate);

        return ts;
    }

    /**
     * @param ts Timestamp for mute date.
     */
    public void timestamp(long ts) {
        this.ts = ts;
    }
}
