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

package org.apache.ignite.ci.web.model;

import org.apache.ignite.ci.observer.BuildsInfo;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class VisaRequest {
    /** */
    private BuildsInfo info;

    /** */
    private Visa visa;

    /** */
    private boolean isObserving;

    /** */
    public VisaRequest(BuildsInfo info) {
        this.info = info;
        this.visa = Visa.emptyVisa();
    }

    /** */
    @Nullable public BuildsInfo getInfo() {
        return info;
    }

    /** */
    @Nullable public Visa getResult() {
        return visa;
    }

    /** */
    public VisaRequest setResult(Visa res) {
        this.visa = res;

        return this;
    }

    /** */
    public VisaRequest setObservingStatus(boolean status) {
        isObserving = status;

        return this;
    }

    /** */
    public boolean isObserving() {
        return isObserving;
    }
}
