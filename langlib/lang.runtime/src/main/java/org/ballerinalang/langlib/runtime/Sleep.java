/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.langlib.runtime;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.values.BDecimal;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Native implementation for sleep function.
 *
 * @since 2.0.0
 */
public class Sleep {

    private static final int CORE_THREAD_POOL_SIZE = 1;

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(CORE_THREAD_POOL_SIZE);

    public static void sleep(Environment env, BDecimal delaySeconds) {
        Future balFuture = env.markAsync();
        long delayMillis = (delaySeconds.decimalValue().multiply(new BigDecimal("1000.0"))).longValue();
        executor.schedule(() -> balFuture.complete(null), delayMillis, TimeUnit.MILLISECONDS);
    }

    private Sleep() {
    }
}
