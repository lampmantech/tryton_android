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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

/** The representation of a generic model. It can either be some data
 * fields definition... */
public class Model implements Serializable {

    private String className;
    private Map<String, Object> attributes;

    public Model(String className) {
        this.className = className;
        this.attributes = new HashMap<String, Object>();
    }
    
    /** Convert JSONObject to a map of attributes. */
    private Map<String, Object> convertJSONObject(JSONObject o) {
        if (o == JSONObject.NULL) {
            return null;
        }
        Map<String, Object> value = new HashMap<String, Object>();
        JSONArray keys = o.names();
        if (keys == null) {
            return value;
        }
        for (int i = 0; i < keys.length(); i++) {
            try {
                String name= keys.getString(i);
                Object subvalue = o.get(name);
                if (subvalue == JSONObject.NULL) {
                    value.put(name, null);
                } else if (subvalue instanceof JSONObject) {
                    Map<String, Object> mValue = this.convertJSONObject((JSONObject) subvalue);
                    value.put(name, mValue);
                } else if (subvalue instanceof JSONArray) {
                    List<Object> lValue = this.convertJSONArray((JSONArray) subvalue);
                    value.put(name, lValue);
                } else {
                    value.put(name, subvalue);
                }
            } catch (JSONException e) {
                // Unreachable
            }
        }
        return value;
    }

    /** Convert JSONArray to List. */
    private List<Object> convertJSONArray(JSONArray o) {
        List<Object> value = new ArrayList<Object>();
        for (int i = 0; i < o.length(); i++) {
            try {
                Object subvalue = o.get(i);
                if (subvalue == JSONObject.NULL) {
                    value.add(null);
                } else if (subvalue instanceof JSONObject) {
                    Map mValue = this.convertJSONObject((JSONObject) subvalue);
                    value.add(mValue);
                } else if (subvalue instanceof JSONArray) {
                    List lValue = this.convertJSONArray((JSONArray) subvalue);
                    value.add(lValue);
                } else {
                    value.add(subvalue);
                }
            } catch (JSONException e) {
                // Unreachable
            }
        }
        return value;
    }
    
    public Model(String className, JSONObject model) {
        this.className = className;
        this.attributes = this.convertJSONObject(model);
    }

    public String getClassName() {
        return this.className;
    }
    
    /** Get all attribute names */
    public Set<String> getAttributeNames() {
        return this.attributes.keySet();
    }

    /** Get the value of an attribute */
    public Object get(String attributeName) {
        return this.attributes.get(attributeName);
    }

    /** Get the value of a string attribute.
        Returns null if it is not a string */
    public String getString(String attributeName) {
        Object value = this.attributes.get(attributeName);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    public void set2One(String name, Model value) {
        this.attributes.put(name, value);
    }

    /** Add a model to a many2many or one2many fields.
        If the field is not yet a many2many or one2many (for example ids)
        it is erased and replaced. */
    @SuppressWarnings("unchecked")
    public void add2Many(String name, Model value) {
        if (!(this.attributes.get(name) instanceof List)) {
            List currValue = (List) this.attributes.get(name);
            if (!(currValue.size() > 0 && currValue.get(0) instanceof Map)) {
                this.attributes.put(name, new ArrayList<Model>());
            }
        }
        ((List<Model>)this.attributes.get(name)).add(value);
    }
    
    /** Set human readable form for debugging */
    @Override
    public String toString() {
        Object oName = this.attributes.get("name");
        if (oName != null && oName instanceof String) {
            return this.className + ":" + ((String) oName);
        } else {
            return super.toString();
        }
    }
}