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

public class CleanerConfig implements ICleanerConfig{
    /** */
    public static final int DEFAULT_SAVE_DAYS = 30 * 6;

    /** */
    public static final int DEFAULT_NUMBER_OF_ITEMS_TO_DELETE = 100;

    /** */
    private Integer safeDays;

    /** */
    private Integer numOfItemsToDel;

    /** */
    private Boolean enabled;

    public static CleanerConfig getDefaultCleanerConfig() {
        CleanerConfig cfg = new CleanerConfig();
        cfg.safeDays = DEFAULT_SAVE_DAYS;
        cfg.numOfItemsToDel = DEFAULT_NUMBER_OF_ITEMS_TO_DELETE;
        cfg.enabled = true;
        return cfg;
    }

    /** */
    public int safeDays() {
        return safeDays == null || safeDays < 0 ? DEFAULT_SAVE_DAYS : safeDays;
    }

    /** */
    public int numOfItemsToDel() {
        return numOfItemsToDel == null || numOfItemsToDel < 0 ? DEFAULT_NUMBER_OF_ITEMS_TO_DELETE : numOfItemsToDel;
    }

    /** */
    public boolean enabled() {
        return enabled == null ? true : enabled;
    }
}
