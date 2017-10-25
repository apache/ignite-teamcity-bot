package org.apache.ignite.ci.web.rest.model.chart;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Дмитрий on 09.08.2017
 */
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
