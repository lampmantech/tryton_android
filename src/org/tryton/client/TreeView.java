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
import java.io.IOException;

import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;

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
    private ProgressDialog loadingDialog;    

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

    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("viewTypes", this.viewTypes);
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
            break;
        case TrytonCall.CALL_VIEWS_NOK:
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            ((Exception)msg.obj).printStackTrace();
            break;
        }
        return true;
    }


}
