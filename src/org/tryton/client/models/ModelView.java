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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The representation of a view. */
public class ModelView {

    protected String modelName;
    protected String type;
    protected String arch;
    protected Map<String, Model> fields;
    protected String title;
    protected List<Model> builtFields;


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

    public List<Model> getStructure() {
        return this.builtFields;
    }

    public void build(List<Model> structure) {
        this.builtFields = structure;
    }
}