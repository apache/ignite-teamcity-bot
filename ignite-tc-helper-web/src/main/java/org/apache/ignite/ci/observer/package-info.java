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

/**
 * This package is intended for creating tasks which statuses and
 * results will be observed scheduled. {@link org.apache.ignite.ci.observer.BuildObserver},
 * {@link org.apache.ignite.ci.observer.BuildsInfo} and {@link org.apache.ignite.ci.observer.ObserverTask} are used for automatic Jira
 * Visa reporting for every {@link org.apache.ignite.ci.observer.BuildsInfo} whose builds were finished.
 * {@link org.apache.ignite.ci.observer.BuildsInfo} is representation of observation request and contains
 * context of observed builds. {@link org.apache.ignite.ci.observer.BuildObserver} is used to run
 * {@link org.apache.ignite.ci.observer.ObserverTask} scheduled and register builds which status should be
 * observed. {@link org.apache.ignite.ci.observer.ObserverTask} uses
 * {@link org.apache.ignite.ci.web.model.hist.VisasHistoryStorage} as
 * persistent storage for observed {@link org.apache.ignite.ci.observer.BuildsInfo} which are stored as
 * property of {@link org.apache.ignite.ci.web.model.VisaRequest}. It's
 * assumed that only one {@link org.apache.ignite.ci.observer.BuildsInfo} observation should be for every
 * {@link org.apache.ignite.ci.web.model.ContributionKey} in same time. And
 * {@link org.apache.ignite.ci.web.model.VisaRequest} with which this
 * observation is connected should be last in
 * {@link org.apache.ignite.ci.web.model.hist.VisasHistoryStorage} request's
 * list for specific {@link org.apache.ignite.ci.web.model.ContributionKey}.
 * It's needed for proper changing of status and result of
 * {@link org.apache.ignite.ci.web.model.VisaRequest} by {@link org.apache.ignite.ci.observer.ObserverTask}.
 * If happens an attempt to addBuild observation
 * for {@link org.apache.ignite.ci.web.model.ContributionKey} while current
 * observation is not finished, then current observation will be marked as
 * cancelled and overwritten by the new one.
 *
 */
package org.apache.ignite.ci.observer;
