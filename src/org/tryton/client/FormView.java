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
import android.widget.TableLayout;
import android.widget.TableRow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tryton.client.data.DataCache;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.tools.FormViewFactory;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;

public class FormView extends Activity implements Handler.Callback {

    /** Use a static initializer to pass data to the activity on start.
     * Set the viewtype that hold the form view. The data to edit is
     * read from current Session. If null a new Model will be created. */
    public static void setup(ModelViewTypes view) {
        viewInitializer = view;
    }
    private static ModelViewTypes viewInitializer;

    private ModelViewTypes viewTypes;
    private Set<String> relModelsToLoad;
    private Set<String> modelsToLoad;
    private ProgressDialog loadingDialog;

    private TableLayout table;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            this.viewTypes = (ModelViewTypes) state.getSerializable("viewTypes");
        } else {
            this.viewTypes = viewInitializer;
            viewInitializer = null;
            // Set a new model to edit in session if required
            if (Session.current.editedModel == null) {
                Model newModel = new Model(this.viewTypes.getModelName());
                newModel.set("id", -1);
                Session.current.editedModel = newModel;
            }
        }
        // Init view
        this.setContentView(R.layout.form);
        this.table = (TableLayout) this.findViewById(R.id.form_table);
        ModelView modelView = this.viewTypes.getView("form");
        int x = 0; // X position of the widget
        TableRow row = null;
        for (Model view : modelView.getStructure()) {
            Session s = Session.current;
            View v = FormViewFactory.getView(view, modelView,
                                             s.editedModel,
                                             s.prefs,
                                             this);
            if (x == 0) {
                if (row != null) {
                    this.table.addView(row);
                }
                row = new TableRow(this);
                row.addView(v);
            } else {
                row.addView(v);
            }
            x++;
            x %= 2;
        }
        this.table.addView(row);
        // Check if we have all the data required for relationnal fields
        this.relModelsToLoad = new HashSet<String>();
        this.modelsToLoad = new HashSet<String>();
        for (Model view : modelView.getStructure()) {
            if (!(view.getClassName().equals("label"))) {
                String type = view.getString("type");
                DataCache db = new DataCache(this);
                String relModel = view.getString("relation");
                if ((type.equals("one2one") || type.equals("many2one"))
                    && !db.isFullyLoaded(relModel, false)) {
                    this.relModelsToLoad.add(relModel);
                } else if ((type.equals("one2many")
                            || type.equals("many2many"))
                           && !db.isFullyLoaded(relModel, true)) {
                    this.modelsToLoad.add(relModel);
                }
            }
        }
        if (!this.relModelsToLoad.isEmpty() || !this.modelsToLoad.isEmpty()) {
            // Start loading
            this.showLoadingDialog();
            this.loadRel();
        }
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("viewTypes", this.viewTypes);
    }

    @Override
    public void onDestroy() {
        // Check if dirty to request save
        // Reset session
        Session.current.editModel(null);
    }

    public void showLoadingDialog() {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            this.loadingDialog.setMessage(this.getString(R.string.data_loading));
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

    /** Load a pending model and remove it from queue.
     * Return true if a load is launched, false if
     * there is nothing to load. */
    private boolean loadRel() {
        if (!this.relModelsToLoad.isEmpty() || !this.modelsToLoad.isEmpty()) {
            Session s = Session.current;
            String modelLoaded = null;
            // Run the first model and remove it. LoadRel will be recalled
            // on handler callback.
            for (String model : this.relModelsToLoad) {
                // This is the easyest way to get an Iterator on the set
                // and get the first entry.
                TrytonCall.getRelData(s.userId, s.cookie, s.prefs, model,
                                      false, new Handler(this));
                modelLoaded = model;
                break;
            }
            if (modelLoaded == null) {
                // relModelToLoad all loaded
                for (String model : this.modelsToLoad) {
                    TrytonCall.getRelData(s.userId, s.cookie, s.prefs, model,
                                          true, new Handler(this));
                    modelLoaded = model;
                    break;
                }
                this.modelsToLoad.remove(modelLoaded);
            } else {
                // Remove from pending models
                this.relModelsToLoad.remove(modelLoaded);
            }
            return true;
        } else {
            return false;
        }
    }

    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_RELDATA_OK:
            String className = (String) ((Object[])msg.obj)[0];
            List<Model> data = (List) ((Object[])msg.obj)[1];
            DataCache db = new DataCache(this);
            if (msg.arg1 == 0) {
                db.storeRelData(className, data);
            } else {
                db.storeClassData(className, data);
            }
            db.setDataCount(className, data.size());
            // Run next loading
            if (!this.loadRel()) {
                // This is the end
                this.hideLoadingDialog();
            }
            break;
        case TrytonCall.CALL_RELDATA_NOK:
            this.hideLoadingDialog();
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            ((Exception)msg.obj).printStackTrace();
            break;
        case TrytonCall.NOT_LOGGED:
            // TODO: this is brutal
            // Logout
            this.hideLoadingDialog();
            Start.logout(this);
            break;
        }
        return true;
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_LOGOUT_ID = 0;
    private static final int MENU_TREE_ID = 1;
    private static final int MENU_GRAPH_ID = 2;
    private static final int MENU_SAVE_ID = 3;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add logout entry
        MenuItem logout = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 100,
                                   this.getString(R.string.general_logout));
        logout.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        // Set save
        MenuItem save = menu.add(android.view.Menu.NONE, MENU_SAVE_ID, 1,
                                 this.getString(R.string.form_save));
        save.setIcon(android.R.drawable.ic_menu_crop);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // Enable/disable save
        MenuItem save = menu.findItem(MENU_SAVE_ID);
        save.setEnabled(Session.current.dirtyModel);
        return true;
    }

    /** Called on menu selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_LOGOUT_ID:
            Start.logout(this);
            break;
        }
        return true;
    }

}
