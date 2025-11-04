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

/**
 * @link https://developer.atlassian.com/cloud/jira/platform/apis/document/nodes/bulletList/
 */
public class BulletList extends ElementContainer {
    private static final String TYPE = "bulletList";

    public BulletList() {
        super(TYPE);
    }

    public BulletList append(String text, Mark mark) {
        Paragraph p = new Paragraph();
        p.appendInternal(new Text(text, mark));

        Item item = new Item();
        item.appendInternal(p);

        appendInternal(item);
        return this;
    }

    public BulletList append(String text) {
        Paragraph p = new Paragraph();
        p.appendInternal(new Text(text));

        Item item = new Item();
        item.appendInternal(p);

        appendInternal(item);
        return this;
    }

    public static class Item extends ElementContainer {
        private static final String TYPE = "listItem";

        public Item() {
            super(TYPE);
        }
    }
}
