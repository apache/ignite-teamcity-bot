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
package org.apache.ignite.tcbot.common.conf;

import java.io.File;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

public class TcBotWorkDir {
    public static File ensureDirExist(File workDir) {
        if (!workDir.exists())
            checkState(workDir.mkdirs(), "Unable to make directory [" + workDir + "]");

        return workDir;
    }

    public static File resolveWorkDir() {
        File workDir = null;
        String property = System.getProperty(TcBotSystemProperties.TEAMCITY_HELPER_HOME);
        if (isNullOrEmpty(property)) {
            String conf = ".ignite-teamcity-helper";
            String prop = System.getProperty("user.home");
            //relative in work dir
            workDir = isNullOrEmpty(prop) ? new File(conf) : new File(prop, conf);
        }
        else
            workDir = new File(property);

        return ensureDirExist(workDir);
    }
}
