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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.tryton.client.data.DataCache;
import org.tryton.client.data.Session;
import org.tryton.client.models.Preferences;
import org.tryton.client.tools.AlertBuilder;
import org.tryton.client.tools.TrytonCall;

/** Start activity. Shows login form. */
public class Start extends Activity implements Handler.Callback {

    /** Boolean to wake up login or make it inactive when user is logged in.
     * The activity goes down when user logs in and is woken up when the
     * user logs out.
     */
    private static boolean awake = true;

    public static boolean isAwake() {
        return awake;
    }

    private String serverVersion;

    private TextView versionLabel;
    private EditText login;
    private EditText password;
    private Button loginBtn;
    private ProgressDialog loadingDialog;
    private int callId;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            // Recreated from a saved state
            this.serverVersion = state.getString("version");
            this.callId = state.getInt("callId");
            if (this.callId != 0) {
                TrytonCall.update(this.callId, new Handler(this));
                this.showLoadingDialog();
            }
        }
        // Load configuration for TrytonCall
        TrytonCall.setup(Configure.getSSL(this), Configure.getHost(this),
                         Configure.getPort(this), Configure.getDatabase(this));
        // Load views from xml resource
        setContentView(R.layout.main);
        this.versionLabel = (TextView) this.findViewById(R.id.server_version);
        this.login = (EditText) this.findViewById(R.id.login);
        this.password = (EditText) this.findViewById(R.id.password);
        this.loginBtn = (Button) this.findViewById(R.id.login_btn);
        // Update server version label
        this.updateVersionLabel();
        // Set last user on start
        if (state == null) {
            String user = Configure.getLastUser(this);
            if (user != null) {
                this.login.setText(user);
            }
        }
    }

    /** Save current state before killing, if necessary (called by system) */
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("version", this.serverVersion);
        outState.putInt("callId", this.callId);
    }

    public void onDestroy() {
        super.onDestroy();
        this.hideLoadingDialog();
    }

    /** Called when activity comes to front */
    @Override
    public void onResume() {
        super.onResume();
        boolean starting = this.getIntent().getBooleanExtra("starting", true);
        this.getIntent().putExtra("starting", false);
        // On first start (when coming from dashboard) the intent is clean
        // Check if user is already logged in to skip login on launch
        if (starting && Session.current.userId != -1) {
            // Starting with a logged in user: go to menu
            Intent i = new Intent(this, org.tryton.client.Menu.class);
            this.startActivity(i);
            awake = false; // zzzz
            return;
        }
        if (!isAwake()) {
            // Coming back without logout
            if (Configure.getAutoLogout(this)) {
                processLogout();
            } else {
                // zzz (quit)
                this.finish();
                return;
            }
        }
        // When returning from configuration TrytonCall host may have changed
        TrytonCall.serverVersion(new Handler(this));
    }

    /** Update display according to stored version */
    public void updateVersionLabel() {
        if (this.serverVersion == null) {
            // Unknown version, server is unavailable
            this.versionLabel.setText(R.string.login_server_unavailable);
            this.login.setEnabled(false);
            this.password.setEnabled(false);
            this.loginBtn.setEnabled(false);
        } else {
            this.versionLabel.setText(this.serverVersion);
            this.login.setEnabled(true);
            this.password.setEnabled(true);
            this.loginBtn.setEnabled(true);
        }
    }

    /** Handle TrytonCall feedback. */
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_VERSION_OK:
            this.serverVersion = (String) msg.obj;
            this.updateVersionLabel();
            break;
        case TrytonCall.CALL_VERSION_NOK:
            this.serverVersion = null;
            this.updateVersionLabel();
            if (msg.obj instanceof Exception) {
                ((Exception)msg.obj).printStackTrace();
            }
            break;
        case TrytonCall.CALL_LOGIN_OK:
            if (msg.arg1 != 0) {
                // Login successfull. Save data in session
                Object[] resp = (Object[]) msg.obj;
                int userId = (Integer) resp[0];
                String cookie = (String) resp[1];
                Session.current.user = this.login.getText().toString();
                Session.current.password = this.password.getText().toString();
                Session.current.userId = userId;
                Session.current.cookie = cookie;
                // Save login for next time
                Configure.saveUser(this.login.getText().toString(), this);
                // Get user preferences
                this.callId = TrytonCall.getPreferences(userId, cookie,
                                                        new Handler(this));
            } else {
                this.hideLoadingDialog();
                this.callId = 0;
                // Show login error
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setMessage(R.string.login_bad_login);
                b.setPositiveButton(R.string.general_ok, null);
                b.show();
            }
            break;
        case TrytonCall.CALL_PREFERENCES_OK:
            this.hideLoadingDialog();
            this.callId = 0;
            // Save the preferences
            Session.current.prefs = (Preferences) msg.obj;
            // Clear password field
            this.password.setText("");
            // Go to menu
            Intent i = new Intent(this, org.tryton.client.Menu.class);
            this.startActivity(i);
            // Check if data cache is still valid (maybe host changed)
            DataCache db = new DataCache(this);
            if (!db.checkDatabase(Configure.getDatabaseCode(this))) {
                db.clear();
            }
            db.setHost(Configure.getDatabaseCode(this));
            // Falling asleep...
            awake = false;
            break;
        case TrytonCall.CALL_LOGIN_NOK:
        case TrytonCall.CALL_PREFERENCES_NOK:
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
        }
        return true;
    }

    // Mapped by xml on login button click
    public void login(View v) {
        // Show loading dialog
        this.showLoadingDialog();
        // Launch call (will be handled by handleMessage on response)
        this.callId = TrytonCall.login(this.login.getText().toString(),
                                       this.password.getText().toString(),
                                       new Handler(this));
    }

    /** Do the logout stuff */
    private static void processLogout() {
        // Clear on server then on client
        TrytonCall.logout(Session.current.userId, Session.current.cookie);
        Session.current.clear();
        awake = true; // Reset sleeping state
    }

    /** Logout from an other activity. Brings back login screen. */
    public static void logout(Activity caller) {
        processLogout();
        // Call back the login screen.
        // FLAG_ACTIVITY_CLEAR_TOP will erase all activities on top of it.
        Intent i = new Intent(caller, Start.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        caller.startActivity(i);
    }

    /** Show the loading dialog if not already shown. */
    public void showLoadingDialog() {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            this.loadingDialog.setCancelable(false);
            this.loadingDialog.setMessage(this.getString(R.string.login_logging_in));
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

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_CONFIG_ID = 0;
    private static final int MENU_ABOUT_ID = 1;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Create and add configuration entry
        MenuItem config = menu.add(Menu.NONE, MENU_CONFIG_ID, 0,
                                   this.getString(R.string.general_config));
        config.setIcon(R.drawable.tryton_preferences_system);
        // Create and add about entry
        MenuItem about = menu.add(Menu.NONE, MENU_ABOUT_ID, 10,
                                  this.getString(R.string.general_about));
        about.setIcon(R.drawable.tryton_help);
        return true;
    }

    /** Called on menu selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_CONFIG_ID:
            // Start the configuration activity
            Intent i = new Intent(this, Configure.class);
            this.startActivity(i);
            break;
        case MENU_ABOUT_ID:
            // Show about
            i = new Intent(this, About.class);
            this.startActivity(i);
        }
        return true;
    }

}
