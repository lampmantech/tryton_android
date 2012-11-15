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
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tryton.client.data.DataCache;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.tools.AlertBuilder;
import org.tryton.client.tools.FormViewFactory;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;

public class FormView extends Activity
    implements Handler.Callback, DialogInterface.OnClickListener,
               DialogInterface.OnCancelListener {

    private static final int LOADING_DATA = 0;
    private static final int LOADING_SEND = 1;

    private static final int DIALOG_DIRTY = 0;
    private static final int DIALOG_DELETE = 1;

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
    private int currentDialog;
    private int callId;
    private int currentLoadingMsg;
    private boolean kill; // Check if edit is finished when destroying activity

    private TableLayout table;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        if (state != null) {
            this.viewTypes = (ModelViewTypes) state.getSerializable("viewTypes");
            this.callId = state.getInt("callId");
            this.currentLoadingMsg = state.getInt("currentLoadingMsg");
            if (this.callId != 0) {
                TrytonCall.update(this.callId, new Handler(this));
                this.showLoadingDialog(this.currentLoadingMsg);
            }
        } else {
            this.viewTypes = viewInitializer;
            viewInitializer = null;
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
            if (view.hasAttribute("type")
                && (view.getString("type").equals("many2many")
                    || view.getString("type").equals("one2many"))) {
                // Special case of x2many: they embed their own title
                // Thus occupies a full line
                TextView label = new TextView(this);
                if (view.hasAttribute("string")) {
                    label.setText(view.getString("string"));
                } else {
                    label.setText(view.getString("name"));
                }
                if (row != null) {
                    this.table.addView(row);
                }
                row = new TableRow(this);
                row.addView(label);
                row.addView(v);
                x = 0;
            } else {
                if (x == 0) {
                    if (row != null) {
                        this.table.addView(row);
                    }
                    row = new TableRow(this);
                    row.addView(v);
                    // Trick, make the first columnt 1/3 of table width
                    if (v instanceof TextView) {
                        TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 0.3f);
                        v.setLayoutParams(lp);
                        ((TextView)v).setWidth(100);
                    }
                } else {
                    row.addView(v);
                    TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 0.7f);
                    v.setLayoutParams(lp);

                }
                x++;
                x %= 2;
            }
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
        if ((!this.relModelsToLoad.isEmpty() || !this.modelsToLoad.isEmpty())
            && this.callId == 0) {
            // Start loading
            this.showLoadingDialog(LOADING_DATA);
            this.loadRel();
        }
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("viewTypes", this.viewTypes);
        outState.putInt("callId", this.callId);
        outState.putInt("currentLoadingMsg", this.currentLoadingMsg);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.refreshDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.kill) {
            Session.current.finishEditing();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.hideLoadingDialog();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // Check if dirty and alert
            this.updateTempModel();
            if (Session.current.editedIsDirty()) {
                // Ask for save
                this.currentDialog = DIALOG_DIRTY;
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.form_dirty_title);
                b.setMessage(R.string.form_dirty_message);
                b.setPositiveButton(R.string.general_yes, this);
                b.setNegativeButton(R.string.general_no, this);
                b.setNeutralButton(R.string.general_cancel, this);
                b.show();
                // Skip standard behaviour
                return true;
            } else {
                if (this.loadingDialog == null) {
                    this.kill = true;
                }
            }
        }
        // Use default behaviour
        return super.onKeyDown(keyCode, event);
    }

    /** Handle buttons click on dirty or delete confirmation popup. */
    public void onClick(DialogInterface dialog, int which) {
        switch (currentDialog) {
        case DIALOG_DIRTY:
            switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                dialog.dismiss();
                this.sendSave();
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                dialog.dismiss();
                this.finish();
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                dialog.dismiss();
                break;
            }
            break;
        case DIALOG_DELETE:
            switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                dialog.dismiss();
                // Show loading dialog and send delete call
                this.showLoadingDialog(LOADING_SEND);
                Session s = Session.current;
                this.callId = TrytonCall.deleteData(s.userId, s.cookie, s.prefs,
                                                    (Integer) s.editedModel.get("id"),
                                                    s.editedModel.getClassName(),
                                                    new Handler(this));
                break;
                // There is no listener bound to negative: default is dismiss.
            }
            break;
        }
    }

    /** Refresh fields with values from tempModel or editedModel. */
    private void refreshDisplay() {
        Session s = Session.current;
        Model tmp = s.tempModel;
        ModelView modelView = this.viewTypes.getView("form");
        int structIndex = -1;
        for (int i = 0; i < this.table.getChildCount(); i++) {
            ViewGroup child = (ViewGroup) this.table.getChildAt(i);
            for (int j = 0; j < child.getChildCount(); j++) {
                structIndex++;
                View v = child.getChildAt(j);
                if (!FormViewFactory.isFieldView(v)) {
                    // Check if it is a x2many label
                    Model field = modelView.getStructure().get(structIndex);
                    if (field.hasAttribute("type")) {
                        String type = field.getString("type");
                        if (type.equals("many2many")
                            || type.equals("one2many")) {
                            // This is the label of a x2many field
                            // It is not present in structure and next
                            // will be the true widget.
                            // Get back in structure for next pass
                            // to point on the x2many field (and not next one)
                            structIndex--;
                        }
                    }
                    continue;
                }
                Model field = modelView.getStructure().get(structIndex);
                FormViewFactory.setValue(v, field, modelView, tmp,
                                         s.editedModel, s.prefs, this);
            }
        }
    }

    public void showLoadingDialog(int message) {
        if (this.loadingDialog == null) {
            this.currentLoadingMsg = message;
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            String msg = "";
            switch (message) {
            case LOADING_DATA:
                msg = this.getString(R.string.data_loading);
                this.loadingDialog.setOnCancelListener(this);
                break;
            case LOADING_SEND:
                msg = this.getString(R.string.data_send);
                this.loadingDialog.setCancelable(false);
                break;
            }
            this.loadingDialog.setMessage(msg);
            this.loadingDialog.show();
        }        
    }

    public void onCancel(DialogInterface dialog) {
        TrytonCall.cancel(this.callId);
        this.callId = 0;
        this.loadingDialog = null;
        this.kill = true;
        this.finish();
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
     * there is nothing to load.
     * Warning: you must check that callId is 0 to prevent multiple loads. */
    private boolean loadRel() {
        if (!this.relModelsToLoad.isEmpty() || !this.modelsToLoad.isEmpty()) {
            Session s = Session.current;
            String modelLoaded = null;
            // Run the first model and remove it. LoadRel will be recalled
            // on handler callback.
            for (String model : this.relModelsToLoad) {
                // This is the easyest way to get an Iterator on the set
                // and get the first entry.
                this.callId = TrytonCall.getRelData(s.userId, s.cookie,
                                                    s.prefs, model,
                                                    false, new Handler(this));
                modelLoaded = model;
                break;
            }
            if (modelLoaded == null) {
                // relModelToLoad all loaded
                for (String model : this.modelsToLoad) {
                    this.callId = TrytonCall.getRelData(s.userId, s.cookie,
                                                        s.prefs, model, true,
                                                        new Handler(this));
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

    /** Update temp model to current values */
    private void updateTempModel() {
        Model tmp = Session.current.tempModel;
        ModelView modelView = this.viewTypes.getView("form");
        int structIndex = -1;
        for (int i = 0; i < this.table.getChildCount(); i++) {
            ViewGroup child = (ViewGroup) this.table.getChildAt(i);
            for (int j = 0; j < child.getChildCount(); j++) {
                structIndex++;
                View v = child.getChildAt(j);
                if (!FormViewFactory.isFieldView(v)) {
                    // Check if it is a x2many label
                    Model field = modelView.getStructure().get(structIndex);
                    if (field.hasAttribute("type")) {
                        String type = field.getString("type");
                        if (type.equals("many2many")
                            || type.equals("one2many")) {
                            // This is the label of a x2many field
                            // It is not present in structure and next
                            // will be the true widget.
                            // Get back in structure for next pass
                            // to point on the x2many field (and not next one)
                            structIndex--;
                        }
                    }
                    // Ignore
                    continue;
                }
                Model field = modelView.getStructure().get(structIndex);
                Object value = FormViewFactory.getValue(v, field,
                                                        Session.current.prefs);
                if (value != FormViewFactory.NO_VALUE) {
                    // If NO_VALUE (not null) the value is ignored
                    tmp.set(field.getString("name"), value);
                }
            }
        }
    }

    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_RELDATA_OK:
            this.callId = 0;
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
        case TrytonCall.CALL_SAVE_OK:
            this.callId = 0;
            this.hideLoadingDialog();
            Toast t = Toast.makeText(this, R.string.data_send_done,
                                     Toast.LENGTH_SHORT);
            t.show();
            // Update data with fresh one
            Model m = (Model) msg.obj;
            className = Session.current.tempModel.getClassName();
            db = new DataCache(this);
            if (m != null) {
                db.storeData(m.getClassName(), m);
            } else {
                // It is saved but not given back, use local data
                Model base = Session.current.editedModel;
                Model edit = Session.current.tempModel;
                for (String attr : edit.getAttributeNames()) {
                    base.set(attr, edit.get(attr));
                }
                db.storeData(className, base);
            }
            // Update session and local data count if required
            if (Session.current.editedModel == null) {
                // Creation: add one to data count and
                // reset session for a new record
                db.addOne(className);
                Session.current.tempModel = new Model(className);
                this.refreshDisplay();
            } else {
                // Edition: clear edition and return back to tree
                this.kill = true;
                this.finish();
            }
            break;
        case TrytonCall.CALL_DELETE_OK:
            this.callId = 0;
            this.hideLoadingDialog();
            // Update local db
            db = new DataCache(this);
            db.deleteData(Session.current.editedModel);
            this.kill = true;
            this.finish();
            break;
        case TrytonCall.CALL_RELDATA_NOK:
        case TrytonCall.CALL_SAVE_NOK:
        case TrytonCall.CALL_DELETE_NOK:
            this.callId = 0;
            this.hideLoadingDialog();
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
            // TODO: this is brutal
            // Logout
            this.hideLoadingDialog();
            Start.logout(this);
            break;
        }
        return true;
    }

    /** Show dialog and send save call to the server. Callback is in handler. */
    private void sendSave() {
        this.showLoadingDialog(LOADING_SEND);
        Session s = Session.current;
        this.callId = TrytonCall.saveData(s.userId, s.cookie, s.prefs,
                                          s.tempModel, s.editedModel, this,
                                          new Handler(this));
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_LOGOUT_ID = 0;
    private static final int MENU_TREE_ID = 1;
    private static final int MENU_GRAPH_ID = 2;
    private static final int MENU_SAVE_ID = 3;
    private static final int MENU_DEL_ID = 4;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add logout entry
        MenuItem logout = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 100,
                                   this.getString(R.string.general_logout));
        logout.setIcon(R.drawable.tryton_log_out);
        // Set save
        MenuItem save = menu.add(android.view.Menu.NONE, MENU_SAVE_ID, 1,
                                 this.getString(R.string.form_save));
        save.setIcon(R.drawable.tryton_save);
        // Set delete
        MenuItem delete = menu.add(android.view.Menu.NONE, MENU_DEL_ID, 5,
                                   this.getString(R.string.form_delete));
        delete.setIcon(R.drawable.tryton_delete);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        this.updateTempModel();
        // Enable/disable save
        MenuItem save = menu.findItem(MENU_SAVE_ID);
        save.setEnabled(Session.current.editedIsDirty());
        // Remove delete for creation
        if (Session.current.editedModel == null) {
            menu.removeItem(MENU_DEL_ID);
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
        case MENU_SAVE_ID:
            // Send save call
            this.sendSave();
            break;
        case MENU_DEL_ID:
            // Ask for confirmation
            this.currentDialog = DIALOG_DELETE;
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.form_delete_title);
            b.setMessage(R.string.form_delete_message);
            b.setPositiveButton(R.string.general_yes, this);
            b.setNegativeButton(R.string.general_no, null);
            b.show();
            break;
        }
        return true;
    }

}
