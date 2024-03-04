/*
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.common.inject.internal;

import java.util.logging.Logger;

/**
 * Enables simple performance monitoring.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Stopwatch {

    private static final Logger LOGGER = Logger.getLogger(Stopwatch.class.getName());
    private static final long NSEC_PER_MSEC = 1000000L;

    private long startNS = System.nanoTime();

    /**
     * Resets and returns elapsed time in milliseconds.
     */
    public long reset() {
        long nowNS = System.nanoTime();
        long deltaNS = nowNS - startNS;
        try {
            return deltaNS / NSEC_PER_MSEC;
        } finally {
            startNS = nowNS;
        }
    }

    /**
     * Resets and logs elapsed time in milliseconds.
     */
    public void resetAndLog(String label) {
        LOGGER.fine(label + ": " + reset() + "ms");
    }
}
