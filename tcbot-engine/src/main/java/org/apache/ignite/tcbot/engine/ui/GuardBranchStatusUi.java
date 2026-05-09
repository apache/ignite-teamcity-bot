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
package org.apache.ignite.tcbot.engine.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Branch summary to be shown at guard page, listing
 */
public class GuardBranchStatusUi {
    private String name;

    private List<Integer> finishedLastDay = new ArrayList<>();
    private List<Integer> runningList = new ArrayList<>();
    private List<Integer> queuedList = new ArrayList<>();
    private List<String> autoTriggerSuites = new ArrayList<>();
    private List<String> lastFailedSuites = new ArrayList<>();
    private List<String> lastStableSuites = new ArrayList<>();
    private List<String> tenBuildFailedSuites = new ArrayList<>();
    private List<String> tenBuildStableSuites = new ArrayList<>();
    private boolean lastBuildStatsAvailable;
    private boolean tenBuildStatsAvailable;
    private int lastBuildsChecked;
    private int tenBuildsChecked;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Integer> getFinishedLastDay() {
        return finishedLastDay;
    }

    public void addSuiteRunStat(int finished, int running, int queued) {
        finishedLastDay.add(finished);
        runningList.add(running);
        queuedList.add(queued);
    }

    public void addAutoTriggerSuite(String suiteId) {
        addUnique(autoTriggerSuites, suiteId);
    }

    public void addLastFailedSuite(String suiteId) {
        lastBuildStatsAvailable = true;
        addUnique(lastFailedSuites, suiteId);
    }

    public void addLastStableSuite(String suiteId) {
        lastBuildStatsAvailable = true;
        addUnique(lastStableSuites, suiteId);
    }

    public void addTenBuildFailedSuite(String suiteId) {
        tenBuildStatsAvailable = true;
        addUnique(tenBuildFailedSuites, suiteId);
    }

    public void addTenBuildStableSuite(String suiteId) {
        tenBuildStatsAvailable = true;
        addUnique(tenBuildStableSuites, suiteId);
    }

    public void addLastBuildsChecked(int count) {
        lastBuildsChecked += count;
    }

    public void addTenBuildsChecked(int count) {
        tenBuildsChecked += count;
    }

    public List<Integer> getRunningList() {
        return runningList;
    }

    public List<Integer> getQueuedList() {
        return queuedList;
    }

    public List<String> getAutoTriggerSuites() {
        return autoTriggerSuites;
    }

    public List<String> getLastFailedSuites() {
        return lastFailedSuites;
    }

    public List<String> getLastStableSuites() {
        return lastStableSuites;
    }

    public List<String> getTenBuildFailedSuites() {
        return tenBuildFailedSuites;
    }

    public List<String> getTenBuildStableSuites() {
        return tenBuildStableSuites;
    }

    public boolean isLastBuildStatsAvailable() {
        return lastBuildStatsAvailable;
    }

    public boolean isTenBuildStatsAvailable() {
        return tenBuildStatsAvailable;
    }

    public int getLastBuildsChecked() {
        return lastBuildsChecked;
    }

    public int getTenBuildsChecked() {
        return tenBuildsChecked;
    }

    private static void addUnique(List<String> target, String suiteId) {
        if (!target.contains(suiteId))
            target.add(suiteId);
    }
}
