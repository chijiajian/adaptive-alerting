/*
 * Copyright 2018 Expedia Group, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expedia.adaptivealerting.tools.pipeline.source;

import com.expedia.metrics.MetricData;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author Willie Wheeler
 */
public final class RandomWalkMetricSourceTest {
    
    @Test
    public void testStartAndStop() {
        final MetricSource metricSource = new RandomWalkMetricSource("random-walk", 100L, 0);
        final MetricData result = metricSource.next();
        assertNotNull(result);
    }
}
