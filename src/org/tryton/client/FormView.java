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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tryton.client.data.DataCache;
import org.tryton.client.data.DataLoader;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.models.RelField;
import org.tryton.client.tools.AlertBuilder;
import org.tryton.client.tools.DelayedRequester;
import org.tryton.client.tools.FormViewFactory;
import org.tryton.client.tools.FieldsConvertion;
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
    public static void setup(ModelView view) {
        viewInitializer = view;
        viewIdInitializer = 0;
        commandInitializer = null;
    }
    /** Setup to load a particular view. If viewId is 0, the default one will
     * be used. */
    public static void setup(int viewId) {
        viewIdInitializer = viewId;
        viewInitializer = null;
        commandInitializer = null;
    }

    /** Setup to edit a delayed command. */
    public static void setup(DelayedRequester.Command cmd) {
        commandInitializer = cmd;
        viewIdInitializer = 0;
        viewInitializer = null;
    }

    private static ModelView viewInitializer;
    private static int viewIdInitializer;
    private static DelayedRequester.Command commandInitializer;

    private int viewId;
    private ModelView view;
    private List<RelField> relFields; // in case of data reload
    private DelayedRequester.Command command; // only on command edit
    private ProgressDialog loadingDialog;
    private int currentDialog;
    private int callId;
    private int currentLoadingMsg;
    private boolean kill; // Check if edit is finished when destroying activity
    private int lastFailMessage; // For relog to resend last call

    private LinearLayout table;

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
            this.callId = state.getInt("callId");
            this.currentLoadingMsg = state.getInt("currentLoadingMsg");
            if (this.callId != 0) {
                TrytonCall.update(this.callId, new Handler(this));
                this.showLoadingDialog(this.currentLoadingMsg);
            }
            this.command = (DelayedRequester.Command) state.getSerializable("command");
        } else {
            this.view = viewInitializer;
            viewInitializer = null;
            this.viewId = viewIdInitializer;
            viewIdInitializer = 0;
            this.command = commandInitializer;
            if (this.command != null) {
                this.view = this.command.getView();
            }
            // This is the first call, need to update data for new fields
            loadView = (this.view == null);
            loadData = true;
        }
        // Init view
        this.setContentView(R.layout.form);
        this.table = (LinearLayout) this.findViewById(R.id.form_table);
        if (!loadView && !loadData) {
            this.initView();
        } else {
            if (loadView) {
                this.loadViewAndData();
            } else {
                this.loadDataAndMeta();
            }
        }
    }

    private void initView() {
        for (Model view : this.view.getStructure()) {
            Session s = Session.current;
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
                this.table.addView(label);
            }
            View v = FormViewFactory.getView(view, this.view,
                                             s.editedModel,
                                             s.prefs,
                                             this);
            if (view.hasAttribute("name")
                && view.getString("name").equals(Session.current.linkToSelf)) {
                // Hide many2one parent field
                v.setVisibility(View.GONE);
            }
            this.table.addView(v);
        }
    }
    
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("view", this.view);
        outState.putInt("viewId", this.viewId);
        outState.putInt("callId", this.callId);
        outState.putInt("currentLoadingMsg", this.currentLoadingMsg);
        outState.putSerializable("command", this.command);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.view != null) {
            this.refreshDisplay();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        this.updateTempModel();
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
            if (this.command == null) {
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
            } else {
                // Update command and get back
                this.updateTempModel();
                this.command.getData().merge(Session.current.tempModel);
                DataCache db = new DataCache(this);
                db.storeData(this.command.getData().getClassName(),
                             this.command.getData());
                this.finish();
                return true;
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
                this.save();
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                dialog.dismiss();
                this.kill = true;
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
                this.delete();
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
        int structIndex = -1;
        for (int j = 0; j < this.table.getChildCount(); j++) {
            structIndex++;
            View v = this.table.getChildAt(j);
            if (!FormViewFactory.isFieldView(v)) {
                // Check if it is a x2many label
                Model field = this.view.getStructure().get(structIndex);
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
            Model field = this.view.getStructure().get(structIndex);
            FormViewFactory.setValue(v, field, this.view, tmp,
                                     s.editedModel, s.prefs, this);
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

    /** Update temp model to current values */
    private void updateTempModel() {
        Model tmp;
        Model origin;
        tmp = Session.current.tempModel;
        origin = Session.current.editedModel;
        int structIndex = -1;
        for (int j = 0; j < this.table.getChildCount(); j++) {
            structIndex++;
            View v = this.table.getChildAt(j);
            if (!FormViewFactory.isFieldView(v)) {
                // Check if it is a x2many label
                Model field = this.view.getStructure().get(structIndex);
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
            Model field = this.view.getStructure().get(structIndex);
            Object value = FormViewFactory.getValue(v, field,
                                                    Session.current.prefs);
            if (value != FormViewFactory.NO_VALUE) {

                tmp.set(field.getString("name"), value);
            } else {
                // If NO_VALUE (not null) the value is ignored
                // but still set null to create the field if necessary
                String fieldName = field.getString("name");
                if (origin != null) {
                    // Origin always has a value, it will be merged
                } else {
                    // Origin doesn't have a value, set explicit null
                    if (!tmp.hasAttribute(fieldName)) {
                        tmp.set(fieldName, null);
                    }
                }
            }
        }
    }

    /** Load views and all data when done (by cascading the calls in handler) */
    private void loadViewAndData() {
        if (this.callId == 0) {
            this.showLoadingDialog(LOADING_DATA);
            String className = Session.current.tempModel.getClassName();
            this.callId = DataLoader.loadView(this, className,
                                              this.viewId, "form",
                                              new Handler(this), false);
        }
    }

    /** Load data count and rel fields, required for data.
     * Requires that views are loaded. */
    private void loadDataAndMeta() {
        if (this.callId == 0) {
            this.showLoadingDialog(LOADING_DATA);
            String className;
            className = Session.current.tempModel.getClassName();
            this.callId = DataLoader.loadRelFields(this, className,
                                                       new Handler(this),
                                                       false);
        }
    }

    private void loadData() {
        if (this.callId == 0) {
            String className;
            Integer id;
            className = Session.current.tempModel.getClassName();
            id = (Integer) Session.current.tempModel.get("id");
            // Add id to list to load for edit
            List<Integer> ids = new ArrayList<Integer>();
            if (id != null) {
                ids.add(id);
            }
            // Call (relies on DataLoader returning well with no id)
            this.callId = DataLoader.loadData(this, className, ids,
                                              this.relFields, this.view,
                                              new Handler(this), false);
        }
    }


    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_SAVE_OK:
            this.callId = 0;
            this.hideLoadingDialog();
            Toast t = Toast.makeText(this, R.string.data_send_done,
                                     Toast.LENGTH_SHORT);
            t.show();
            // Update data with fresh one
            Model m = (Model) msg.obj;
            if (Session.current.editedModel == null) {
                this.postCreate(m);
            } else {
                this.postUpdate(m);
            }
            break;
        case TrytonCall.CALL_DELETE_OK:
            this.callId = 0;
            this.hideLoadingDialog();
            this.postDelete();
            break;
        case TrytonCall.CALL_SAVE_NOK:
        case TrytonCall.CALL_DELETE_NOK:
        case DataLoader.DATA_NOK:
        case DataLoader.VIEWS_NOK:
            this.callId = 0;
            this.hideLoadingDialog();
            Exception e = (Exception) msg.obj;
            if (!AlertBuilder.showUserError(e, this)
                && !AlertBuilder.showUserError(e, this)) {
                if (Configure.getOfflineUse(this)) {
                    // Generic error, queue call and continue
                    t = Toast.makeText(this, R.string.data_send_queued,
                                       Toast.LENGTH_SHORT);
                    t.show();
                    if (msg.what == TrytonCall.CALL_DELETE_NOK) {
                        this.queueDelete();
                    } else {
                        if (Session.current.editedModel != null) {
                            this.queueUpdate();
                        } else {
                            this.queueCreate();
                        }
                    }
                } else {
                    // Show the error
                    AlertDialog.Builder b = new AlertDialog.Builder(this);
                    b.setTitle(R.string.error);
                    b.setMessage(R.string.network_error);
                    ((Exception)msg.obj).printStackTrace();
                    b.show();
                }
            }
            break;
        case DataLoader.VIEWS_OK:
            this.callId = 0;
            this.view = (ModelView)((Object[])msg.obj)[1];
            this.loadDataAndMeta();
            break;
        case DataLoader.RELFIELDS_OK:
            this.callId = 0;
            this.relFields = (List<RelField>)((Object[]) msg.obj)[1];
            this.loadData();
            break;
        case DataLoader.DATA_OK:
            this.callId = 0;
            List<Model> dataList = (List<Model>)((Object[])msg.obj)[1];
            if (dataList.size() > 0) {
                // Refresh edited model (not done when creating
                // as data is not loaded)
                Session.current.updateEditedModel(dataList.get(0));
            }
            this.initView();
            this.refreshDisplay();
            this.hideLoadingDialog();
            break;
        case TrytonCall.NOT_LOGGED:
            this.callId = 0;
            // Ask for relog
            this.lastFailMessage = (Integer) msg.obj;
            this.hideLoadingDialog();
            AlertBuilder.showRelog(this, new Handler(this));
            break;
        case AlertBuilder.RELOG_CANCEL:
            if (this.lastFailMessage == TrytonCall.CALL_DELETE_NOK) {
                // Nothing
            } else if (this.lastFailMessage == TrytonCall.CALL_SAVE_NOK) {
                // Nothing
            } else {
                this.finish();
            }
            break;
        case AlertBuilder.RELOG_OK:
            if (this.lastFailMessage == TrytonCall.CALL_DELETE_NOK) {
                this.sendDelete();
            } else if (this.lastFailMessage == TrytonCall.CALL_SAVE_NOK) {
                this.sendSave();
            } else {
                this.loadViewAndData();
            }
            break;
        }
        return true;
    }

    ///////////////////////////////
    // Save/Delete/Queue section //
    ///////////////////////////////

    private void save() {
        // If it is the top level model, actually save
        if (!Session.current.isEditingSub()) {
            this.sendSave();
        } else {
            if (Session.current.linkToSelf != null) {
                // It is a one2many field that is edited. It will be propagated
                // with parent save
                Toast t = Toast.makeText(this, R.string.data_send_done,
                                         Toast.LENGTH_SHORT);
                t.show();
                if (Session.current.editedModel == null) {
                    // Create
                    Session.current.addOne2Many();
                    this.endNew(Session.current.tempModel);
                } else {
                    // Update
                    Session.current.updateOne2Many();
                    this.endQuit();
                }
            } else {
                // Send the call, must update the parent on return to add the id
                this.sendSave();
            }
        }
    }

    private void delete() {
        // If it is the top level model, actually save
        if (!Session.current.isEditingSub()) {
            this.sendDelete();
        } else {
            if (Session.current.linkToSelf != null) {
                // It is a one2many field
                Session.current.deleteOne2Many();
                this.endQuit();
            } else {
                this.sendDelete();
            }
        }
    }

    /** Show dialog and send save call to the server. Callback is in handler. */
    private void sendSave() {
        if (DelayedRequester.current.getQueueSize() > 0) {
            // There is a queue waiting, don't mess command order by sending
            // a new command before the other
            Toast t = Toast.makeText(this, R.string.data_send_queued,
                                     Toast.LENGTH_SHORT);
            t.show();
            if (Session.current.editedModel != null) {
                this.queueUpdate();
            } else {
                this.queueCreate();
            }
        } else {
            // Standard connected behaviour: send to server
            this.showLoadingDialog(LOADING_SEND);
            Session s = Session.current;
            this.callId = TrytonCall.saveData(s.userId, s.cookie, s.prefs,
                                              s.tempModel, s.editedModel, this,
                                              new Handler(this));
        }
    }

    private void sendDelete() {
        if (DelayedRequester.current.getQueueSize() > 0) {
            // There is a queue waiting, don't mess command order by sending
            // a new command before the other
            Toast t = Toast.makeText(this, R.string.data_send_queued,
                                     Toast.LENGTH_SHORT);
            t.show();
            this.queueDelete();
        } else {
            // Standard connected behaviour: send to server
            this.showLoadingDialog(LOADING_SEND);
            Session s = Session.current;
            this.callId = TrytonCall.deleteData(s.userId, s.cookie, s.prefs,
                                                (Integer) s.editedModel.get("id"),
                                                s.editedModel.getClassName(),
                                                new Handler(this));
        }
    }

    /** Add a create call to the queue, update db and start a new record */
    private void queueCreate() {
        Model newModel = Session.current.tempModel;
        Model queuedModel = new Model(newModel.getClassName());
        queuedModel.merge(newModel);
        // Make it pending (also sets temp id for create)
        DelayedRequester.current.queueCreate(queuedModel,
                                             this.view, this);
        this.postCreate(queuedModel);
    }
    
    /** Add a update call to the queue, update db and return */
    private void queueUpdate() {
        Model newModel = Session.current.tempModel;
        Model oldModel = Session.current.editedModel;
        Model queuedModel = new Model(newModel.getClassName());
        queuedModel.merge(oldModel);
        queuedModel.merge(newModel);
        // Make it pending
        DelayedRequester.current.queueUpdate(queuedModel,
                                             this.view, this);
        this.postUpdate(queuedModel);
    }

    /** Add a delete call to the queue, update db and return */
    private void queueDelete() {
        Model newModel = Session.current.tempModel;
        DelayedRequester.current.queueDelete(newModel, this);
        this.postDelete();
    }

    /////////////////////
    // Post processing //
    /////////////////////

    /** Delete in database and go back */
    private void postDelete() {
        // Delete from local cache and go back
        DataCache db = new DataCache(this);
        db.deleteData(Session.current.editedModel);
        this.endQuit();
    }

    private void postCreate(Model newModel) {
        // Save locally
        DataCache db = new DataCache(this);
        db.addOne(newModel.getClassName());
        db.storeData(newModel.getClassName(), newModel);
        // Update parent if necessary
        if (Session.current.linkToParent != null) {
            // Add the id to parent
            int id = (Integer) newModel.get("id");
            Session.current.addToParent(id);
        }
        this.endNew(newModel);
    }

    private void postUpdate(Model updated) {
        // Save locally and continue
        DataCache db = new DataCache(this);
        db.storeData(updated.getClassName(), updated);
        this.endQuit();
    }

    private void endNew(Model newModel) {
        if (Session.current.linkToParent != null) {
            // Create new record
            String linkToParent = Session.current.linkToParent;
            String linkToSelf = Session.current.linkToSelf;
            Session.current.finishEditing();
            Session.current.editNewModel(newModel.getClassName(),
                                         linkToParent,
                                         linkToSelf);
        } else {
            // Just create a new record
            Session.current.finishEditing();
            Session.current.editNewModel(newModel.getClassName());
        }
        TreeView.setDirty();
        this.refreshDisplay();
    }

    private void endQuit() {
        // Clear edition and return back to tree
        this.kill = true;
        TreeView.setDirty();
        this.finish();
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
        if (this.command == null) {
            // Enable/disable save
            MenuItem save = menu.findItem(MENU_SAVE_ID);
            save.setEnabled(Session.current.editedIsDirty());
            // Remove delete for creation and many2many
            if (Session.current.editedModel == null
                || (Session.current.isEditingSub()
                    && Session.current.linkToSelf == null)) {
                menu.removeItem(MENU_DEL_ID);
            }
        } else {
            // Keep only save
            menu.removeItem(MENU_DEL_ID);
            menu.removeItem(MENU_LOGOUT_ID);
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
            if (this.command == null) {
                // Send save call
                this.save();
            } else {
                // Update command and get back to pending requests
                this.command.getData().merge(Session.current.tempModel);
                DataCache db = new DataCache(this);
                db.storeData(this.command.getData().getClassName(),
                             this.command.getData());
                this.finish();
            }
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
