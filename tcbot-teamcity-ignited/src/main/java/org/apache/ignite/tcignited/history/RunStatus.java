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

package org.apache.ignite.tcignited.history;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Status of run: Generalized result of execution of Suite/Test.
 */
public enum RunStatus {
    /** Result: success. */
    RES_OK(0),
    /** Result: general failure of test or suite. */
    RES_FAILURE(1),
    /** RES OK or RES FAILURE */
    RES_OK_OR_FAILURE(10),
    /** Result of test execution, muted failure found. */
    RES_MUTED_FAILURE(2),
    /** Result of suite: Critical failure, no results. */
    RES_CRITICAL_FAILURE(3),
    /** Test is not present in current run */
    RES_MISSING(4),
    /** Muted, but test passed. */
    RES_OK_MUTED(5),
    /** Muted, and test failed. */
    RES_FAILURE_MUTED(6),
    /** Test ignored. */
    RES_IGNORED(7);


    /** Mapping of status int -> object. */
    private static Map<Integer, RunStatus> holder = Stream.of(values()).collect(Collectors.toMap(RunStatus::getCode, i -> i));

    /** Represent status in int. */
    int code;

    /** */
    RunStatus(int code) {
        this.code = code;
    }

    /**
     * @return Status as int.
     */
    public int getCode() {
        return code;
    }

    /**
     * @param code Status as int.
     * @return Status of build.
     */
    public static RunStatus byCode(int code) {
        return holder.get(code);
    }
}
