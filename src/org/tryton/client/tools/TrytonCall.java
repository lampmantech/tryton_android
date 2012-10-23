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
package org.tryton.client.tools;

import android.os.Handler;
import android.os.Message;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.alexd.jsonrpc.JSONRPCClient;
import org.alexd.jsonrpc.JSONRPCException;
import org.alexd.jsonrpc.JSONRPCParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.models.Preferences;

/** Tryton requester.
 * It makes asynchronous calls to the tryton server set by setHost.
 * Responses are given back to caller through handlers. */
public class TrytonCall {

    public static final int CALL_VERSION_OK = 0;
    public static final int CALL_VERSION_NOK = -1;
    public static final int CALL_LOGIN_OK = 1;
    public static final int CALL_LOGIN_NOK = -2;
    public static final int CALL_PREFERENCES_OK = 2;
    public static final int CALL_PREFERENCES_NOK = -3;
    public static final int CALL_MENUS_OK = 3;
    public static final int CALL_MENUS_NOK = -4;
    public static final int CALL_VIEWS_OK = 4;
    public static final int CALL_VIEWS_NOK = -5;
    public static final int CALL_DATA_OK = 5;
    public static final int CALL_DATA_NOK = -6;
    
    private static JSONRPCClient c;
    private static final JSONRPCParams.Versions version =
        JSONRPCParams.Versions.VERSION_2;
    private static int timeout = 20000;
    private static int soTimeout = 30000;

    public static boolean setup(String host, String database) {
        if (host == null || host.equals("")
            || database == null || database.equals("")) {
            c = null;
            return false;
        }
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        if (!host.endsWith("/")) {
            host = host + "/";
        }
        c = JSONRPCClient.create(host + database, TrytonCall.version);
        c.setConnectionTimeout(timeout);
        c.setSoTimeout(soTimeout);
        return true;
    }

