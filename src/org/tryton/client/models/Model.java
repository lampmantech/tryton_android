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
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

/** The representation of a generic model */
public class Model implements Serializable {

    private String className;
    private Map<String, Object> attributes;

    public Model(String className) {
        this.className = className;
        this.attributes = new HashMap<String, Object>();
    }
    
    public Model(String className, JSONObject model) {
        this.className = className;
        this.attributes = new HashMap<String, Object>();
        JSONArray keys = model.names();
        for (int i = 0; i < keys.length(); i++) {
            try {
                String name= keys.getString(i);
                this.attributes.put(name, model.get(name));
            } catch (JSONException e) {
                // Unreachable
            }
        }
    }

    public String getClassName() {
        return this.className;
    }
    
    public Set<String> getAttributeNames() {
        return this.attributes.keySet();
    }

    public Object get(String attributeName) {
        return this.attributes.get(attributeName);
    }
}