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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.data.DataCache;
import org.tryton.client.data.DataLoader;
import org.tryton.client.data.Session;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.models.RelField;
import org.tryton.client.tools.AlertBuilder;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.views.TreeFullAdapter;

public class ToManyEditor extends Activity
    implements OnItemLongClickListener, OnItemClickListener, Handler.Callback,
               DialogInterface.OnCancelListener  {

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
    private List<RelField> relFields;
    /** Holder for long click listener */
    private int longClickedIndex;
    private int callId;

    private ListView selected;
    private ProgressDialog loadingDialog;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        AlertBuilder.updateRelogHandler(new Handler(this), this);
        // Init data
        if (state != null) {
            this.parentView = (ModelView) state.getSerializable("parentView");
            this.fieldName = (String) state.getSerializable("fieldName");
            this.callId = state.getInt("callId");
            if (this.callId != 0) {
                DataLoader.update(this.callId, new Handler(this));
                this.showLoadingDialog();
            }
            if (state.containsKey("rel_count")) {
                int count = state.getInt("rel_count");
                this.relFields = new ArrayList<RelField>();
                for (int i = 0; i < count; i++) {
                    this.relFields.add((RelField)state.getSerializable("rel_" + i));
                }
            }
            this.view = (ModelView) state.getSerializable("view");
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
        this.selected.setOnItemClickListener(this);
        this.selected.setOnItemLongClickListener(this);
        Button add = (Button) this.findViewById(R.id.tomany_add);
        if (this.parentView.getField(this.fieldName).getString("type").equals("one2many")) {
            add.setText(R.string.tomany_add_new);
            add.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        create();
                    }
                });
        }
        // Get the view if not set
        if (this.view == null) {
            ModelViewTypes viewTypes = this.parentView.getSubview(this.fieldName);
            if (viewTypes != null && viewTypes.getView("tree") != null) {
                this.view = viewTypes.getView("tree");
                this.loadDataAndMeta();
            } else {
                // View not loaded
                this.loadViewsAndData();
            }
        } else {
            // Load data
            this.loadDataAndMeta();
        }
    }

    public void onResume() {
        super.onResume();
        this.loadData();
    }

    public void onDestroy() {
        super.onDestroy();
        this.hideLoadingDialog();
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("parentView", this.parentView);
        outState.putSerializable("fieldName", this.fieldName);
        outState.putSerializable("view", this.view);
        if (this.relFields != null) {
            outState.putSerializable("rel_count", this.relFields.size());
            for (int i = 0; i < this.relFields.size(); i++) {
                outState.putSerializable("rel_" + i, this.relFields.get(i));
            }
        }
        outState.putInt("callId", this.callId);
    }

    public void showLoadingDialog() {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            this.loadingDialog.setMessage(this.getString(R.string.data_loading));
            this.loadingDialog.setOnCancelListener(this);
            this.loadingDialog.show();
        }
    }

    public void onCancel(DialogInterface dialog) {
        DataLoader.cancel(this.callId);
        this.callId = 0;
        this.loadingDialog = null;
        this.finish();
    }

    /** Hide the loading dialog if shown. */
    public void hideLoadingDialog() {
        if (this.loadingDialog != null) {
            this.loadingDialog.dismiss();
            this.loadingDialog = null;
        }
    }

    private void updateList() {
        TreeFullAdapter adapt = new TreeFullAdapter(view, data);
        this.selected.setAdapter(adapt);
    }

    /** Load views and all data when done (by cascading the calls in handler) */
    private void loadViewsAndData() {
        if (this.callId == 0) {
            this.showLoadingDialog();
            // Load tree view.
            ModelViewTypes viewTypes = this.parentView.getSubview(this.fieldName);
            int viewId = 0;
            if (viewTypes != null) {
                viewId = viewTypes.getViewId("tree");
            }
            this.callId = DataLoader.loadView(this, this.className,
                                              viewId, "tree",
                                              new Handler(this), false);
        }
    }

    /** Load data count and rel fields, required for data.
     * Requires that views are loaded. */
    private void loadDataAndMeta() {
        if (this.callId == 0) {
            this.showLoadingDialog();
            this.callId = DataLoader.loadRelFields(this, this.className,
                                                   new Handler(this),
                                                   false);
        }
    }

    private void loadData() {
        if (this.callId == 0 && this.relFields != null) {
            List<Integer> dataId = this.getIds(false);
            this.callId = DataLoader.loadData(this, this.className, dataId, 
                                              this.relFields, this.view,
                                              new Handler(this), false);
            this.showLoadingDialog();
        }
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
        PickOne.setup(this.parentView, this.fieldName);
        Intent i = new Intent(this, PickOne.class);
        this.startActivity(i);
    }

    /** Open form view to edit or create a model
     * (make sure to configure session before calling it) */
    private void openForm() {
        ModelViewTypes viewTypes = this.parentView.getSubview(this.fieldName);
        if (viewTypes == null) {
            // Use default form view
            FormView.setup(0);
        } else {
            // Check for view or id
            ModelView form = viewTypes.getView("form");
            if (form != null) {
                FormView.setup(form);
            } else {
                int viewId = viewTypes.getViewId("form");
                FormView.setup(viewId);
            }
        }
        Intent i = new Intent(this, FormView.class);
        this.startActivity(i);
    }

    /** Action called on create button. Replaces add for one2many field. */
    public void create() {
        // Open a new form to create the relation
        Model parentField = this.parentView.getField(this.fieldName);
        if (parentField.hasAttribute("relation_field")) {
            String relField = parentField.getString("relation_field");
            Session.current.editNewOne2Many(this.className, this.fieldName,
                                            relField);
        } else {
            Session.current.editNewMany2Many(this.className, this.fieldName);
        }
        this.openForm();
    }

    private void edit(Model model) {
        Model parentField = this.parentView.getField(this.fieldName);
        if (parentField.hasAttribute("relation_field")) {
            // Edit a many2one field
            String relField = parentField.getString("relation_field");
            Session.current.editOne2Many(model, this.fieldName, relField);
        } else {
            Session.current.editMany2Many(model, this.fieldName);
        }
        this.openForm();
    }

    public void onItemClick(AdapterView parent, View v, int position,
                               long id) {
        Model clicked = this.data.get(position);
        this.edit(clicked);
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

    /** Handle loading feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case DataLoader.VIEWS_OK:
            this.callId = 0;
            ModelView view = (ModelView)((Object[])msg.obj)[1];
            this.view = view;
            this.loadDataAndMeta();
            break;
        case DataLoader.RELFIELDS_OK:
            this.callId = 0;
            Object[] ret = (Object[]) msg.obj;
            this.relFields = (List<RelField>) ret[1];
            this.loadData();
            break;
        case DataLoader.DATA_OK:
            this.callId = 0;
            ret = (Object[]) msg.obj;
            List<Model> data = (List<Model>) ret[1];
            // Add one2many data if any
            Model m = Session.current.tempModel;
            List<Model> one2many = m.getOne2ManyOperations(this.fieldName);
            if (one2many != null) {
                // Add new records and merge edited ones
                for (Model newM : one2many) {
                    if (newM.hasAttribute("id")) {
                        // Replace the existing one
                        Integer id = (Integer) newM.get("id");
                        for (int i = 0; i < data.size(); i++) {
                            Model oldM = data.get(i);
                            if (oldM.get("id").equals(id)) {
                                data.remove(i);
                                data.add(i, newM);
                                break;
                            }
                        }
                    } else {
                        // New, add
                        data.add(newM);
                    }
                }
            }
            this.data = data;
            this.updateList();
            this.hideLoadingDialog();
            break;
        case DataLoader.VIEWS_NOK:
        case DataLoader.DATA_NOK:
        case DataLoader.RELFIELDS_NOK:
            this.hideLoadingDialog();
            this.callId = 0;
            Exception e = (Exception) msg.obj;
            if (!AlertBuilder.showUserError(e, this)
                && !AlertBuilder.showUserError(e, this)) {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.error);
                b.setMessage(R.string.network_error);
                b.show();
                ((Exception)msg.obj).printStackTrace();
            }
            break;
        case TrytonCall.NOT_LOGGED:
            this.callId = 0;
            // Ask for relog
            this.hideLoadingDialog();
            AlertBuilder.showRelog(this, new Handler(this));
            break;
        case AlertBuilder.RELOG_CANCEL:
            this.finish();
            break;
        case AlertBuilder.RELOG_OK:
            this.loadViewsAndData();
            break;
        }
        return true;
    }
    
    /** Handler for item long click actions */
    public void onLongClickDialog(DialogInterface dialog, int which) {
        // Delete selected item
        if (Session.current.isEditingOne2Many()) {
            Session.current.deleteOne2Many(this.data.get(this.longClickedIndex));
        } else {
            // It is a many2many field
            List<Integer> ids = this.getIds(true);
            ids.remove(this.longClickedIndex);
        }
        this.data.remove(this.longClickedIndex);
        this.updateList();
    }

}