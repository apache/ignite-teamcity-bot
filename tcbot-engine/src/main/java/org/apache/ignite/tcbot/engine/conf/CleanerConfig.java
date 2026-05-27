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
package org.apache.ignite.tcbot.engine.conf;

/** */
public class CleanerConfig implements ICleanerConfig{
    /** */
    public static final int DEFAULT_SAFE_DAYS_FOR_CACHES = 120;

    /** */
    public static final int DEFAULT_SAFE_DAYS_FOR_LOGS = 70;

    /** */
    public static final int DEFAULT_NUMBER_OF_ITEMS_TO_DELETE = 100_000;

    /** */
    public static final int DEFAULT_DELETE_BATCH = 1_000;

    /** */
    public static final int DEFAULT_PERIOD_MINUTES = 360;

    /** */
    private Integer safeDaysForCaches;

    /** */
    private Integer safeDaysForLogs;

    /** */
    private Integer numOfItemsToDel;

    /** */
    private Integer deleteBatch;

    /** */
    private Integer period;

    /** */
    private Boolean enabled;

    /** */
    public static CleanerConfig getDefaultCleanerConfig() {
        CleanerConfig cfg = new CleanerConfig();
        cfg.safeDaysForCaches = DEFAULT_SAFE_DAYS_FOR_CACHES;
        cfg.safeDaysForLogs = DEFAULT_SAFE_DAYS_FOR_LOGS;
        cfg.numOfItemsToDel = DEFAULT_NUMBER_OF_ITEMS_TO_DELETE;
        cfg.deleteBatch = DEFAULT_DELETE_BATCH;
        cfg.enabled = true;
        cfg.period = DEFAULT_PERIOD_MINUTES;
        return cfg;
    }

    /** */
    public int safeDaysForCaches() {
        return safeDaysForCaches == null || safeDaysForCaches < 0 ? DEFAULT_SAFE_DAYS_FOR_CACHES : safeDaysForCaches;
    }

    /** */
    public int safeDaysForLogs() {
        return safeDaysForLogs == null || safeDaysForLogs < 0 ? DEFAULT_SAFE_DAYS_FOR_LOGS : safeDaysForLogs;
    }

    /** */
    public int numOfItemsToDel() {
        return numOfItemsToDel == null || numOfItemsToDel < 0 ? DEFAULT_NUMBER_OF_ITEMS_TO_DELETE : numOfItemsToDel;
    }

    /** */
    public int deleteBatch() {
        return deleteBatch == null || deleteBatch < 0 ? DEFAULT_DELETE_BATCH : deleteBatch;
    }

    /** */
    public int period() {
        return period == null || period < 0 ? DEFAULT_PERIOD_MINUTES : period;
    }

    /** */
    public boolean enabled() {
        return enabled == null ? true : enabled;
    }
}
