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
import android.widget.Button;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
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

/** Activity to pick one record in a list, mainly for one2many fields. */
public class PickOne extends Activity
    implements OnItemClickListener, Handler.Callback,
               DialogInterface.OnCancelListener {

    /** Use a static initializer to pass data to the activity on start. */
    public static void setup(ModelView parentView, String fieldName) {
        parentViewInitializer = parentView;
        fieldNameInitializer = fieldName;
    }
    private static ModelView parentViewInitializer;
    private static String fieldNameInitializer;

    private ModelView parentView;
    private String fieldName;
    private ModelView view;
    private String className;
    private int totalDataCount = -1;
    private int dataOffset;
    private List<Model> data;
    private List<RelField> relFields;
    /** Holder for long click listener */
    private int longClickedIndex;
    /** Holder for add click listener */
    private List<Model> rels;
    private int callCountId;
    private int callDataId;

    private ListView recordList;
    private TextView pagination;
    private ImageButton nextPage, previousPage;
    private ProgressDialog loadingDialog;

    private boolean isOneValue() {
        Model field = this.parentView.getField(this.fieldName);
        String type = (String) field.get("type");
        return type.equals("many2one") || type.equals("one2one");
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            this.parentView = (ModelView) state.getSerializable("parentView");
            this.fieldName = (String) state.getSerializable("fieldName");
            this.view = (ModelView) state.getSerializable("view");
            if (this.callCountId != 0) {
                DataLoader.update(this.callCountId, new Handler(this));
                this.showLoadingDialog();
            }
            if (this.callDataId != 0) {
                DataLoader.update(this.callDataId, new Handler(this));
                this.showLoadingDialog();
            }
        } else {
            this.parentView = parentViewInitializer;
            this.fieldName = fieldNameInitializer;
            parentViewInitializer = null;
            fieldNameInitializer = null;
        }
        this.className = this.parentView.getField(this.fieldName).getString("relation");
        // Load views
        this.setContentView(R.layout.pickone);
        this.recordList = (ListView) this.findViewById(R.id.pickone_list);
        this.recordList.setOnItemClickListener(this);
        this.pagination = (TextView) this.findViewById(R.id.pickone_pagination);
        this.nextPage = (ImageButton) this.findViewById(R.id.pickone_next_btn);
        this.previousPage = (ImageButton) this.findViewById(R.id.pickone_prev_btn);
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
        // Load data if there isn't anyone or setup the list
        // or update existing data
        if (this.relFields == null || this.data == null) {
            this.loadDataAndMeta();
        } else {
            this.loadData();
        }
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
        outState.putInt("callCountId", this.callCountId);
        outState.putInt("callDataId", this.callDataId);
    }

    /** Update the display list and header with loaded data.
     * A call to hideLoadingDialog should be done around it as it means
     * the data are all there. */
    private void updateList() {
        // Update paging display
        String format = this.getString(R.string.tree_pagination);
        int start = 0;
        if (this.data.size() > 0) {
            start = this.dataOffset + 1;
        }
        this.pagination.setText(String.format(format, start,
                                              this.dataOffset + this.data.size(),
                                              this.totalDataCount));
        if (this.dataOffset == 0) {
            this.previousPage.setVisibility(View.INVISIBLE);
        } else {
            this.previousPage.setVisibility(View.VISIBLE);
        }
        if (this.dataOffset + this.data.size() < this.totalDataCount) {
            this.nextPage.setVisibility(View.VISIBLE);
        } else {
            this.nextPage.setVisibility(View.INVISIBLE);
        }
        // Update data
        ModelViewTypes views = this.parentView.getSubview(this.fieldName);
        ModelView subview = null;
        if (views != null) {
            subview = views.getView("tree");
            if (subview == null) {
                subview = views.getView("form");
            }
        }
        TreeFullAdapter sumadapt = new TreeFullAdapter(subview, this.data);
        this.recordList.setAdapter(sumadapt);
    }

    public void prevPage(View button) {
        this.dataOffset -= TreeView.PAGING_SUMMARY;
        if (this.dataOffset < 0) {
            this.dataOffset = 0;
        }
        this.loadData();
    }

    public void nextPage(View button) {
        int maxOffset = this.totalDataCount;
        this.dataOffset += TreeView.PAGING_SUMMARY;
        maxOffset -= TreeView.PAGING_SUMMARY;
        this.dataOffset = Math.min(this.dataOffset, maxOffset);
        this.loadData();
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
        DataLoader.cancel(this.callCountId);
        DataLoader.cancel(this.callDataId);
        this.callDataId = 0;
        this.callCountId = 0;
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

    /** Load views and all data when done (by cascading the calls in handler) */
    private void loadViewsAndData() {
        if (this.callDataId == 0) {
            this.showLoadingDialog();
            // Load tree view.
            ModelViewTypes viewTypes = this.parentView.getSubview(this.fieldName);
            int viewId = 0;
            if (viewTypes != null) {
                viewId = viewTypes.getViewId("tree");
            }
            this.callDataId = DataLoader.loadView(this, this.className,
                                                  viewId, "tree",
                                                  new Handler(this), false);
        }
    }

    /** Load data count and rel fields, required for data.
     * Requires that views are loaded. */
    private void loadDataAndMeta() {
        if (this.callCountId == 0) {
            this.showLoadingDialog();
            this.callCountId = DataLoader.loadDataCount(this, this.className,
                                                        new Handler(this),
                                                        false);
        }
        if (this.callDataId == 0) {
            this.showLoadingDialog();
            this.callDataId = DataLoader.loadRelFields(this, this.className,
                                                       new Handler(this),
                                                       false);
        }
    }

    private void loadData() {
        if (this.callDataId == 0) {
            int count = TreeView.PAGING_SUMMARY;
            int expectedSize = Math.min(this.totalDataCount - this.dataOffset,
                                        count);
            ModelViewTypes views = this.parentView.getSubview(this.fieldName);
            DataLoader.loadData(this, this.className, this.dataOffset,
                                count, expectedSize, this.relFields, views,
                                new Handler(this), false);
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

    @SuppressWarnings("unchecked")
    public void onItemClick(AdapterView parent, View v, int position,
                               long id) {
        Model clicked = this.data.get(position);
        int clickedId = (Integer) clicked.get("id");
        if (this.isOneValue()) {
            Session.current.tempModel.set(this.fieldName, clickedId);
            Session.current.tempModel.set2One(this.fieldName, clicked);
        } else {
            Model edit = Session.current.editedModel;
            Model tmp = Session.current.tempModel;
            if (tmp.get(this.fieldName) == null) {
                List<Integer> ids = new ArrayList<Integer>();
                if (edit.get(this.fieldName) != null) {
                    List<Integer> eIds = (List<Integer>)edit.get(this.fieldName);
                    ids.addAll(eIds);
                }
                if (!ids.contains(clickedId)) {
                    ids.add(clickedId);
                    tmp.set(this.fieldName, ids);
                }
            } else {
                List<Integer> ids = (List<Integer>)tmp.get(this.fieldName);
                if (!ids.contains(clickedId)) {
                    ids.add(clickedId);
                }
            }
        }
        this.finish();
    }

    /** Action called on create button. */
    public void create(View button) {
        // Open a new form to create the relation
        Model parentField = this.parentView.getField(this.fieldName);
        if (parentField.hasAttribute("relation_field")) {
            String relField = parentField.getString("relation_field");
            Session.current.editNewModel(this.className, this.fieldName,
                                         relField);
        } else {
            Session.current.editNewModel(this.className);
        }
        // TODO: IMPORTANT
        //FormView.setup(this.parentView.getSubview(this.fieldName));
        Intent i = new Intent(this, FormView.class);
        this.startActivity(i);
    }

    /** Handle loading feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case DataLoader.DATA_NOK:
        case DataLoader.DATACOUNT_NOK:
            // Close the loading dialog if present
            this.hideLoadingDialog();
            if (msg.what == DataLoader.DATACOUNT_NOK) {
                this.callCountId = 0;
            } else {
                this.callDataId = 0;
            }
            // Show error popup
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
        case DataLoader.VIEWS_OK:
            this.callDataId = 0;
            ModelView view = (ModelView)((Object[])msg.obj)[1];
            this.view = view;
            this.loadDataAndMeta();
            break;
        case DataLoader.DATACOUNT_OK:
            this.callCountId = 0;
            Object[] ret = (Object[]) msg.obj;
            int count = (Integer) ret[1];
            this.totalDataCount = count;
            if (this.data == null) {
                // Wait for data callback
            } else {
                // Load data
                this.loadData();
            }
            break;
        case DataLoader.RELFIELDS_OK:
            this.callDataId = 0;
            ret = (Object[]) msg.obj;
            this.relFields = (List<RelField>) ret[1];
            if (this.totalDataCount == -1) {
                // Wait for data count callback
            } else {
                // Load data
                this.loadData();
            }
            break;
        case DataLoader.DATA_OK:
            this.callDataId = 0;
            ret = (Object[]) msg.obj;
            List<Model> data = (List<Model>) ret[1];
            this.data = data;
            this.hideLoadingDialog();
            this.updateList();
            break;
        case TrytonCall.NOT_LOGGED:
            // TODO: this is brutal
            // Logout
            Start.logout(this);
            break;
        }
        return true;
    }

}