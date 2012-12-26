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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MenuItem;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import org.achartengine.GraphicalView;

import org.tryton.client.data.DataLoader;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.RelField;
import org.tryton.client.tools.AlertBuilder;
import org.tryton.client.tools.GraphViewFactory;
import org.tryton.client.tools.TrytonCall;

public class GraphView extends Activity
    implements Handler.Callback, DialogInterface.OnCancelListener {

    /** Use a static initializer to pass data to the activity on start.
     * Set the viewtype that hold the form view. The data to view is
     * read from current Session. If null a new Model will be created. */
    public static void setup(ModelView view) {
        viewInitializer = view;
        classNameInitializer = null;
        viewIdInitializer = 0;
    }
    /** Setup to load a particular view. If viewId is 0, the default one will
     * be used. */
    public static void setup(int viewId, String className) {
        viewIdInitializer = viewId;
        classNameInitializer = className;
        viewInitializer = null;
    }

    private static ModelView viewInitializer;
    private static int viewIdInitializer;
    private static String classNameInitializer;

    private int callCountId; // Id for parallel count call
    private int callDataId; // Id for the other call chain
    private ModelView view;
    private int viewId;
    private String className;
    private int totalDataCount = -1;
    private List<RelField> relFields;
    private List<Model> data;
    private boolean refreshing;

    private FrameLayout graphLayout;
    private ProgressDialog loadingDialog;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean loadData = false;
        boolean loadView = false;
        AlertBuilder.updateRelogHandler(new Handler(this), this);
        // Init data
        if (state != null) {
            this.view = (ModelView) state.getSerializable("view");
            this.viewId = state.getInt("viewId");
            this.className = state.getString("className");
            this.refreshing = state.getBoolean("refreshing");
            if (this.callCountId != 0) {
                DataLoader.update(this.callCountId, new Handler(this));
                this.showLoadingDialog();
            }
            if (this.callDataId != 0) {
                DataLoader.update(this.callDataId, new Handler(this));
                this.showLoadingDialog();
            }
            this.totalDataCount = state.getInt("totalDataCount");
            if (state.containsKey("data_count")) {
                int count = state.getInt("data_count");
                this.data = new ArrayList<Model>();
                for (int i = 0; i < count; i++) {
                    this.data.add((Model)state.getSerializable("data_" + i));
                }
            }
            if (state.containsKey("rel_count")) {
                int count = state.getInt("rel_count");
                this.relFields = new ArrayList<RelField>();
                for (int i = 0; i < count; i++) {
                    this.relFields.add((RelField)state.getSerializable("rel_" + i));
                }
            }
        } else {
            this.view = viewInitializer;
            viewInitializer = null;
            this.viewId = viewIdInitializer;
            viewIdInitializer = 0;
            this.className = classNameInitializer;
            classNameInitializer = null;
            if (this.view != null) {
                this.className = this.view.getModelName();
            }
            // This is the first call, need to update data for new fields
            loadView = (this.view == null);
            loadData = true;

        }
        this.setContentView(R.layout.graph);
        this.graphLayout = (FrameLayout) this.findViewById(R.id.graph_layout);
        if (!loadView && !loadData) {
            this.initView();
        } else {
            if (loadView) {
                this.loadViewAndData();
            } else {
                this.loadDataAndMeta(this.refreshing);
            }
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("view", this.view);
        outState.putInt("viewId", this.viewId);
        outState.putString("className", this.className);
        outState.putInt("totalDataCount", this.totalDataCount);
        outState.putInt("callCountId", this.callCountId);
        outState.putInt("callDataId", this.callDataId);
        outState.putBoolean("refreshing", this.refreshing);
        if (this.data != null) {
            outState.putSerializable("data_count", this.data.size());
            for (int i = 0; i < this.data.size(); i++) {
                outState.putSerializable("data_" + i, this.data.get(i));
            }
        }
        if (this.relFields != null) {
            outState.putSerializable("rel_count", this.relFields.size());
            for (int i = 0; i < this.relFields.size(); i++) {
                outState.putSerializable("rel_" + i, this.relFields.get(i));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.hideLoadingDialog();
    }

    public void showLoadingDialog() {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            this.loadingDialog.setOnCancelListener(this);
            this.loadingDialog.setMessage(this.getString(R.string.data_loading));
            this.loadingDialog.show();
        }        
    }

    public void onCancel(DialogInterface dialog) {
        DataLoader.cancel(this.callCountId);
        DataLoader.cancel(this.callDataId);
        this.callCountId = 0;
        this.callDataId = 0;
        this.refreshing = false;
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

    /** Load view and all data when done (by cascading the calls in handler) */
    private void loadViewAndData() {
        if (this.callDataId == 0) {
            this.showLoadingDialog();
            this.callDataId = DataLoader.loadView(this, this.className,
                                                  this.viewId, "graph",
                                                  new Handler(this), false);
        }
    }
    
    /** Load data count and rel fields, required for data.
     * Requires that views are loaded. */
    private void loadDataAndMeta(boolean refresh) {
        if (this.callCountId == 0) {
            this.showLoadingDialog();
            this.callCountId = DataLoader.loadDataCount(this, this.className,
                                                        new Handler(this),
                                                        refresh);
        }
        if (this.callDataId == 0) {
            this.showLoadingDialog();
            this.callDataId = DataLoader.loadRelFields(this, this.className,
                                                       new Handler(this),
                                                       refresh);
        }
    }

    /** Load data. Requires that views and meta are loaded. */
    private void loadData(boolean refresh) {
        if (this.callDataId != 0) {
            // A call is already pending, wait for its result
            return;
        }
        this.showLoadingDialog();
        this.callDataId = DataLoader.loadData(this, this.className, 0,
                                              this.totalDataCount,
                                              this.totalDataCount,
                                              this.relFields, this.view,
                                              new Handler(this),
                                              refresh);
    }

    private void initView() {
        GraphicalView graph = GraphViewFactory.getGraphView(this, this.view,
                                                            this.data);
        this.graphLayout.addView(graph);
    }

    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case DataLoader.VIEWS_OK:
            // Close the loading dialog if present
            this.hideLoadingDialog();
            this.callDataId = 0;
            @SuppressWarnings("unchecked")
            Object[] ret = (Object[]) msg.obj;
            this.view = (ModelView) ret[1];
            this.loadDataAndMeta(this.refreshing);
            break;
        case DataLoader.DATACOUNT_OK:
            this.callCountId = 0;
            ret = (Object[]) msg.obj;
            int count = (Integer) ret[1];
            this.totalDataCount = count;
            if (this.relFields == null) {
                // Wait for relfields callback
            } else {
                // Load data
                this.loadData(this.refreshing);
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
                this.loadData(this.refreshing);
            }
            break;
        case DataLoader.DATA_OK:
            this.callDataId = 0;
            this.refreshing = false;
            ret = (Object[]) msg.obj;
            List<Model> data = (List<Model>) ret[1];
            this.data = data;
            this.initView();
            this.hideLoadingDialog();
            break;
        case DataLoader.VIEWS_NOK:
        case DataLoader.DATA_NOK:
        case DataLoader.DATACOUNT_NOK:
            // Close the loading dialog if present
            this.hideLoadingDialog();
            this.refreshing = false;
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
        case TrytonCall.NOT_LOGGED:
            this.callDataId = 0;
            this.callCountId = 0;
            // Ask for relog
            this.hideLoadingDialog();
            AlertBuilder.showRelog(this, new Handler(this));
            break;
        case AlertBuilder.RELOG_CANCEL:
            this.finish();
            break;
        case AlertBuilder.RELOG_OK:
            this.loadViewAndData();
            break;
        }
        return true;
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_LOGOUT_ID = 0;
    private static final int MENU_REFRESH_ID = 1;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add logout entry
        MenuItem logout = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 100,
                                   this.getString(R.string.general_logout));
        logout.setIcon(R.drawable.tryton_log_out);
        // Set refresh
        MenuItem refresh = menu.add(android.view.Menu.NONE, MENU_REFRESH_ID, 30,
                                 this.getString(R.string.general_reload));
        refresh.setIcon(R.drawable.tryton_refresh);
        return true;
    }

    /** Called on menu selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_LOGOUT_ID:
            Start.logout(this);
            break;
        case MENU_REFRESH_ID:
            this.refreshing = true;
            this.loadDataAndMeta(this.refreshing);
            break;
        }
        return true;
    }

}
