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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 */
@XmlRootElement(name = "mute")
@XmlAccessorType(XmlAccessType.FIELD)
public class MuteInfo {
    /** Id. */
    @XmlAttribute public int id;

    /** Assignment. */
    @XmlElement public MuteAssignment assignment;

    /** Scope. */
    @XmlElement public MuteScope scope;

    /** Target. */
    @XmlElement public MuteTarget target;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        MuteInfo info = (MuteInfo)o;

        if (id != info.id)
            return false;

        if (assignment != null ? !assignment.equals(info.assignment) : info.assignment != null)
            return false;

        if (scope != null ? !scope.equals(info.scope) : info.scope != null)
            return false;

        return target != null ? target.equals(info.target) : info.target == null;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = id;
        res = 31 * res + (assignment != null ? assignment.hashCode() : 0);
        res = 31 * res + (scope != null ? scope.hashCode() : 0);
        res = 31 * res + (target != null ? target.hashCode() : 0);
        return res;
    }
}
