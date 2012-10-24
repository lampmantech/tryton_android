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
import android.widget.ListView;
import java.io.IOException;
import java.util.List;

import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;
import org.tryton.client.views.TreeFullAdapter;

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
    
    private ModelViewTypes viewTypes;
    private List<Model> data;
    private ProgressDialog loadingDialog;
    private ListView tree;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            this.viewTypes = (ModelViewTypes) state.getSerializable("viewTypes");
        } else if (entryInitializer != null) {
            this.showLoadingDialog();
            Session s = Session.current;
            TrytonCall.getViews(s.userId, s.cookie, s.prefs, entryInitializer,
                                new Handler(this));
            entryInitializer = null; // Reset (consume setup)
        } else if (viewTypesInitializer != null) {
            this.viewTypes = viewTypesInitializer;
            viewTypesInitializer = null; // Reset (consume setup)
        }
        // Init view
        this.setContentView(R.layout.tree);
        this.tree = (ListView) this.findViewById(R.id.tree_list);
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("viewTypes", this.viewTypes);
    }

    private void updateList() {
        TreeFullAdapter adapt = new TreeFullAdapter(this.viewTypes.getView("tree"),
                                                    this.data);
        this.tree.setAdapter(adapt);
    }

    public void showLoadingDialog() {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            this.loadingDialog.setMessage(this.getString(R.string.view_loading));
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
            this.showLoadingDialog();
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
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add configuration entry
        MenuItem config = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 0,
                                   this.getString(R.string.general_logout));
        config.setIcon(android.R.drawable.ic_menu_preferences);
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
