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
package org.tryton.client.models;

import org.json.JSONException;
import org.json.JSONObject;

/** User preferences as set on the server */
public class Preferences {

    private JSONObject source;

    public Preferences(JSONObject source) {
        this.source = source;
    }

    public String getDateFormat() {
        try {
            JSONObject locale = this.source.getJSONObject("locale");
            return locale.getString("date");
        } catch (JSONException e) {
            return null;
        }
    }

    public char getThousandsSeparator() {
        try {
            JSONObject locale = this.source.getJSONObject("locale");
            String sep = locale.getString("thousands_sep");
            if (sep.length() > 0) {
                return locale.getString("thousands_sep").charAt(0);
            } else {
                return ' ';
            }
        } catch (JSONException e) {
            return ' ';
        }
    }

    public char getDecimalPoint() {
        try {
            JSONObject locale = this.source.getJSONObject("locale");
            String decPoint = locale.getString("decimal_point");
            if (decPoint.length() > 0) {
                return locale.getString("decimal_point").charAt(0);
            } else {
                return '.';
            }
        } catch (JSONException e) {
            return '.';
        }
    }

    public JSONObject json() {
        return this.source;
    }
}