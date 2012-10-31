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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.Serializable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The representation of a view of a given type. It is built in two steps
 * first get the information from the server (arch and so on), then
 * build models which represent the arch in data shape.
 * See TrytonCall for the generation with ArchParser. */
public class ModelView implements Serializable {

    protected String modelName;
    protected String type;
    protected String arch;
    /** The fields given by the server, indexed by name */
    protected Map<String, Model> fields;
    protected String title;
    /** The arch in data shape. Accessible only once build is called.
     * It contains fields form the fields map with some extra data. */
    protected List<Model> builtFields;

    /** Create the view from server data. It must be built to be used
     *  in the application */
    public ModelView(JSONObject json) throws JSONException {
        this.arch = json.getString("arch");
        this.type = json.getString("type");
        this.modelName = json.getString("model");
        this.fields = new HashMap<String, Model>();
        JSONObject fields = json.getJSONObject("fields");
        JSONArray fieldNames = fields.names();
        for (int i = 0; i < fieldNames.length(); i++) {
            String fieldName = fieldNames.getString(i);
            JSONObject field = fields.getJSONObject(fieldName);
            Model fieldModel = new Model("ir.model.field", field);
            this.fields.put(fieldName, fieldModel);
        }
    }

    public String getModelName() {
        return this.modelName;
    }

    public String getType() {
        return this.type;
    }

    /** Get the view title. Is valid only once the view is built. */
    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArch() {
        return this.arch;
    }

    public Map<String, Model> getFields() {
        return this.fields;
    }

    public Model getField(String name) {
        return this.fields.get(name);
    }

    public List<Model> getStructure() {
        return this.builtFields;
    }

    /** Build the structure. It represent arch and fields
     * in a combined way. See ArchParser. */
    public void build(List<Model> structure) {
        this.builtFields = structure;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof ModelView
                && ((ModelView)o).modelName.equals(this.modelName)
                && ((ModelView)o).type.equals(this.type)
                && ((ModelView)o).arch.equals(this.arch));
    }
}