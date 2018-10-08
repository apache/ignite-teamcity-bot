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

package org.apache.ignite.ci.teamcity.pure;

import com.google.common.base.Charsets;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class TeamcityRecorder {
    /** Urls. */
    private ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<>();

    /** Lock. */
    private ReentrantLock lock = new ReentrantLock();

    /** File. */
    private OutputStream file;

    /**
     * @param inputStream Input stream.
     * @param url Url.
     */
    public InputStream onGet(InputStream inputStream, String url) throws IOException {
        if (Boolean.valueOf(System.getProperty("teamcity.bot.recorder.urls"))) {
            urls.add(url);

            if (urls.size() > 100)
                urls.remove();
        }

        if (Boolean.valueOf(System.getProperty("teamcity.bot.recorder"))) {
            boolean success = false;

            lock.lock();
            try {
                if (file == null)
                    file = new FileOutputStream("tcrecorder.txt");

                final String newUrlStartStr = "===HTTP=RECORDER=== GET " + url + "\n";
                file.write(newUrlStartStr.getBytes(Charsets.UTF_8));

                FileRecordingInputStream spyStream = new FileRecordingInputStream(inputStream, file, lock);

                success = true;

                return spyStream;
            }
            finally {
                if(!success)
                    lock.unlock();;
            }
        }

        return inputStream;
    }

    /**
     *
     */
    public List<String> getUrls() {
        return new ArrayList<>(urls);
    }

    /**
     *
     */
    public void stop() throws IOException {
        if (file != null)
            file.close();
    }
}
