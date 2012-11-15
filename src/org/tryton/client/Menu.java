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
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.models.MenuEntry;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.Session;
import org.tryton.client.data.MenuCache;
import org.tryton.client.views.MenuEntryAdapter;
import org.tryton.client.views.MenuEntryItem;

public class Menu extends Activity
    implements Handler.Callback, AdapterView.OnItemClickListener,
               DialogInterface.OnCancelListener {

    /** Use a static initializer to pass data to the activity. If not set
        the whole menu will be loaded and first level shown. */
    private static void setup(List<MenuEntry> entries) {
        entriesInitializer = entries;
    }
    private static List<MenuEntry> entriesInitializer;

    private List<MenuEntry> entries;
    private int callId;

    private ListView menuList;
    private ProgressDialog loadingDialog;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        this.entries = new ArrayList<MenuEntry>();
        if (state != null) {
            int count = state.getInt("count");
            for (int i = 0; i < count; i++) {
                MenuEntry entry = (MenuEntry) state.getSerializable("entry" + i);
                this.entries.add(entry);
            }
            this.callId = state.getInt("callId");
            if (this.callId != 0) {
                TrytonCall.update(callId, new Handler(this));
                this.showLoadingDialog();
            }
        } else if (entriesInitializer != null) {
            this.entries = entriesInitializer;
            entriesInitializer = null; // Reset as it will now be saved in state
        }
        if (this.entries.size() == 0) {
            // There is no menu, check in cache and load it if empty
            List<MenuEntry> cachedMenus = null;
            try {
                cachedMenus = MenuCache.load(this);
            } catch (IOException e) {
                if (!(e instanceof FileNotFoundException)) {
                    // Ignore no cache exception
                    Log.w("Tryton", "Unable to load menu cache", e);
                }
            }
            if (cachedMenus != null) {
                // Got it
                this.entries = cachedMenus;
            } else if (this.callId == 0) {
                // Launch menu loading as it is empty
                this.showLoadingDialog();
                this.callId = TrytonCall.getMenus(Session.current.userId,
                                                  Session.current.cookie,
                                                  Session.current.prefs,
                                                  new Handler(this));
            }
        }
        // Init view
        this.setContentView(R.layout.menu);
        this.menuList = (ListView) this.findViewById(R.id.menu_list);
        this.menuList.setOnItemClickListener(this);
        // Link data to views
        this.updateMenus(this.entries);
    }

    private void updateMenus(List<MenuEntry> menus) {
        MenuEntryAdapter adapt = new MenuEntryAdapter(menus);
        this.menuList.setAdapter(adapt);
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("count", this.entries.size());
        for (int i = 0; i < this.entries.size(); i++) {
            outState.putSerializable("entry" + i, this.entries.get(i));
        }
        outState.putInt("callId", this.callId);
    }

    public void showLoadingDialog() {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            this.loadingDialog.setMessage(this.getString(R.string.menu_loading));
            this.loadingDialog.setOnCancelListener(this);
            this.loadingDialog.show();
        }
    }

    public void onCancel(DialogInterface dialog) {
        TrytonCall.cancel(this.callId);
        this.callId = 0;
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

    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_MENUS_OK:
            this.callId = 0;
            List<MenuEntry> menus = (List) msg.obj;
            // Cache the menu
            try {
                MenuCache.save(menus, this);
            } catch (IOException e) {
                    Log.w("Tryton", "Unable to cache menus", e);
            }
            // Update the view
            this.entries = menus;
            this.updateMenus(menus);
            this.hideLoadingDialog();
            break;
        case TrytonCall.CALL_MENUS_NOK:
            this.hideLoadingDialog();
            this.callId = 0;
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            ((Exception)msg.obj).printStackTrace();
            break;
        case TrytonCall.NOT_LOGGED:
            // TODO: this is brutal
            // Logout
            Start.logout(this);
            break;
        }
        return true;
    }

    public void onItemClick(AdapterView list, View v, int position,
                            long id) {
        MenuEntryItem itemClicked = (MenuEntryItem) v;
        MenuEntry clickedMenu = itemClicked.getMenuEntry();
        if (clickedMenu.getChildren().size() > 0) {
            // Setup a new instance of Menu and call it
            Menu.setup(clickedMenu.getChildren());
            Intent i = new Intent(this, Menu.class);
            this.startActivity(i);
        } else if (clickedMenu.getActionType() != null) {
            // Setup a tree view and go to it
            TreeView.setup(clickedMenu);
            Intent i = new Intent(this, TreeView.class);
            this.startActivity(i);
        }
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_LOGOUT_ID = 0;
    private static final int MENU_PREFERENCES_ID = 1;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add configuration entry
        MenuItem logout = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 100,
                                   this.getString(R.string.general_logout));
        logout.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        MenuItem prefs = menu.add(android.view.Menu.NONE, MENU_PREFERENCES_ID,
                                  200,
                                  this.getString(R.string.general_preferences));
        prefs.setIcon(android.R.drawable.ic_menu_preferences);
        return true;
    }

    /** Called on menu selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_LOGOUT_ID:
            Start.logout(this);
            break;
        case MENU_PREFERENCES_ID:
            Intent i = new Intent(this, Preferences.class);
            this.startActivity(i);
            break;
        }
        return true;
    }

}