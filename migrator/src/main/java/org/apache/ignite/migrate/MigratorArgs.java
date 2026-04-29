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

package org.apache.ignite.migrate;

/**
 * Migrator command-line arguments.
 * <p>
 * Supported flags:
 * --apply           actually write changes (otherwise dry-run)
 * --verbose         more diagnostics
 * --cache <substr>  process only caches whose name contains given substring
 * --report <N>      progress log interval
 * --workDir <path>  path to work/ directory (overrides IGNITE_WORK_DIR)
 */
public final class MigratorArgs {
    boolean apply = false;
    boolean verbose = false;
    String cacheFilter = null;
    int reportEvery = 500;
    String workDir = null;

    static MigratorArgs parse(String[] args) {
        MigratorArgs cliArgs = new MigratorArgs();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--apply":
                    cliArgs.apply = true;
                    break;
                case "--verbose":
                    cliArgs.verbose = true;
                    break;
                case "--cache":
                    cliArgs.cacheFilter = args[++i];
                    break;
                case "--report":
                    cliArgs.reportEvery = Integer.parseInt(args[++i]);
                    break;
                case "--workDir":
                    cliArgs.workDir = args[++i];
                    break;
                default:
                    break;
            }
        }
        GridIntListMigrator.GetMigratorLogger().info(
            "Args: apply={} verbose={} cacheFilter={} reportEvery={} workDir={}",
            cliArgs.apply, cliArgs.verbose, cliArgs.cacheFilter, cliArgs.reportEvery, cliArgs.workDir
        );

        return cliArgs;
    }
}