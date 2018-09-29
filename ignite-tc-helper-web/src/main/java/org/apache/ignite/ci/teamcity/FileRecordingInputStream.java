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

package org.apache.ignite.ci.teamcity;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class FileRecordingInputStream extends FilterInputStream {

    private final OutputStream file;
    private final ReentrantLock lock;
    private final AtomicBoolean closeGuard = new AtomicBoolean();

    protected FileRecordingInputStream(InputStream in,
                                       OutputStream file,
                                       ReentrantLock lock) {
        super(in);
        this.file = file;
        this.lock = lock;
    }

    @Override
    public int read() throws IOException {
        Preconditions.checkState(!closeGuard.get());

        int readByte = super.read();

        processByte(readByte);

        return readByte;
    }

    @Override
    public int read(@NotNull byte[] buffer, int offset, int count) throws IOException {
        Preconditions.checkState(!closeGuard.get());

        int readBytes = super.read(buffer, offset, count);

        if (readBytes < 0)
            return readBytes;

        processBytes(buffer, offset, readBytes);

        return readBytes;
    }

    private void processBytes(byte[] buffer, int offset, int readBytes) throws IOException {
        file.write(buffer, offset, readBytes);
    }

    private void processByte(int readByte) throws IOException {
        file.write(new byte[]{(byte) readByte});
    }

    @Override
    public void close() throws IOException {
        super.close();

        if (closeGuard.compareAndSet(false, true)) {
            file.write("\n".getBytes(Charsets.UTF_8));

            lock.unlock();

            file.flush();
        }
    }
}