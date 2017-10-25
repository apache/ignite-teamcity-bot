package org.apache.ignite.ci.web.rest.model.chart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contains values for one line on chart
 */
public class LineData {
    public List<Double> values;

    public LineData(Double... values) {
        this.values = new ArrayList<>(Arrays.asList(values));
    }

    public void addMeasurementAt(int idx, Double value) {
        if (idx == values.size())
            values.add(value);
        else {
            if (values.size() < idx) {
                int add = idx - values.size();

                for (int i = 0; i < add; i++)
                    values.add(null);
            }
            values.add(idx, value);
        }
    }
}
