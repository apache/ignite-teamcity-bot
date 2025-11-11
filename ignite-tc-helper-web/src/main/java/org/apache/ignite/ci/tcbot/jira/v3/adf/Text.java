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

package org.apache.ignite.ci.tcbot.jira.v3.adf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @link https://developer.atlassian.com/cloud/jira/platform/apis/document/nodes/text/
 */
public class Text extends Element {
    private static final String TYPE = "text";

    private final String text;

    private List<Mark> marks;

    public Text(String text) {
        super(TYPE);

        this.text = text;
    }

    public Text(String text, Mark mark) {
        super(TYPE);

        this.text = text;
        this.marks = Collections.singletonList(mark);
    }

    public Text append(Mark mark) {
        if (marks == null)
            marks = new ArrayList<>();

        marks.add(mark);

        return this;
    }
}
