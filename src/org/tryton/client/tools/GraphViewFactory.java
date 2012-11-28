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
                for (Object o : plots.keySet()) {
                    series.add(i, plots.get(o));
                    renderers.addXTextLabel((double)i, o.toString());
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
                for (Object o : plots.keySet()) {
                    series.add(o.toString(), plots.get(o));
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