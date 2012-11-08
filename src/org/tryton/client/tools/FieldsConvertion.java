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

import android.util.Log;
import java.util.Map;

import org.tryton.client.models.Model;

/** Manipulate field values in different representations
 * and convert models to one representation to an other. */
public class FieldsConvertion {

    /** Convert from server received float/decimal field value to double */
    public static double numericToDouble(Map decimal) {
        if (decimal.containsKey("decimal")) {
            Double val = Double.parseDouble((String)decimal.get("decimal"));
            return val.doubleValue();
        } else {
            Log.e("Tryton", "Converting an invalid map to double");
            return 0;
        }
    }

    /** Convert a server received or client-generated date to an int array
     * [year, month, day] */
    public static int[] dateToIntA(Map date) {
        if (date.containsKey("year") && date.containsKey("month")
            && date.containsKey("day")) {
            return new int[]{(Integer)date.get("year"),
                             (Integer)date.get("month"),
                             (Integer)date.get("day")};
        } else {
            Log.e("Tryton", "Converting an invalid date to int[]");
            return new int[]{0, 0, 0};
        }
    }

    /** Convert a server received or client-generated time to an int array
     * [hour, minute] (second not supported yet) */
    public static int[] timeToIntA(Map time) {
        if (time.containsKey("hour") && time.containsKey("minute")) {
            return new int[]{(Integer) time.get("hour"),
                             (Integer) time.get("minute")};
        } else {
            Log.e("Tryton", "Converting an invalid time to int[]");
            return new int[]{0, 0};
        }
    }

    /** Convert a server received or client-generated datetime to an int array
     * [year, month, day, hour, minute] */
    public static int[] dateTimeToIntA(Map datetime) {
        if (datetime.containsKey("year") && datetime.containsKey("month")
            && datetime.containsKey("day") && datetime.containsKey("hour")
            && datetime.containsKey("minute")) {
            return new int[]{(Integer)datetime.get("year"),
                             (Integer)datetime.get("month"),
                             (Integer)datetime.get("day"),
                             (Integer)datetime.get("hour"),
                             (Integer)datetime.get("minute")};
        } else {
            Log.e("Tryton", "Converting an invalid datetime to int[]");
            return new int[]{0, 0, 0, 0, 0};
        }
    }

    /** Convert a server received or client-generated date to a string suitable
     * to send to the server (year, month, day) */
    public static String dateToStr(Map date) {
        if (date.containsKey("year") && date.containsKey("month")
            && date.containsKey("day")) {
            return (Integer)date.get("year") + "-"
                + (Integer)date.get("month") + "-"
                + (Integer)date.get("day");
        } else {
            Log.e("Tryton", "Converting an invalid date to string");
            return "0-0-0";
        }
    }

    /** Convert a server received or client-generated time to a string suitable
     * to send to the server (hour, minute, second) */
    public static String timeToStr(Map time) {
        if (time.containsKey("hour") && time.containsKey("minute")) {
            return (Integer)time.get("hour") + ":"
                + (Integer)time.get("minute") + ":"
                + "0";
        } else {
            Log.e("Tryton", "Converting an invalid time to string");
            return "0:0:0";
        }
    }

    /** Convert a server received or client-generated datetime
     * to a string suitable to send to the server
     * (year, month, day, hour, minute, second) */
    public static String dateTimeToStr(Map datetime) {
        if (datetime.containsKey("year") && datetime.containsKey("month")
            && datetime.containsKey("day") && datetime.containsKey("hour")
            && datetime.containsKey("minute")) {
            return (Integer)datetime.get("year") + "-"
                + (Integer)datetime.get("month") + "-"
                + (Integer)datetime.get("day") + " "
                + (Integer)datetime.get("hour") + ":"
                + (Integer)datetime.get("minute") + ":"
                + "0";
        } else {
            Log.e("Tryton", "Converting an invalid datetime to string");
            return "0-0-0 0:0:0";
        }
    }

    /** Convert a model received by the server or generated by the client
     * to a model suitable to send back to the server */
    public static Model modelToSend(Model model) {
        Model send = new Model(model.getClassName());
        for (String attr : model.getAttributeNames()) {
            Object oVal = model.get(attr);
            if (oVal instanceof Map) {
                // Must convert date, time and datetime value
                Map mVal = (Map) oVal;
                if (mVal.containsKey("year")) {
                    String val;
                    if (mVal.containsKey("hour")) {
                        val = dateTimeToStr(mVal);
                    } else {
                        val = dateToStr(mVal);
                    }
                    send.set(attr, val);
                } else if (mVal.containsKey("hour")) {
                    String val = timeToStr(mVal);
                    send.set(attr, val);
                } else if (mVal.containsKey("decimal")) {
                    System.out.println("Decimal sending not supported (non-standard JSON conversion to Decimal('number') required)");
                } else {
                    // Neither date, time nor datetime
                    send.set(attr, oVal);    
                }
            } else {
                send.set(attr, oVal);
            }
        }
        return send;
    }
}