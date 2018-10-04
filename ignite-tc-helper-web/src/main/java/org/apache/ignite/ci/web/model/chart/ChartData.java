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

package org.apache.ignite.ci.web.model.chart;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 *
 */
@Deprecated
public class ChartData<K> {
    /** Captions for measurements at axis X */
    public List<String> axisX;

    public List<LineData> lines = new ArrayList<>();

    public List<String> legendLabels = new ArrayList<>();

    transient Map<K, Integer> keyToLineMapping = new HashMap<>();

    public ChartData(String... axisX) {
        this.axisX = new ArrayList<>(Arrays.asList(axisX));
    }

    public void addEmptyLine(K key) {
        addEmptyLine(key, null);
    }

    public void addEmptyLine(K key, Function<K, String> legendFunction) {
        keyToLineMapping.computeIfAbsent(key, (k) -> {
            int index = lines.size();
            addLine(new LineData());
            if (legendFunction != null) {
                legendLabels.add(legendFunction.apply(k));
            }
            return index;
        });
    }

    public void addLine(LineData data) {
        lines.add(data);
    }

    public int addAxisXLabel(String label) {
        int idx = axisX.size();
        axisX.add(label);
        return idx;
    }

    public void addMeasurement(K mappedKey, int idx, Double value) {
        Integer lineId = keyToLineMapping.get(mappedKey);
        Preconditions.checkState(lineId != null, "Error key [" + mappedKey + "] has no mapped line");
        LineData data = lines.get(lineId);
        Preconditions.checkState(data != null, "Error key [" + mappedKey + "] has null mapped line");

        data.addMeasurementAt(idx, value);
    }
}
