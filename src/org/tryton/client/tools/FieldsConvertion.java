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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.tryton.client.data.DataCache;
import org.tryton.client.models.Model;
import org.tryton.client.models.RelField;

/** Manipulate field values in different representations
 * and convert models to one representation to an other. */
public class FieldsConvertion {

    /** Convert from server received float/decimal field value to double */
    public static Double numericToDouble(Map decimal) {
        if (decimal.containsKey("decimal")) {
            if (decimal.get("decimal") == null
                || decimal.get("decimal").equals("")) {
                return null;
            }
            try {
                Double val = Double.parseDouble((String)decimal.get("decimal"));
                return val.doubleValue();
            } catch (NumberFormatException e) {
                Log.w("Tryton", "Ignoring numeric " + decimal.get("decimal")
                      + " value", e);
                return null;
            }
        } else {
            Log.e("Tryton", "Converting an invalid map to double");
            return null;
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

    public static Map<String, Object> intAToDate(int[] intA) {
        Map<String, Object> date = new TreeMap<String, Object>();
        date.put("year", intA[0]);
        date.put("month", intA[1]);
        date.put("day", intA[2]);
        date.put("__class__", "date");
        return date;
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
    @SuppressWarnings("unchecked")
    public static Model modelToSend(Model model, Model previousModel,
                                        Context ctx) {
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
                    Double val = numericToDouble(mVal);
                    if (val == null) {
                        send.set(attr, JSONObject.NULL);
                    } else {
                        // Format the decimal map to JSONObject
                        JSONObject dec = new JSONObject();
                        try {
                            dec.put("__class__", "Decimal");
                            dec.put("decimal", mVal.get("decimal"));
                        } catch (Exception e) {}
                        send.set(attr, dec);
                    }
                } else {
                    // What's that?
                    Log.e("Tryton", "Unrecognized map attribute " + mVal);
                }
            } else if (oVal instanceof List) {
                // x2many, add action
                List<Integer> allIds = (List<Integer>) oVal;
                List<Integer> ids = new ArrayList<Integer>();
                ids.addAll(allIds);
                // Remove null values in ids as they are handled separately
                while (ids.remove(null)) { /* loop on remove */ }
                JSONArray cmds = new JSONArray();
                // Check created/updated submodels (one2many only)
                System.out.println(attr + " " + model.getOne2ManyOperations(attr));
                if (model.getOne2ManyOperations(attr) != null) {
                    for (Model m : model.getOne2ManyOperations(attr)) {
                        JSONArray cmd = new JSONArray();
                        if (m.hasAttribute("id")) {
                            // Update
                            cmd.put("write");
                        } else {
                            // Create
                            cmd.put("create");
                        }
                        // Set attributes
                        JSONObject args = new JSONObject();
                        for (String mAttr : m.getAttributeNames()) {
                            try {
                                args.put(mAttr, m.get(mAttr));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        cmd.put(args);
                        cmds.put(cmd);
                    }
                }
                // Compare with the previous list for commands
                if (previousModel == null) {
                    if (ids.size() > 0) {
                        // Size > 0 to not interfere with write/create
                        // Creation, just set
                        JSONArray cmd = new JSONArray();
                        cmd.put("set");
                        JSONArray args = new JSONArray();
                        for (int id : ids) {
                            args.put(id);
                        }
                        cmd.put(args);
                        cmds.put(cmd);
                    }
                } else {
                    List<Integer> oldIds = (List<Integer>)previousModel.get(attr);
                    JSONArray dels = new JSONArray();
                    JSONArray adds = new JSONArray();
                    // Check for deletions
                    for (int id : oldIds) {
                        if (ids.indexOf(id) == -1) {
                            dels.put(id);
                        }
                    }
                    if (dels.length() > 0) {
                        JSONArray cmd = new JSONArray();
                        // Check type, delete for one2many, unlink for many2many
                        DataCache db = new DataCache(ctx);
                        RelField rel = db.getRelField(model.getClassName(),
                                                      attr);
                        if (rel.getType().equals("many2many")) {
                            cmd.put("unlink");
                        } else {
                            cmd.put("delete");
                        }
                        cmd.put(dels);
                        cmds.put(cmd);
                    }
                    // Check for addition
                    for (int id : ids) {
                        if (oldIds.indexOf(id) == -1) {
                            adds.put(id);
                        }
                    }
                    if (adds.length() > 0) {
                        JSONArray cmd = new JSONArray();
                        cmd.put("add");
                        cmd.put(adds);
                        cmds.put(cmd);
                    }
                }
                send.set(attr, cmds);
            } else {
                send.set(attr, oVal);
            }
        }
        return send;
    }
}