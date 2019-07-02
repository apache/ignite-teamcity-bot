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

package org.apache.ignite.githubservice;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.github.PullRequest;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class GitHubPrsParseTest {

    @Test
    public void parse() {
        InputStream stream = this.getClass().getResourceAsStream("/prsList.json");
        Preconditions.checkNotNull(stream, "Can't find resource");
        Type listType = new TypeToken<ArrayList<PullRequest>>() {
        }.getType();
        List<PullRequest> list = new Gson().fromJson(new InputStreamReader(stream), listType);

        System.out.println(list.size());
        System.out.println(list);
    }

    @Test
    public void parseLinkRspHeader() {
        String s = "<https://api.github.com/repositories/31006158/pulls?sort=updated&direction=desc&page=2>; rel=\"next\", <https://api.github.com/repositories/31006158/pulls?sort=updated&direction=desc&page=45>; rel=\"last\"\n";
        String nextLink = GitHubConnectionImpl.parseNextLinkFromLinkRspHeader(s);

        assertEquals("https://api.github.com/repositories/31006158/pulls?sort=updated&direction=desc&page=2", nextLink);
    }

}
