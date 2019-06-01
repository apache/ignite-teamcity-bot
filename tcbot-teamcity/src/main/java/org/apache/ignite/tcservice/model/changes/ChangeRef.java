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

package org.apache.ignite.tcservice.model.changes;

import com.google.common.base.Strings;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import org.apache.ignite.tcservice.model.result.AbstractRef;

import static org.apache.ignite.tcbot.common.exeption.ExceptionUtil.propagateException;

/**
 * Change short version.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ChangeRef extends AbstractRef {
    @XmlAttribute public String id;
    @XmlAttribute public String version;

    /**
     * VCS username
     */
    @XmlAttribute public String username;
    @XmlAttribute public String date;
    @XmlAttribute public String webUrl;


    @Override public String toString() {
        return "ChangeRef{" +
            "href='" + href + '\'' +
            ", id='" + id + '\'' +
            ", version='" + version + '\'' +
            ", username='" + username + '\'' +
            ", date='" + date + '\'' +
            ", webUrl='" + webUrl + '\'' +
            '}';
    }

    /** Format local. */
    @XmlTransient private static ThreadLocal<SimpleDateFormat> fmtLoc
        = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMdd'T'HHmmssZ"));

    /**
     *
     */
    public Long getDateTs() {
        try {
            return Strings.isNullOrEmpty(date) ? null : fmtLoc.get().parse(date).getTime();
        }
        catch (ParseException e) {
            throw propagateException(e);
        }
    }
}
