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
import android.widget.ExpandableListView;
import android.widget.ListView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;
import org.tryton.client.views.TreeFullAdapter;
import org.tryton.client.views.TreeSummaryAdapter;
import org.tryton.client.views.TreeSummaryItem;

public class TreeView extends Activity implements Handler.Callback {

    /** Use a static initializer to pass data to the activity on start.
        Set the menu that triggers the view to load the views. */
    public static void setup(MenuEntry origin) {
        entryInitializer = origin;
        viewTypesInitializer = null;
    }
    /** Use a static initializer to pass data to the activity on start.
        Set the views available. */
    public static void setup(ModelViewTypes viewTypes) {
        entryInitializer = null;
        viewTypesInitializer = viewTypes;
    }
    private static MenuEntry entryInitializer;
    private static ModelViewTypes viewTypesInitializer;

    private static final int MODE_SUMMARY = 1;
    private static final int MODE_EXTENDED = 2;

    private static final int PAGING_SUMMARY = 40;
    private static final int PAGING_EXTENDED = 10;
    
    private ModelViewTypes viewTypes;
    private List<Model> data;
    private int mode;
    private ProgressDialog loadingDialog;
    private ListView tree;
    private ExpandableListView sumtree;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            this.viewTypes = (ModelViewTypes) state.getSerializable("viewTypes");
            if (state.containsKey("data_count")) {
                int count = state.getInt("data_count");
                this.data = new ArrayList<Model>();
                for (int i = 0; i < count; i++) {
                    this.data.add((Model)state.getSerializable("data_" + i));
                }
            }
            this.mode = state.getInt("mode");
        } else if (entryInitializer != null) {
            this.showLoadingDialog(LOADING_VIEWS);
            Session s = Session.current;
            TrytonCall.getViews(s.userId, s.cookie, s.prefs, entryInitializer,
                                new Handler(this));
            entryInitializer = null; // Reset (consume setup)
            this.mode = MODE_EXTENDED;
        } else if (viewTypesInitializer != null) {
            this.viewTypes = viewTypesInitializer;
            viewTypesInitializer = null; // Reset (consume setup)
            this.mode = MODE_EXTENDED;
        }
        // Init view
        this.setContentView(R.layout.tree);
        this.tree = (ListView) this.findViewById(R.id.tree_list);
        this.sumtree = (ExpandableListView) this.findViewById(R.id.tree_sum_list);
        // Load data if there isn't anyone or setup the list
        if (this.data == null && this.viewTypes != null) {
            this.showLoadingDialog(LOADING_DATA);
            Session s = Session.current;
            String model = this.viewTypes.getModelName();
            TrytonCall.getData(s.userId, s.cookie, s.prefs, model, 0, 10,
                                new Handler(this));
        } else if (this.data != null) {
            this.updateList();
        }
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("viewTypes", this.viewTypes);
        if (this.data != null) {
            outState.putSerializable("data_count", this.data.size());
            for (int i = 0; i < this.data.size(); i++) {
                outState.putSerializable("data_" + i, this.data.get(i));
            }
        }
        outState.putInt("mode", this.mode);
    }

    private void updateList() {
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

    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Close the loading dialog if present
        this.hideLoadingDialog();
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_VIEWS_OK:
            ModelViewTypes viewTypes = (ModelViewTypes) msg.obj;
            this.viewTypes = viewTypes;
            // Load data
            String model = viewTypes.getModelName();
            this.showLoadingDialog(LOADING_DATA);
            Session s = Session.current;
            TrytonCall.getData(s.userId, s.cookie, s.prefs, model, 0, 10,
                                new Handler(this));
            break;
        case TrytonCall.CALL_VIEWS_NOK:
        case TrytonCall.CALL_DATA_NOK:
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            ((Exception)msg.obj).printStackTrace();
            break;
        case TrytonCall.CALL_DATA_OK:
            List<Model> data = (List) msg.obj;
            this.data = data;
            this.updateList();
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
            this.updateList();
        }
        return true;
    }

}
