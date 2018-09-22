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
package org.apache.ignite.ci.web.rest.monitoring;

import org.apache.ignite.ci.util.TimeUtil;

public class HotSpot {
    public String method;
    public String duration;

    private long ns;
    private int count;

    public long getNanos() {
        return ns;
    }

    @Override
    public String toString() {
        return "HotSpot{" +
                "method='" + method + '\'' +
                ", count='" + count + "'" +
                ", duration='" + TimeUtil.nanosToDurationPrintable(ns) + "'" +
                ", avg='" + TimeUtil.nanosToDurationPrintable(ns/count) + "'" +
                '}';
    }

    public void setNanos(long l) {
        ns = l;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
