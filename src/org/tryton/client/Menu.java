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
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.models.MenuEntry;
import org.tryton.client.tools.TrytonCall;
import org.tryton.client.data.DataLoader;
import org.tryton.client.data.Session;
import org.tryton.client.data.MenuCache;
import org.tryton.client.tools.AlertBuilder;
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

    public static final int MODE_NAV = 0;
    public static final int MODE_CACHE = 1;

    private List<MenuEntry> entries;
    private List<Boolean> pickedEntries; // For caching
    private int callId;
    private int mode;
    /** The list of entries selected to cache. It is null when loading is not
     * running */
    private List<MenuEntry> entriesToCache;
    private int cacheProgress;

    private ListView menuList;
    private ProgressDialog loadingDialog;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Init data
        this.entries = new ArrayList<MenuEntry>();
        this.pickedEntries = new ArrayList<Boolean>();
        this.entriesToCache = null;
        if (state != null) {
            int count = state.getInt("count");
            for (int i = 0; i < count; i++) {
                MenuEntry entry = (MenuEntry) state.getSerializable("entry" + i);
                this.entries.add(entry);
            }
            for (int i = 0; i < count; i++) {
                boolean b = state.getBoolean("pick" + i);
                this.pickedEntries.add(b);
            }
            this.mode = state.getInt("mode");
            if (state.containsKey("cacheProgress")) {
                this.cacheProgress = state.getInt("cacheProgress");
                int cacheCount = state.getInt("toCacheCount");
                this.entriesToCache = new ArrayList<MenuEntry>();
                for (int i = 0; i < cacheCount; i++) {
                    MenuEntry entry = (MenuEntry) state.getSerializable("toCache" + i);
                    this.entriesToCache.add(entry);
                }
                this.showCachingDialog(this.cacheProgress);
                this.updateCachingMessage();
            }
            this.callId = state.getInt("callId");
            if (this.callId != 0) {
                DataLoader.update(callId, new Handler(this));
                this.showLoadingDialog();
            }
        } else if (entriesInitializer != null) {
            this.entries = entriesInitializer;
            for (int i = 0; i < this.entries.size(); i++) {
                this.pickedEntries.add(false);
            }
            entriesInitializer = null; // Reset as it will now be saved in state
            this.mode = MODE_NAV;
        }
        if (this.entries.size() == 0) {
            this.showLoadingDialog();
            DataLoader.loadMenu(this, new Handler(this), false);
        }
        // Init view
        this.setContentView(R.layout.menu);
        this.menuList = (ListView) this.findViewById(R.id.menu_list);
        this.menuList.setOnItemClickListener(this);
        // Link data to views
        this.updateMenus(this.entries);
    }

    public void onDestroy() {
        super.onDestroy();
        this.hideLoadingDialog();
    }

    private void updateMenus(List<MenuEntry> menus) {
        View cacheBar = this.findViewById(R.id.menu_loadbar);
        switch (this.mode) {
        case MODE_NAV:
            MenuEntryAdapter adapt = new MenuEntryAdapter(menus);
            this.menuList.setAdapter(adapt);
            cacheBar.setVisibility(View.GONE);
            break;
        case MODE_CACHE:
            adapt = new MenuEntryAdapter(menus, this.pickedEntries);
            this.menuList.setAdapter(adapt);
            cacheBar.setVisibility(View.VISIBLE);
            break;
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("count", this.entries.size());
        for (int i = 0; i < this.entries.size(); i++) {
            outState.putSerializable("entry" + i, this.entries.get(i));
            outState.putBoolean("pick" + i, this.pickedEntries.get(i));
        }
        outState.putInt("callId", this.callId);
        outState.putInt("mode", this.mode);
        if (this.entriesToCache != null) {
            outState.putInt("cacheProgress", this.cacheProgress);
            outState.putInt("toCacheCount", this.entriesToCache.size());
            for (int i = 0; i < this.entriesToCache.size(); i++) {
                outState.putSerializable("toCache" + i, this.entriesToCache.get(i));
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // Cancel caching
            if (this.mode == MODE_CACHE && this.callId == 0) {
                this.cancelCache(null);
                return true;
            }
        }
        // Use default behaviour
        return super.onKeyDown(keyCode, event);
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

    public void showCachingDialog(int progress) {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(false);
            this.loadingDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            this.loadingDialog.setMax(this.entriesToCache.size());
            this.loadingDialog.setMessage(""); // Required to update it later
            this.loadingDialog.setProgress(progress);
            this.loadingDialog.setOnCancelListener(this);
            this.loadingDialog.show();
        }
    }

    public void updateCachingMessage() {
        String pmsg = String.format(this.getString(R.string.menu_caching,
                                                   this.entriesToCache.get(this.cacheProgress).getLabel()));
        this.loadingDialog.setMessage(pmsg);
        this.loadingDialog.setProgress(this.cacheProgress);
    }

    public void onCancel(DialogInterface dialog) {
        DataLoader.cancel(this.callId);
        this.callId = 0;
        this.loadingDialog = null;
        if (this.entriesToCache == null) {
            // Cancel menu loading
            this.finish();
        } else {
            // Cancel cache loading            
            this.cancelCache(null);
        }
    }

    /** Hide the loading dialog if shown. */
    public void hideLoadingDialog() {
        if (this.loadingDialog != null) {
            this.loadingDialog.dismiss();
            this.loadingDialog = null;
        }
    }

    /** Handle loader feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case DataLoader.MENUS_OK:
            this.callId = 0;
            List<MenuEntry> menus = (List) msg.obj;
            // Update the view
            this.entries = menus;
            this.pickedEntries.clear();
            for (int i = 0; i < this.entries.size(); i++) {
                this.pickedEntries.add(false);
            }
            this.updateMenus(menus);
            this.hideLoadingDialog();
            break;
        case DataLoader.MENUS_NOK:
            this.hideLoadingDialog();
            this.callId = 0;
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            ((Exception)msg.obj).printStackTrace();
            break;
        case DataLoader.MENUDATA_OK:
            this.callId = 0;
            this.cacheProgress++;
            if (this.cacheProgress < this.entriesToCache.size()) {
                // Load next
                this.loadCache();
            } else {
                // Caching done
                this.hideLoadingDialog();
                this.entriesToCache = null;
                this.cacheProgress = 0;
                this.mode = MODE_NAV;
                this.updateMenus(this.entries);
            }
            break;
        case TrytonCall.NOT_LOGGED:
            this.callId = 0;
            // Ask for relog
            this.hideLoadingDialog();
            AlertBuilder.showRelog(this, new Handler(this));
            break;
        case AlertBuilder.RELOG_CANCEL:
            if (this.entriesToCache == null) {
                // Cancel menu loading relog
                this.finish();
            } else {
                // Cancel caching
                this.cancelCache(null);
            }
            break;
        case AlertBuilder.RELOG_OK:
            if (this.entriesToCache == null) {
                // Restart menu loading
                this.showLoadingDialog();
                DataLoader.loadMenu(this, new Handler(this), false);
            } else {
                // Restart caching
                this.loadCache();
            }
            break;
        }
        return true;
    }

    public void onItemClick(AdapterView list, View v, int position,
                            long id) {
        MenuEntryItem itemClicked = (MenuEntryItem) v;
        MenuEntry clickedMenu = itemClicked.getMenuEntry();
        if (this.mode == MODE_NAV) {
            if (clickedMenu.getChildren().size() > 0) {
                // Setup a new instance of Menu and call it
                Menu.setup(clickedMenu.getChildren());
                Intent i = new Intent(this, Menu.class);
                this.startActivity(i);
            } else if (clickedMenu.getActionType() != null) {
                String action = clickedMenu.getActionType();
                if (action.equals("ir.action.act_window")) {
                    // Setup a tree view and go to it
                    TreeView.setup(clickedMenu);
                    Intent i = new Intent(this, TreeView.class);
                    this.startActivity(i);
                } else if (action.equals("ir.action.wizard")) {
                    Toast t = Toast.makeText(this,
                                             R.string.general_not_supported,
                                             Toast.LENGTH_SHORT);
                    t.show();
                } else {
                    Toast t = Toast.makeText(this,
                                             R.string.general_not_supported,
                                             Toast.LENGTH_SHORT);
                    t.show();
                }
            }
        } else {
            MenuEntryItem item = (MenuEntryItem) v;
            boolean selected = this.pickedEntries.get(position);
            selected = !selected;
            item.setSelection(selected);
            this.pickedEntries.remove(position);
            this.pickedEntries.add(position, selected);
        }
    }

    private void loadCache() {
        if (this.callId == 0) {
            if (this.loadingDialog == null) {
                this.showCachingDialog(this.cacheProgress);
            }
            MenuEntry toCache = this.entriesToCache.get(this.cacheProgress);
            this.callId = DataLoader.loadFullEntry(this, toCache,
                                                   new Handler(this), true);
            if (this.callId == -1) {
                // Simulate a loading done message to load next
                Message m = new Message();
                m.what = DataLoader.MENUDATA_OK;
                this.handleMessage(m);
            } else {
                this.updateCachingMessage();
            }
        }
    }

    public void startCaching(View v) {
        // Reset cache values
        this.cacheProgress = 0;
        this.entriesToCache = new ArrayList<MenuEntry>();
        // Setup cache values
        for (int i = 0; i < this.entries.size(); i++) {
            boolean selected = this.pickedEntries.get(i);
            if (selected) {
                MenuEntry menu = this.entries.get(i);
                this.entriesToCache.add(menu);
                this.entriesToCache.addAll(menu.getAllChildren());
            }
        }
        for (int i = 0; i < this.entriesToCache.size(); i++) {
            MenuEntry menu = this.entriesToCache.get(i);
            if (menu.getChildren().size() > 0) {
                this.entriesToCache.remove(menu);
                i--;
            }
        }
        DataLoader.initEntriesLoading();
        // Run (or not)
        if (this.entriesToCache.size() > 0) {
            this.loadCache();
        } else {
            this.entriesToCache = null;
            this.mode = MODE_NAV;
        }
    }

    public void cancelCache(View v) {
        this.mode = MODE_NAV;
        this.entriesToCache = null;
        this.updateMenus(this.entries);
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_LOGOUT_ID = 0;
    private static final int MENU_PREFERENCES_ID = 1;
    private static final int MENU_CACHE_ID = 2;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Create and add configuration entry
        MenuItem logout = menu.add(android.view.Menu.NONE, MENU_LOGOUT_ID, 100,
                                   this.getString(R.string.general_logout));
        logout.setIcon(R.drawable.tryton_log_out);
        MenuItem prefs = menu.add(android.view.Menu.NONE, MENU_PREFERENCES_ID,
                                  200,
                                  this.getString(R.string.general_preferences));
        prefs.setIcon(R.drawable.tryton_preferences_system);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        if (this.mode != MODE_CACHE) {
            if (menu.findItem(MENU_CACHE_ID) == null) {
                MenuItem cacheMode = menu.add(android.view.Menu.NONE,
                                              MENU_CACHE_ID, 50,
                                              this.getString(R.string.menu_cache));
                cacheMode.setIcon(R.drawable.tryton_save);
            }
        } else {
            menu.removeItem(MENU_CACHE_ID);
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
        case MENU_PREFERENCES_ID:
            Intent i = new Intent(this, Preferences.class);
            this.startActivity(i);
            break;
        case MENU_CACHE_ID:
            this.mode = MODE_CACHE;
            this.pickedEntries.clear();
            for (int j = 0; j < this.entries.size(); j++) {
                this.pickedEntries.add(false);
            }
            this.updateMenus(this.entries);
            break;
        }
        return true;
    }

}