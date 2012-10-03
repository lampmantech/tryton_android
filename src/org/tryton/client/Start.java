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
import android.view.Menu;
import android.view.MenuItem;

/** Start activity. Shows login form. */
public class Start extends Activity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load views from xml resource
        setContentView(R.layout.main);
        
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
