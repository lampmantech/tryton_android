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
import android.widget.Toast;

import org.tryton.client.data.Session;
import org.tryton.client.tools.TrytonCall;

/** Start activity. Shows login form. */
public class Start extends Activity implements Handler.Callback {

    private String serverVersion;

    private TextView versionLabel;
    private EditText login;
    private EditText password;
    private Button loginBtn;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Check if user is already logged in to skip login
        if (Session.current.userId != -1) {
            Intent i = new Intent(this, org.tryton.client.Menu.class);
            this.startActivity(i);
            this.finish();
            return;
        }
        if (state != null) {
            // Recreated from a saved state
            this.serverVersion = state.getString("version");
        }
        // Load configuration for TrytonCall
        TrytonCall.setup(Configure.getHost(this), Configure.getDatabase(this));
        // Load views from xml resource
        setContentView(R.layout.main);
        this.versionLabel = (TextView) this.findViewById(R.id.server_version);
        this.login = (EditText) this.findViewById(R.id.login);
        this.password = (EditText) this.findViewById(R.id.password);
        this.loginBtn = (Button) this.findViewById(R.id.login_btn);
        // Update server version label
        this.updateVersionLabel();
    }

    /** Save current state before killing, if necessary (called by system) */
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(this.serverVersion, "version");
    }

    /** Called when activity comes to front */
    @Override
    public void onResume() {
        super.onResume();
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
                Session.current.user = this.login.getText().toString();
                Session.current.password = this.login.getText().toString();
                Session.current.userId = (Integer) resp[0];
                Session.current.cookie = (String) resp[1];
                // Go to menu
                Intent i = new Intent(this, org.tryton.client.Menu.class);
                this.startActivity(i);
                // Kill the current activity until logout
                this.finish();
            } else {
                // Show login error
                Toast t = Toast.makeText(this, R.string.login_bad_login,
                                         Toast.LENGTH_LONG);
                t.show();
            }
            break;
        case TrytonCall.CALL_LOGIN_NOK:
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            break;
        }
        return true;
    }

    // Mapped by xml on login button click
    public void login(View v) {
        TrytonCall.login(this.login.getText().toString(),
                         this.password.getText().toString(),
                         new Handler(this));
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_CONFIG_ID = 0;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Create and add configuration entry
        MenuItem config = menu.add(Menu.NONE, MENU_CONFIG_ID, 0,
                                   this.getString(R.string.menu_config));
        config.setIcon(android.R.drawable.ic_menu_preferences);
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
        }
        return true;
    }

}
