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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.data.DataCache;
import org.tryton.client.data.Session;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.views.TreeFullAdapter;

public class ToManyEditor extends Activity implements OnItemLongClickListener {

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
    private List<Model> data;
    /** Holder for long click listener */
    private int longClickedIndex;
    /** Holder for add click listener */
    private List<Model> rels;

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
        Button add = (Button) this.findViewById(R.id.tomany_add);
        if (this.parentView.getField(this.fieldName).getString("type").equals("one2many")) {
            add.setText(R.string.tomany_add_new);
            add.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        create();
                    }
                });
        }
        // Load model subview
        this.view = this.parentView.getSubview(this.fieldName).getView("tree");
        if (this.view == null) {
            this.view = this.parentView.getSubview(this.fieldName).getView("form");
        }
        this.refresh();
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("parentView", this.parentView);
        outState.putSerializable("fieldName", this.fieldName);
    }

    private void refresh() {
        DataCache db = new DataCache(this);
        List<Integer> dataId = this.getIds(false);
        this.data = db.getData(this.className, dataId);

        TreeFullAdapter adapt = new TreeFullAdapter(view, data);
        this.selected.setAdapter(adapt);
        this.selected.setOnItemLongClickListener(this);
    }
    
    /** Get ids of the registered items from the edited model.
     * Set forUpdate to force getting from session tempModel. */
    @SuppressWarnings("unchecked")
    private List<Integer> getIds(boolean forUpdate) {
        Session s = Session.current;
        List<Integer> ids = (List<Integer>) s.tempModel.get(this.fieldName);
        if (ids == null) {
            if (forUpdate) {
                // Make a copy of the original to edit
                ids = new ArrayList<Integer>();
                if (s.editedModel != null) {
                    ids.addAll((List<Integer>)s.editedModel.get(this.fieldName));
                }
                s.tempModel.set(this.fieldName, ids);
            } else {
                if (s.editedModel != null) {
                    ids = (List<Integer>) s.editedModel.get(this.fieldName);
                } else {
                    ids = new ArrayList<Integer>();
                }
            }
        }
        return ids;
    }

    /** Action called on add button for many2many field (linked in xml) */
    public void add(View button) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.tomany_add);
        DataCache db = new DataCache(this);
        List<Model> rels = db.list(this.className);
        // Remove already selected values
        for (Integer id : this.getIds(false)) {
            for (int i = 0; i < rels.size(); i++) {
                if (rels.get(i).get("id").equals(id)) {
                    rels.remove(i);
                    break;
                }
            }
        }
        this.rels = rels;
        String[] values = new String[rels.size()];
        for (int i = 0; i < values.length; i++) {
            if (rels.get(i).hasAttribute("rec_name")
                && rels.get(i).get("rec_name") != null) {
                values[i] = rels.get(i).getString("rec_name");
            } else {
                Log.e("Tryton", "No rec_name found on " + rels.get(i));
            }
        }
        DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    onAddDialog(dialog, which);
                }
            };
        b.setItems(values, l);
        b.setNeutralButton(R.string.tomany_add_new, l);
        b.show();
    }

    /** Action called on create button. */
    public void create() {
        // Open a new form to create the relation
        Session.current.editNewModel(this.className);
        FormView.setup(this.parentView.getSubview(this.fieldName));
        Intent i = new Intent(this, FormView.class);
        this.startActivity(i);
    }

    public boolean onItemLongClick(AdapterView parent, View v, int position,
                                   long id) {
        this.longClickedIndex = position;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        String items[];
        Model field = this.parentView.getField(this.fieldName);
        if (this.parentView.getField(this.fieldName).getString("type").equals("one2many")) {
            items = new String[]{this.getString(R.string.tomany_delete)};
        } else {
            // many2many
            items = new String[]{this.getString(R.string.tomany_remove)};
        }
        String title;
        if (this.data.get(position).hasAttribute("rec_name")) {
            title = this.data.get(position).getString("rec_name");
            b.setTitle(title);
        }
        b.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    onLongClickDialog(dialog, which);
                }
            });
        b.setNegativeButton(R.string.general_cancel, null);
        b.show();
        return true;
    }
    
    /** Handler for item long click actions */
    public void onLongClickDialog(DialogInterface dialog, int which) {
        // Delete selected item
        Session s = Session.current;
        List<Integer> ids = this.getIds(true);
        ids.remove(this.longClickedIndex);
        this.refresh();
    }

    public void onAddDialog(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            create();
            return;
        }
        Model clickedModel = this.rels.get(which);
        Integer id = (Integer) clickedModel.get("id");
        List<Integer> ids = this.getIds(true);
        ids.add(id);
        this.refresh();
    }
}