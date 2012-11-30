/*
    Tryton Android
    Copyright (C) 2012 SARL SCOP Scil (contact@scil.coop)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.tryton.client.tools;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.BarChart;
import org.achartengine.model.CategorySeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.SimpleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import org.tryton.client.data.Session;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;

public class GraphViewFactory {

    private static final int[] DEFAULT_COLORS = new int[] {
        Color.argb(255, 34, 69, 101),
        Color.argb(255, 54, 89, 121),
        Color.argb(255, 74, 109, 141),
        Color.argb(255, 94, 130, 162),
        Color.argb(255, 115, 150, 182)
    };

    private static double getYValue(Model data, Model yAxis) {
        String fieldName = yAxis.getString("name");
        String fieldType = yAxis.getString("type");
        if (fieldType.equals("integer") || fieldType.equals("biginteger")) {
            Integer value = (Integer) data.get(fieldName);
            if (value == null) {
                value = 0;
            }
            return value;
        } else if (fieldType.equals("float") || fieldType.equals("numeric")) {
            Object value = data.get(fieldName);
            if (value != null) {
                Double dval = null;
                if (value instanceof Map) {
                    // decimal
                    dval = FieldsConvertion.numericToDouble((Map)value);
                } else {
                    // float
                    dval = (Double)value;
                }
                if (dval == null) {
                    dval = 0.0;
                }
                return dval;
            } else {
                return 0;
            }
        } else if (fieldType.equals("boolean")) {
            if ((Boolean) data.get(fieldName)) {
                return 1;
            } else {
                return 0;
            }
        } else {
            return 1;
        }
    }

    private static Map<Object, Double> getValues(List<Model> data,
                                                 Model xAxis,  Model yAxis) {
        String xFieldName = xAxis.getString("name");
        // Compute y summed value for each x value
        double sum = 0.0;
        Map<Object, Double> plots = new HashMap<Object, Double>();
        for (Model m : data) {
            Object xValue = m.get(xFieldName);
            if (!plots.containsKey(xValue)) {
                plots.put(xValue, 0.0);
            }
            double currVal = plots.get(xValue);
            currVal += getYValue(m, yAxis);
            plots.put(xValue, currVal);
        }
        return plots;
    }

    private static List<Integer> fillIntKeys(Map<Object, Double> values,
                                             boolean addEmpty) {
        List<Integer> ret = new ArrayList<Integer>();
        if (addEmpty) {
            int min = 1;
            int max = 0;
            for (Object o : values.keySet()) {
                int val = (Integer) o;
                if (min > max) {
                    min = val;
                    max = val;
                } else {
                    if (val > max) {
                        max = val;
                    }
                    if (val < min) {
                        min = val;
                    }
                }
            }
            for (int i = min; i < max; i++) {
                ret.add(i);
                if (!values.containsKey(i)) {
                    values.put(i, 0.0);
                }
            }
        } else {
            for (Object o : values.keySet()) {
                int val = (Integer) o;
                ret.add(val);
            }
            Collections.sort(ret);
        }
        return ret;
    }

    private static List<Double> sortDoubleKeys(Map<Object, Double> values) {
        List<Double> ret = new ArrayList<Double>();
        for (Object o : values.keySet()) {
            Double val = null;
            if (o instanceof Map) {
                val = FieldsConvertion.numericToDouble((Map) o);
            } else {
                val = (Double) o;
            }
            ret.add(val);
        }
        Collections.sort(ret);
        return ret;
    }

    private static List<String> sortStringKeys(Map<Object, Double> values) {
        List<String> ret = new ArrayList<String>();
        for (Object o : values.keySet()) {
            String val = (String) o;
            ret.add(val);
        }
        Collections.sort(ret);
        return ret;
    }

    private static List<Map> fillDateKeys(Map<Object, Double> values,
                                            boolean addEmpty) {
        List<int[]> retInt = new ArrayList<int[]>();
        List<Map> ret = new ArrayList<Map>();
        for (Object o : values.keySet()) {
            int[] date = FieldsConvertion.dateToIntA((Map) o);
            retInt.add(date);
        }
        Comparator<int[]> c = new Comparator<int[]>() {
            public int compare (int[] a, int[] b) {
                int aa = a[0] * 10000 + a[1] * 100 + a[2];
                int bb = b[0] * 10000 + b[1] * 100 + b[2];
                return aa - bb;
            }
            public boolean equals(int[] a, int[] b) {
                return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
            }
        };
        Collections.sort(retInt, c);
        for (int[] intA : retInt) {
            Map date = FieldsConvertion.intAToDate(intA);
            ret.add(date);
        }
        // TODO: fill
        return ret;
    }

    /** Order the keys from a value map and add missing ones.
     * AddEmty is the empty attribute (add 0 values) */
    private static List fillKeys(Map<Object, Double> values,
                                         boolean addEmpty) {
        // Get one key to check type
        Object key = null;
        for (Object o : values.keySet()) {
            key = o;
            break;
        }
        if (key instanceof Integer) {
            return fillIntKeys(values, addEmpty);
        } else if (key instanceof String) {
            return sortStringKeys(values);
        } else if (key instanceof Map) {
            if (((Map)key).containsKey("decimal")) {
                return sortDoubleKeys(values);
            } else if (((Map)key).containsKey("year")) {
                return fillDateKeys(values, addEmpty);
            }
        }
        return null;
    }

    /** Format a label for axis according to its type and preferences */
    private static String formatLabel(Object label) {
        if ((label instanceof Map) && ((Map)label).containsKey("year")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mDate = (Map<String, Object>) label;
            int year = (Integer) mDate.get("year");
            int month = (Integer) mDate.get("month");
            int day = (Integer) mDate.get("day");
            String format = Session.current.prefs.getDateFormat();
            return Formatter.formatDate(format, year, month, day);
        }
        return label.toString();
    }

    public static GraphicalView getGraphView(Context ctx,
                                             ModelView view, List<Model> data) {
        String graphType = view.getSubtype();
        Model xAxis = null;
        List<Model> yAxis = null;
        for (Model m : view.getStructure()) {
            String className = m.getClassName();
            if (className.equals("graph.axis.x")) {
                @SuppressWarnings("unchecked")
                    List<Model> axis = (List<Model>) m.get("axis");
                xAxis = axis.get(0);
            } else if (className.equals("graph.axis.y")) {
                @SuppressWarnings("unchecked")
                    List<Model> axis = (List<Model>) m.get("axis");
                yAxis = axis;
            }
        }
        // Create XYSeries from data
        XYMultipleSeriesRenderer renderers = new XYMultipleSeriesRenderer();
        renderers.setYAxisMin(0);
        if (graphType.equals("vbar") || graphType.equals("hbar")
            || graphType.equals("line")) {
            XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
            if (xAxis.hasAttribute("string")) {
                renderers.setXTitle(xAxis.getString("string"));
            } else {
                renderers.setXTitle(xAxis.getString("name"));
            }
            int defaultColorIndex = 0;
            for (Model y : yAxis) {
                // Set series name
                String seriesName = null;
                if (y.hasAttribute("string")) {
                    seriesName = y.getString("string");
                } else {
                    seriesName = y.getString("name");
                }
                XYSeries series = new XYSeries(seriesName);
                XYSeriesRenderer renderer = new XYSeriesRenderer();
                int color = 0;
                if (y.hasAttribute("color")) {
                    try {
                    color = Color.parseColor(y.getString("color"));
                    renderer.setColor(color);
                    } catch (IllegalArgumentException e) {
                        Log.e("Tryton", "Unable to read color "
                              + y.getString("color"), e);
                        renderer.setColor(DEFAULT_COLORS[defaultColorIndex]);
                        defaultColorIndex++;
                        defaultColorIndex %= DEFAULT_COLORS.length;
                    }
                } else {
                    renderer.setColor(DEFAULT_COLORS[defaultColorIndex]);
                    defaultColorIndex++;
                    defaultColorIndex %= DEFAULT_COLORS.length;
                }
                if (y.hasAttribute("fill")) {
                    boolean fill = false;
                    try {
                        fill = (Integer.parseInt(((String) y.get("fill"))) == 1);
                    } catch (NumberFormatException e) {
                        Log.w("Tryton", "Incorrect fill attribute "
                              + y.get("fill"), e);
                    }
                    renderer.setFillBelowLine(fill);
                    renderer.setFillBelowLineColor(color);
                }
                Map<Object, Double> plots = getValues(data, xAxis, y);
                // Set series values
                int i = 0;
                for (Object o : fillKeys(plots, false)) {
                    series.add(i, plots.get(o));
                    renderers.addXTextLabel((double)i, formatLabel(o));
                    i++;
                }
                renderers.setXLabels(0);
                dataset.addSeries(series);
                renderers.addSeriesRenderer(renderer);
            }

            if (graphType.equals("hbar")) {
                renderers.setOrientation(XYMultipleSeriesRenderer.Orientation.VERTICAL);
            } else {
                renderers.setOrientation(XYMultipleSeriesRenderer.Orientation.HORIZONTAL);
            }
            if (graphType.equals("line")) {
                GraphicalView g = ChartFactory.getLineChartView(ctx, dataset, 
                                                                renderers);
                return g;
            } else {
                GraphicalView g = ChartFactory.getBarChartView(ctx, dataset, 
                                                               renderers,
                                                               BarChart.Type.DEFAULT);
                return g;
            }
        } else if (graphType.equals("pie")) {
            // Pie chart supports only one field in y axis
            CategorySeries series = new CategorySeries("");
            for (Model y : yAxis) {
                Map<Object, Double> plots = getValues(data, xAxis, y);
                int defaultColorIndex = 0;
                for (Object o : fillKeys(plots, false)) {
                    series.add(formatLabel(o), plots.get(o));
                    SimpleSeriesRenderer renderer = new SimpleSeriesRenderer();
                    renderer.setColor(DEFAULT_COLORS[defaultColorIndex]);
                    defaultColorIndex++;
                    defaultColorIndex %= DEFAULT_COLORS.length;
                    renderers.addSeriesRenderer(renderer);
                }
                break;
            }
            GraphicalView g = ChartFactory.getPieChartView(ctx, series,
                                                           renderers);
            return g;
        } else {
            Log.e("Tryton", "Unknown graph type " + graphType);
            return null;
        }
    }
}