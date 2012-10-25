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
import android.util.Log;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.tryton.client.R;
import org.tryton.client.models.Model;
import org.tryton.client.models.Preferences;

/** Factory to convert various data types to string for tree views. */
public class TreeViewFactory {

    public static String getView(Model field, Model data,
                                 Preferences prefs,Context ctx) {
        String name = (String) field.get("name");
        if (name == null || data.get(name) == null) {
            return null;
        }
        String type = (String) field.get("type");
        Object value = data.get(name);
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (type == null) {
            return null;
        } else if (type.equals("boolean")) {
            boolean bval = (Boolean) value;
            if (bval) {
                return ctx.getString(R.string.general_true);
            } else {
                return ctx.getString(R.string.general_false);
            }
        } else if (type.equals("integer") || type.equals("biginteger")) {
            long lval = 0;
            if (value instanceof Integer) {
                lval = ((Integer)value).longValue();
            } else {
                lval = (Long) value;
            }
            return String.valueOf(lval);
        } else if (type.equals("char") || type.equals("text")) {
            return (String) value;
        } else if (type.equals("sha")) {
            System.out.println("Sha type not supported yet");
        } else if (type.equals("float") || type.equals("numeric")) {
            double dval = 0;
            if (value instanceof JSONObject) {
                JSONObject jsVal = (JSONObject) value;
                try {
                    dval = Double.parseDouble(jsVal.getString("decimal"));
                } catch (JSONException e) {
                    // TODO: exception
                }
            } else {
                dval = (Double)value;
            }
            // Set default format
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setDecimalSeparator(prefs.getDecimalPoint());
            symbols.setGroupingSeparator(prefs.getThousandsSeparator());
            String format = "###,##0.###";
            // Get digits format
            String digits = (String) field.get("digits");
            if (digits != null && digits != JSONObject.NULL) {
                digits = digits.substring(1, digits.length() - 1);
                String[] split = digits.split(",");
                int intNum = Integer.parseInt(split[0].trim());
                int decNum = Integer.parseInt(split[1].trim());
                format = "###,##0";
                format += ".";
                for (int i = 0; i < decNum; i++) {
                    format += "0";
                }
            }
            DecimalFormat formatter = new DecimalFormat(format, symbols);
            return formatter.format(dval);
        } else if (type.equals("date")) {
            JSONObject jsDate = (JSONObject) value;
            try {
                int year = jsDate.getInt("year");
                int month = jsDate.getInt("month");
                int day = jsDate.getInt("day");
                // TODO: use preferences for date formatting
                String format = prefs.getDateFormat();
                if (format != null) {
                    String result = format;
                    result = result.replace("%d", String.format("%02d", day));
                    result = result.replace("%m", String.format("%02d", month));
                    result = result.replace("%Y", String.format("%04d", year));
                    return result;
                }
                return String.format("%04d/%02d/%02d", year, month, day);
            } catch (JSONException e) {
                // TODO: invalid date
            }
        } else if (type.equals("datetime")) {
            JSONObject jsDate = (JSONObject) value;
            try {
                int year = jsDate.getInt("year");
                int month = jsDate.getInt("month");
                int day = jsDate.getInt("day");
                int hour = jsDate.getInt("hour");
                int minute = jsDate.getInt("minute");
                int second = jsDate.getInt("second");
                // TODO: use preferences for date formatting
                return String.format("%04d/%02d/%02d %02d:%02d:%02d",
                                     year, month, day, hour, minute, second);
            } catch (JSONException e) {
                // TODO: invalid date
            }
        } else if (type.equals("time")) {
            JSONObject jsDate = (JSONObject) value;
            try {
                int hour = jsDate.getInt("hour");
                int minute = jsDate.getInt("minute");
                int second = jsDate.getInt("second");
                // TODO: use preferences for date formatting
                return String.format("%02d:%02d:%02d",
                                     hour, minute, second);
            } catch (JSONException e) {
                // TODO: invalid date
            }
        } else if (type.equals("binary")) {
            System.out.println("Binary type not supported yet");
            return "(bin)";
        } else if (type.equals("selection")) {
            JSONArray selection = (JSONArray) field.get("selection");
            try {
                for (int i = 0; i < selection.length(); i++) {
                    JSONArray couple = selection.getJSONArray(i);
                    if (couple.getString(0).equals(value)) {
                        return couple.getString(1);
                    }
                }
                Log.w("Tryton", "Selection value for " + value.toString()
                      + " not found");
            } catch (JSONException e) {
                // TODO: invalid select
            }
        } else if (type.equals("reference")) {
            System.out.println("Reference type not supported yet");
        } else if (type.equals("many2one")) {
            System.out.println("many2one type not supported yet");
        } else if (type.equals("many2many") || type.equals("one2many")) {
            JSONArray jsMany = (JSONArray) value;
            return "( " + jsMany.length() + " )";
        } else if (type.equals("one2one")) {
            System.out.println("one2one type not supported yet");
        } else if (type.equals("function")) {
            System.out.println("Function type not supported yet");
        } else if (type.equals("property")) {
            System.out.println("Property type not supported yet");
        } else {
            System.out.println("Unknown type " + type);
        }
        return value.toString();
    }
}