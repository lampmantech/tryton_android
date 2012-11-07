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
package org.tryton.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import java.util.List;

import org.tryton.client.data.DataCache;
import org.tryton.client.data.Session;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.views.TreeFullAdapter;

public class ToManyEditor extends Activity {

    /** Use a static initializer to pass data to the activity on start.
     * Set the className to edit and the field that is currently edited.
     * This field is one of those in the editedModel in Session. */
    public static void setup(ModelView parentView, String fieldName) {
        parentViewInitializer = parentView;
        fieldNameInitializer = fieldName;
    }
    private static ModelView parentViewInitializer;
    private static String fieldNameInitializer;

    private ModelView parentView;
    private ModelView view;
    private String fieldName;
    private String className;

    private ListView selected;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            this.parentView = (ModelView) state.getSerializable("parentView");
            this.fieldName = (String) state.getSerializable("fieldName");
        } else {
            this.parentView = parentViewInitializer;
            this.fieldName = fieldNameInitializer;
            parentViewInitializer = null;
            fieldNameInitializer = null;
        }
        this.className = this.parentView.getField(this.fieldName).getString("relation");
        // Load views
        this.setContentView(R.layout.tomany);
        this.selected = (ListView) this.findViewById(R.id.tomany_list);
        DataCache db = new DataCache(this);
        Session s = Session.current;
        @SuppressWarnings("unchecked")
        List<Model> data = db.getData(this.className, (List<Integer>) s.editedModel.get(this.fieldName));
        // Load model subview
        this.view = this.parentView.getSubview(this.fieldName).getView("tree");
        TreeFullAdapter adapt = new TreeFullAdapter(view, data);
        this.selected.setAdapter(adapt);
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("parentView", this.parentView);
        outState.putSerializable("fieldName", this.fieldName);
    }

    public void add(View button) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.tomany_add);
        DataCache db = new DataCache(this);
        List<Model> rels = db.list(this.className);
        String[] values = new String[rels.size()];
        for (int i = 0; i < values.length; i++) {
            // TODO: get _rec_name for fields that has no name field
            values[i] = rels.get(i).getString("name");
        }
        b.setItems(values, null);
        b.show();
    }
}