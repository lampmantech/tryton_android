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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.data.DataCache;
import org.tryton.client.data.ViewCache;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.models.RelField;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;
import org.tryton.client.views.TreeFullAdapter;
import org.tryton.client.views.TreeSummaryAdapter;
import org.tryton.client.views.TreeSummaryItem;

public class TreeView extends Activity
    implements Handler.Callback, ListView.OnItemClickListener,
               ExpandableListView.OnChildClickListener {

    /** Use a static initializer to pass data to the activity on start.
        Set the menu that triggers the view to load the views. */
    public static void setup(MenuEntry origin) {
        entryInitializer = origin;
    }
    private static MenuEntry entryInitializer;

    private static final int MODE_SUMMARY = 1;
    private static final int MODE_EXTENDED = 2;

    private static final int PAGING_SUMMARY = 40;
    private static final int PAGING_EXTENDED = 10;
    
    private MenuEntry origin;
    private ModelViewTypes viewTypes;
    private int totalDataCount = -1;
    private int dataOffset;
    private List<RelField> relFields;
    private List<Model> data;
    private int mode;

    private TextView pagination;
    private ImageButton nextPage, previousPage;
    private ProgressDialog loadingDialog;
    private ListView tree;
    private ExpandableListView sumtree;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            this.origin = (MenuEntry) state.getSerializable("origin");
            this.viewTypes = (ModelViewTypes) state.getSerializable("viewTypes");
            this.totalDataCount = state.getInt("totalDataCount");
            if (state.containsKey("data_count")) {
                int count = state.getInt("data_count");
                this.data = new ArrayList<Model>();
                for (int i = 0; i < count; i++) {
                    this.data.add((Model)state.getSerializable("data_" + i));
                }
            }
            this.mode = state.getInt("mode");
        } else if (entryInitializer != null) {
            this.origin = entryInitializer;
            entryInitializer = null;
            this.mode = MODE_SUMMARY;
        }
        // Init view
        this.setContentView(R.layout.tree);
        this.tree = (ListView) this.findViewById(R.id.tree_list);
        this.tree.setOnItemClickListener(this);
        this.sumtree = (ExpandableListView) this.findViewById(R.id.tree_sum_list);
        this.sumtree.setOnChildClickListener(this);
        this.pagination = (TextView) this.findViewById(R.id.tree_pagination);
        this.nextPage = (ImageButton) this.findViewById(R.id.tree_next_btn);
        this.previousPage = (ImageButton) this.findViewById(R.id.tree_prev_btn);
        // Load data if there isn't anyone or setup the list
        if (this.data == null && this.viewTypes == null) {
            this.loadViewsAndData();
        } else if (this.data != null) {
            this.updateList();
        } else {
            this.loadData();
        }
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("origin", this.origin);
        outState.putSerializable("viewTypes", this.viewTypes);
        outState.putInt("totalDataCount", this.totalDataCount);
        if (this.data != null) {
            outState.putSerializable("data_count", this.data.size());
            for (int i = 0; i < this.data.size(); i++) {
                outState.putSerializable("data_" + i, this.data.get(i));
            }
        }
        outState.putInt("mode", this.mode);
    }

    private void updateList() {
        // Update paging display
        String format = this.getString(R.string.tree_pagination);
        this.pagination.setText(String.format(format,
                                              this.dataOffset + 1,
                                              this.dataOffset + this.data.size(),
                                              this.totalDataCount));
        if (this.dataOffset == 0) {
            this.previousPage.setEnabled(false);
        } else {
            this.previousPage.setEnabled(true);
        }
        if (this.dataOffset + this.data.size() < this.totalDataCount) {
            this.nextPage.setEnabled(true);
        } else {
            this.nextPage.setEnabled(false);
        }
        // Update data
        switch (this.mode) {
        case MODE_EXTENDED:
            TreeFullAdapter adapt = new TreeFullAdapter(this.viewTypes.getView("tree"),
                                                        this.data);
            this.tree.setAdapter(adapt);
            this.sumtree.setVisibility(View.GONE);
            this.tree.setVisibility(View.VISIBLE);
            break;
        case MODE_SUMMARY:
            TreeSummaryAdapter sumadapt = new TreeSummaryAdapter(this.viewTypes.getView("tree"), this.data);
            this.sumtree.setAdapter(sumadapt);
            this.sumtree.setVisibility(View.VISIBLE);
            this.tree.setVisibility(View.GONE);
            break;
        }
    }

    public void prevPage(View button) {
        switch (this.mode) {
        case MODE_EXTENDED:
            this.dataOffset -= PAGING_EXTENDED;
            break;
        case MODE_SUMMARY:
            this.dataOffset -= PAGING_SUMMARY;
        }
        if (this.dataOffset < 0) {
            this.dataOffset = 0;
        }
        this.loadData();
    }

    public void nextPage(View button) {
        int maxOffset = this.totalDataCount;
        switch (this.mode) {
        case MODE_EXTENDED:
            this.dataOffset += PAGING_EXTENDED;
            maxOffset -= PAGING_EXTENDED;
            break;
        case MODE_SUMMARY:
            this.dataOffset += PAGING_SUMMARY;
            maxOffset -= PAGING_SUMMARY;
        }
        this.dataOffset = Math.min(this.dataOffset, maxOffset);
        this.loadData();
    }

    public void onItemClick(AdapterView<?> adapt, View v,
                            int position, long id) {
        Model clickedData = this.data.get(position);
        FormView.setup(this.viewTypes);
        Session.current.editModel(clickedData);
        Intent i = new Intent(this, FormView.class);
        this.startActivity(i);
    }
    public boolean onChildClick(ExpandableListView parent, View v, int groupPos,
                                int childPos, long id) {
        Model clickedData = this.data.get(groupPos);
        FormView.setup(this.viewTypes);
        Session.current.editModel(clickedData);
        Intent i = new Intent(this, FormView.class);
        this.startActivity(i);
        return true;
    }

    private static final int LOADING_VIEWS = 0;
    private static final int LOADING_DATA = 1;
    public void showLoadingDialog(int type) {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            String message;
            switch (type) {
            case LOADING_VIEWS:
                message = this.getString(R.string.view_loading);
                break;
            default:
                message = this.getString(R.string.data_loading);
                break;
            }
            this.loadingDialog.setMessage(message);
            this.loadingDialog.show();
        }        
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
            // Check if views are available from cache
            try {
                ModelViewTypes views = ViewCache.load(this.origin, this);
                if (views != null) {
                    this.viewTypes = views;
                    this.loadDataAndMeta();
                }
            } catch (IOException e) {
                if (!(e instanceof FileNotFoundException)) {
                    // Ignore no cache exception   
                    Log.i("Tryton",
                          "Unable to load view cache for " + this.origin, e);
                }
            }
            if (this.viewTypes == null) {
                this.showLoadingDialog(LOADING_VIEWS);
                Session s = Session.current;
                TrytonCall.getViews(s.userId, s.cookie, s.prefs, this.origin,
                                    new Handler(this));
            }
    }

    /** Load data count and rel fields, required for data.
     * Requires that views are loaded. */
    private void loadDataAndMeta() {
        String model = this.viewTypes.getModelName();
        Session s = Session.current;
        DataCache db = new DataCache(this);
        // Get total from cache if present, otherwise from server
        if (this.totalDataCount == -1) {
            this.totalDataCount = db.getDataCount(model);
            if (this.totalDataCount == -1) {
                TrytonCall.getDataCount(s.userId, s.cookie, s.prefs, model,
                                        new Handler(this));
            }
        }
        // Get field definition
        if (this.relFields == null) {
            this.relFields = db.getRelFields(model);
            if (this.relFields == null) {
                // We don't have them. Query them, it will load data on handler
                // callback.
                this.showLoadingDialog(LOADING_DATA);
                TrytonCall.getRelFields(s.userId, s.cookie, s.prefs, model,
                                        new Handler(this));
            } else {
                this.loadData();
            }
        } else {
            this.loadData();
        }
    }

    /** Load data. Requires that views and meta are loaded. */
    private void loadData() {
        String model = this.viewTypes.getModelName();
        Session s = Session.current;
        DataCache db = new DataCache(this);
        // Get data from cache if present, otherwise from server
        int fieldsCount = this.viewTypes.getView("tree").getStructure().size();
        int count = 10;
        switch (this.mode) {
        case MODE_EXTENDED:
            count = PAGING_EXTENDED;
            break;
        case MODE_SUMMARY:
            count = PAGING_SUMMARY;
            break;
        }
        // Force mode to extended view with summary entry count if
        // there is not enough fields to display
        if (fieldsCount < TreeSummaryItem.FIELDS_COUNT) {
            count = PAGING_SUMMARY;
            this.mode = MODE_EXTENDED;
        }
        List<Model> cacheData = db.getData(model, this.dataOffset, count);
        if (cacheData.size() == Math.min(this.totalDataCount - this.dataOffset,
                                         count)) {
            // Data is full, use it
            this.data = cacheData;
            this.updateList();
        } else {
            // Data is incomplete, or even empty, reload from server
            this.showLoadingDialog(LOADING_DATA);
            TrytonCall.getData(s.userId, s.cookie, s.prefs, model,
                               this.dataOffset, count, this.relFields,
                               new Handler(this));
        }
    }

    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Close the loading dialog if present
        this.hideLoadingDialog();
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_VIEWS_OK:
            // Save view and load data
            ModelViewTypes viewTypes = (ModelViewTypes) msg.obj;
            try {
                ViewCache.save(this.origin, viewTypes, this);
            } catch (IOException e) {
                Log.w("Tryton",
                      "Unable to cache view data for " + this.origin, e);
            }
            this.viewTypes = viewTypes;
            this.loadDataAndMeta();
            break;
        case TrytonCall.CALL_VIEWS_NOK:
        case TrytonCall.CALL_DATA_NOK:
        case TrytonCall.CALL_DATACOUNT_NOK:
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            ((Exception)msg.obj).printStackTrace();
            break;
        case TrytonCall.CALL_DATACOUNT_OK:
            int count = (Integer) msg.obj;
            DataCache db = new DataCache(this);
            db.setDataCount(this.viewTypes.getModelName(), count);
            this.totalDataCount = count;
            if (this.data == null) {
                // Wait for data callback
            } else {
                this.updateList();
            }
            break;
        case TrytonCall.CALL_RELFIELDS_OK:
            @SuppressWarnings("unchecked")
            List<RelField> rel = (List<RelField>)msg.obj;
            db = new DataCache(this);
            db.storeRelFields(this.viewTypes.getModelName(), rel);
            this.relFields = rel;
            this.loadData();
            break;
        case TrytonCall.CALL_DATA_OK:
            List<Model> data = (List) msg.obj;
            db = new DataCache(this);
            db.storeData(this.viewTypes.getModelName(), data);
            this.data = data;
            if (this.totalDataCount == -1) {
                // Wait for data count callback
            } else {
                this.updateList();
            }
            break;
        case TrytonCall.NOT_LOGGED:
            // TODO: this is brutal
            // Logout
            Start.logout(this);
            break;
        }
        return true;
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_LOGOUT_ID = 0;
    private static final int MENU_NEW_ID = 1;
    private static final int MENU_GRAPH_ID = 2;
    private static final int MENU_MODE_ID = 3;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add logout entry
        MenuItem logout = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 100,
                                   this.getString(R.string.general_logout));
        logout.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        // Set form entry (new data)
        MenuItem add = menu.add(android.view.Menu.NONE, MENU_NEW_ID, 1,
                                this.getString(R.string.general_new_record));
        add.setIcon(android.R.drawable.ic_menu_add);
        // Set view mode switch
        MenuItem mode = menu.add(android.view.Menu.NONE, MENU_MODE_ID, 10,
                                 this.getString(R.string.tree_switch_mode_summary));
        mode.setIcon(android.R.drawable.ic_menu_crop);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // Add graph entry if there is a graph view
        if (this.viewTypes.getView("graph") != null
            && menu.findItem(MENU_GRAPH_ID) == null) {
            // Set graph entry
            MenuItem graph = menu.add(android.view.Menu.NONE, MENU_GRAPH_ID, 2,
                                      this.getString(R.string.general_graph));
            graph.setIcon(android.R.drawable.ic_menu_gallery);
        }
        // Set mode label
        MenuItem mode = menu.findItem(MENU_MODE_ID);
        if (mode != null) {
            switch (this.mode) {
            case MODE_SUMMARY:
                mode.setTitle(R.string.tree_switch_mode_extended);
                break;
            case MODE_EXTENDED:
                mode.setTitle(R.string.tree_switch_mode_summary);
            }
        }
        // Remove mode switch if the view has less than 3 fields
        int fieldsCount = this.viewTypes.getView("tree").getStructure().size();
        if (fieldsCount <= TreeSummaryItem.FIELDS_COUNT) {
            menu.removeItem(MENU_MODE_ID);
        }
        return true;
    }

    /** Called on menu selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_LOGOUT_ID:
            Start.logout(this);
            break;
        case MENU_MODE_ID:
            if (this.mode == MODE_SUMMARY) {
                this.mode = MODE_EXTENDED;
            } else {
                this.mode = MODE_SUMMARY;
            }
            this.loadData();
            break;
        case MENU_NEW_ID:
            Session.current.editNewModel(this.viewTypes.getModelName());
            FormView.setup(this.viewTypes);
            Intent i = new Intent(this, FormView.class);
            this.startActivity(i);
            break;
        }
        return true;
    }

}
