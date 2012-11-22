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
import java.util.TreeMap;
import java.util.Map;
import java.util.Set;

/** This is just an aggregation of views per type for a model name. */
public class ModelViewTypes implements Serializable {

    /** Autogenerated serial UID */
    static final long serialVersionUID = 4878345939938015119L;

    private String modelName;
    private Map<String, Integer> viewIds;
    private Map<String, ModelView> views;

    public ModelViewTypes(String modelName) {
        this.modelName = modelName;
        this.viewIds = new TreeMap<String, Integer>();
        this.views = new TreeMap<String, ModelView>();
    }
    
    /** Add a subview id, without view */
    public void putViewId(String type, int id) {
        this.viewIds.put(type, id);
    }
    /** Add a subview (also add its id) */
    public void putView(String type, ModelView view) {
        this.views.put(type, view);
        this.viewIds.put(type, view.getId());
    }

    public ModelView getView(String type) {
        return this.views.get(type);
    }

    public int getViewId(String type) {
        Integer id = this.viewIds.get(type);
        if (id == null) {
            return 0;
        } else {
            return id.intValue();
        }
    }

    public Set<String> getTypes() {
        return this.views.keySet();
    }

    public String getModelName() {
        return this.modelName;
    }

    public List<String> getAllFieldNames() {
        List<String> fields = new ArrayList<String>();
        for (String type : this.getTypes()) {
            ModelView v = this.views.get(type);
            for (String fieldName : v.getFields().keySet()) {
                if (!fields.contains(fieldName)) {
                    fields.add(fieldName);
                }
            }
        }
        return fields;
    }
}