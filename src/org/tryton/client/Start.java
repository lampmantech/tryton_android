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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.tryton.client.tools.TrytonCall;

/** Start activity. Shows login form. */
public class Start extends Activity implements Handler.Callback {

    private String serverVersion;

    private TextView versionLabel;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            // Recreated from a saved state
            this.serverVersion = state.getString("version");
        }
        // Load configuration for TrytonCall
        TrytonCall.setHost(Configure.getHost(this));
        // Load views from xml resource
        setContentView(R.layout.main);
        this.versionLabel = (TextView) this.findViewById(R.id.server_version);
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
    public void onStart() {
        super.onStart();
        // If server is unknown try to get its version
        // When returning from configuration TrytonCall host may have changed
        if (this.serverVersion == null) {
            TrytonCall.serverVersion(new Handler(this));
        }
    }

    /** Update display according to stored version */
    public void updateVersionLabel() {
        if (this.serverVersion == null) {
            // Unknown version
            this.versionLabel.setText(null);
        } else {
            this.versionLabel.setText(this.serverVersion);
        }
    }

    /** Handle TrytonCall feedback. */
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
        case TrytonCall.CALL_VERSION_OK:
            this.serverVersion = (String) msg.obj;
            this.updateVersionLabel();
            break;
        }
        return true;
    }

    // Mapped by xml on login button click
    public void login(View v) {
        System.out.println("Click");
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
