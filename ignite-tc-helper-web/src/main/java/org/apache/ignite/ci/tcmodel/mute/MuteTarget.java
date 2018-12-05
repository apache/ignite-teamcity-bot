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

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import org.apache.ignite.ci.tcmodel.result.tests.TestRef;

/**
 * Mute target (e.g. muted test).
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MuteTarget {
    /** Tests. */
    @XmlElementWrapper(name="tests")
    @XmlElement(name="test")
    public List<TestRef> tests;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        MuteTarget target = (MuteTarget)o;

        return tests != null ? tests.equals(target.tests) : target.tests == null;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return tests != null ? tests.hashCode() : 0;
    }
}