    public static boolean serverVersion(final Handler h) {
        if (c == null) {
            return false;
        }
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                try {
                    Object resp = c.call("common.server.version",
                                         JSONObject.NULL, JSONObject.NULL);
                    String version = null;            
                    if (resp instanceof String && (String) resp != "") {
                        // Ok, send version back
                        m.what = CALL_VERSION_OK;
                        m.obj = resp;
                    } else {
                        // Nok
                        m.what = CALL_VERSION_NOK;
                        m.obj = new Exception("Incorrect response type "
                                              + resp);
                    }
                } catch (Exception e) {
                    m.what = CALL_VERSION_NOK;
                    m.obj = e;
                }
                m.sendToTarget();
            }
        }.start();
        return true;
    }

    /** Call to login.
     * Message will contain success on arg1 and if success obj will be
     * a array with obj[0] as user id (integer) and obj[1] the cookie (string).
     */
    public static boolean login(final String user, final String password,
                                final Handler h) {
        if (c == null) {
            return false;
        }
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                try {
                    Object resp = c.call("common.db.login", user, password);
                    boolean success = false;
                    Object obj = null;
                    if (resp instanceof Boolean
                        && ((Boolean)resp).booleanValue() == false) {
                        // Login failed
                        m.what = CALL_LOGIN_OK;
                        m.arg1 = 0;
                    } else if (resp instanceof JSONArray
                               && ((JSONArray)resp).length() == 2) {
                        // Login succeeded
                        m.what = CALL_LOGIN_OK;
                        m.arg1 = 1;
                        m.obj = new Object[]{((JSONArray)resp).getInt(0),
                                             ((JSONArray)resp).getString(1)};
                    } else {
                        // Unknown result
                        m.what = CALL_LOGIN_NOK;
                        m.obj = new Exception("Unknown response type " + resp);
                    }
                } catch (Exception e) {
                    m.what = CALL_LOGIN_NOK;
                    m.obj = e;
                }
                m.sendToTarget();
            }
        }.start();
        return true;
    }

    /** Logout. This is a send and forget call (don't expect any result) */
    public static boolean logout(final int userId, final String cookie) {
        if (c == null) {
            return false;
        }
        new Thread() {
            public void run() {
                try {
                    Object resp = c.call("common.db.logout", userId, cookie);
                } catch (Exception e) {
                }
            }
        }.start();
        return true;
    }

    public static boolean getPreferences(final int userId, final String cookie,
                                         final Handler h) {
        if (c == null) {
            return false;
        }
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                try {
                    Object resp = c.call("model.res.user.get_preferences",
                                         userId, cookie, true, new JSONObject());
                    Object obj = null;
                    if (resp instanceof JSONObject) {
                        Preferences prefs = new Preferences((JSONObject) resp);
                        m.what = CALL_PREFERENCES_OK;
                        m.obj = prefs;
                    } else {
                        // Unknown result
                        m.what = CALL_PREFERENCES_NOK;
                        m.obj = new Exception("Unknown response type " + resp);
                    }
                } catch (Exception e) {
                    m.what = CALL_PREFERENCES_NOK;
                    m.obj = e;
                }
                m.sendToTarget();
            }
        }.start();
        return true;
    }

    /** Utility function to search and read models in one function call. */
    private static JSONArray search(int userId, String cookie,
                                    Preferences prefs, String model,
                                    List args, int offset, int count)
        throws JSONRPCException, JSONException {
        if (c == null) {
            return null;
        }
        // First step: search ids
        JSONArray jsArgs = new JSONArray();
        if (args != null) {
            // TODO: convert the list of arguments to a JSONArray
        }
        Object resp = c.call(model + ".search", userId, cookie, jsArgs,
                             offset, count, JSONObject.NULL, prefs.json());
        if (resp instanceof JSONArray) {
            // Get the ids
            JSONArray jsIds = (JSONArray) resp;
            int[] ids = new int[jsIds.length()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = jsIds.getInt(i);
            }
            // Read the models
            resp = c.call(model + ".read", userId, cookie, jsIds,
                          new JSONArray(), prefs.json());
            if (resp instanceof JSONArray) {
                // We've got them!
                return (JSONArray) resp;
            }
        }
        return null;
    }

    /** Get the full menu as a list of top level entries with children. */
    public static boolean getMenus(final int userId, final String cookie,
                                   final Preferences prefs, final Handler h) {
        /* The procedure is broken into 5 parts:
         * Request the menus from the server
         * Request the icon ids and name from the server
         * Get the icons for the menu entries from the server
         * Build all menu entries individually
         * Build the tree */
        if (c == null) {
            return false;
        }
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                try {
                    // Get the first "1000" menu entries
                    JSONArray jsMenus = search(userId, cookie, prefs,
                                               "model.ir.ui.menu",
                                               null,
                                               0, 1000);
                    // Get icon ids
                    Map<String, Integer> iconIds = new HashMap<String, Integer>();
                    Object oIconIds = c.call("model.ir.ui.icon.list_icons",
                                             userId, cookie,
                                             prefs.json());
                    if (oIconIds instanceof JSONArray) {
                        // Convert the JSONArray to the map
                        JSONArray jsIconIds = (JSONArray) oIconIds;
                        for (int i = 0; i < jsIconIds.length(); i++) {
                            JSONArray jsIconId = jsIconIds.getJSONArray(i);
                            String name = jsIconId.getString(1);
                            int id = jsIconId.getInt(0);
                            iconIds.put(name, id);
                        }
                    }
                    // We've got the icon ids, get the icons themselves for each
                    // menu entry
                    Map<String, String> icons = new HashMap<String, String>();
                    // Look for the used ids
                    JSONArray usefullIconIds = new JSONArray();
                    Set<String> readIds = new HashSet<String>();
                    for (int i = 0; i < jsMenus.length(); i++) {
                        JSONObject jsMenu = jsMenus.getJSONObject(i);
                        String iconName = jsMenu.getString("icon");
                        if (iconName != null && !iconName.equals("")
                            && !readIds.contains(iconName)
                            && iconIds.containsKey(iconName)) {
                            readIds.add(iconName);
                            usefullIconIds.put(iconIds.get(iconName));
                        }
                    }
                    // Get them
                    Object oIcons = c.call("model.ir.ui.icon.read", userId,
                                           cookie, usefullIconIds,
                                           new JSONArray(),
                                           prefs.json());
                    if (oIcons instanceof JSONArray) {
                        JSONArray jsIcons = (JSONArray) oIcons;
                        for (int i = 0; i < jsIcons.length(); i++) {
                            JSONObject jsIcon = jsIcons.getJSONObject(i);
                            String name = jsIcon.getString("name");
                            String svg = jsIcon.getString("icon");
                            icons.put(name, svg);
                        }
                    }
                    // We have all we need, create menu entries and return them
                    // First build all entries
                    Map<Integer, MenuEntry> allMenus = new HashMap<Integer, MenuEntry>();
                    for (int i = 0; i < jsMenus.length(); i++) {
                        JSONObject jsMenu = jsMenus.getJSONObject(i);
                        String action = null;
                        if (!jsMenu.isNull("action")) {
                            action = jsMenu.getString("action");
                        }
                        String actionType = null;
                        int actionId = -1;
                        if (action != null) {
                            String[] split = action.split(",");
                            actionType = split[0];
                            actionId = Integer.parseInt(split[1]);
                        }
                        MenuEntry menu = new MenuEntry(jsMenu.getInt("id"),
                                                       jsMenu.getString("name"),
                                                       jsMenu.getInt("sequence"),
                                                       actionType, actionId);
                        String iconName = jsMenu.getString("icon");
                        if (icons.get(iconName) != null) {
                            menu.setIconSource(iconName, icons.get(iconName));
                        }
                        allMenus.put(jsMenu.getInt("id"), menu);
                    }
                    // Second pass, build tree
                    List<MenuEntry> topLevelMenu = new ArrayList<MenuEntry>();
                    for (int i = 0; i < jsMenus.length(); i++) {
                        JSONObject jsMenu = jsMenus.getJSONObject(i);
                        MenuEntry menu = allMenus.get(jsMenu.getInt("id"));
                        JSONArray childrenId = jsMenu.getJSONArray("childs");
                        // Append children of this entry
                        for (int j = 0; j < childrenId.length(); j++) {
                            int childId = childrenId.getInt(j);
                            menu.addChild(allMenus.get(childId));
                        }
                        if (jsMenu.isNull("parent")) {
                            // This is a top level menu entry
                            topLevelMenu.add(menu);
                          }
                    }
                    // We've got our menu tree, sort it by sequence
                    MenuEntry.SequenceSorter.recursiveSort(topLevelMenu);
                    // All done, return it
                    m.what = CALL_MENUS_OK;
                    m.obj = topLevelMenu;
                } catch (Exception e) {
                    m.what = CALL_MENUS_NOK;
                    m.obj = e;
                }
                m.sendToTarget();
            }
        }.start();
        return true;
    }

    public static boolean getViews(final int userId, final String cookie,
                                   final Preferences prefs,
                                   final MenuEntry entry,
                                   final Handler h) {
        if (c == null) {
            return false;
        }
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                JSONArray action = new JSONArray();
                action.put("ir.ui.menu");
                action.put(entry.getId());
                try {
                    Object oViews = c.call("model.ir.action.keyword.get_keyword",
                                           userId, cookie, "tree_open", action,
                                           prefs.json());
                    if (oViews instanceof JSONArray) {
                        // Get the view by type and other general data
                        JSONArray jsViews = (JSONArray) oViews;
                        JSONObject jsView = jsViews.getJSONObject(0);
                        String model = jsView.getString("res_model");
                        String name = jsView.getString("rec_name");
                        JSONArray jsViewTypes = jsView.getJSONArray("views");
                        // Get each view
                        ModelViewTypes modelViews = new ModelViewTypes(model);
                        for (int i = 0; i < jsViewTypes.length(); i++) {
                            JSONArray jsTypeId = jsViewTypes.getJSONArray(i);
                            int viewId = jsTypeId.getInt(0);
                            String type = jsTypeId.getString(1);
                            // Get the view itself
                            Object oView = c.call("model." + model
                                                  + ".fields_view_get",
                                                  userId, cookie, viewId,
                                                  type, prefs.json());
                            if (oView instanceof JSONObject) {
                                // Register the view for its type
                                JSONObject jsFields = (JSONObject) oView;
                                ModelView mView = new ModelView(jsFields);
                                ArchParser.buildTree(mView);
                                modelViews.putView(type, mView);
                            }
                        }
                        // Send them back
                        m.what = CALL_VIEWS_OK;
                        m.obj = modelViews;
                    }
                } catch (JSONException e) {
                    m.what = CALL_VIEWS_NOK;
                    m.obj = e;
                } catch (JSONRPCException e) {
                    m.what = CALL_VIEWS_NOK;
                    m.obj = e;
                }
                m.sendToTarget();
            }
        }.start();
        return true;
    }

    /** Get some data for a model */
    public static boolean getData(final int userId, final String cookie,
                           final Preferences prefs,
                           final String modelName,
                           final int offset, final int count,
                           final Handler h) {
        if (c == null) {
            return false;
        }
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                List<Model> allData = new ArrayList<Model>();
                try {
                    // Simply search the data and add them to a list
                    JSONArray result = search(userId, cookie, prefs,
                                              "model." + modelName,
                                              null, offset, count);
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject jsData = result.getJSONObject(i);
                        Model data = new Model(modelName, jsData);
                        allData.add(data);
                    }
                    // Send back the list to the handler
                    m.what = CALL_DATA_OK;
                    m.obj = allData;
                } catch (Exception e) {
                    m.what = CALL_DATA_NOK;
                    m.obj = e;
                }
                m.sendToTarget();
            }
        }.start();
        return true;
    }
}
