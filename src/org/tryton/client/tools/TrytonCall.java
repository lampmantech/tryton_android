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

import android.content.Context;
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
import org.tryton.client.models.RelField;

/** Tryton requester.
 * It makes asynchronous calls to the tryton server set by setHost.
 * Responses are given back to caller through handlers. */
public class TrytonCall {

    public static final int NOT_LOGGED = -256;
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
    public static final int CALL_DATACOUNT_OK = 6;
    public static final int CALL_DATACOUNT_NOK = -7;
    public static final int CALL_RELFIELDS_OK = 7;
    public static final int CALL_RELFIELDS_NOK = -8;
    public static final int CALL_RELDATA_OK = 8;
    public static final int CALL_RELDATA_NOK = -9;
    public static final int CALL_SAVE_OK = 9;
    public static final int CALL_SAVE_NOK = -10;
    public static final int CALL_DELETE_OK = 10;
    public static final int CALL_DELETE_NOK = -11;
    
    private static JSONRPCClient c;
    private static final JSONRPCParams.Versions version =
        JSONRPCParams.Versions.VERSION_2;
    private static int timeout = 20000;
    private static int soTimeout = 30000;

    private static int callSequence = 1;
    private static Map<Integer, Handler> handlers = new HashMap<Integer, Handler>();

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

    public static void cancel(int callId) {
        handlers.remove(callId);
    }
    public static void update(int callId, Handler h) {
        if (handlers.containsKey(callId)) {
            handlers.put(callId, h);
        }
    }
    /** Send the message to the right handler if it was updated or not
     * if it was canceled. */
    private static void sendMessage(int callId, Message m) {
        if (handlers.containsKey(callId)) {
            Handler h = handlers.get(callId);
            if (m.getTarget().equals(h)) {
                m.sendToTarget();
            } else {
                m.setTarget(h);
                m.sendToTarget();
            }
            handlers.remove(callId);
        }
    }
    /** Check if a call has been canceled or not. */
    private static boolean isCanceled(int callId) {
        return (!handlers.containsKey(callId));
    }

    /** Check if the exception is an user error and return the message.
     * First string is the message, second is the optionnal description,
     * which is "" when nothing.
     * Returns null if the exception is not an user error. */
    public static String[] getUserError(Exception error) {
        if (!(error instanceof JSONRPCException)) {
            return null;
        }
        try {
            JSONArray jsError = new JSONArray(error.getMessage());
            String type = jsError.getString(0);
            if (type.equals("UserError")) {
                JSONArray jsMsg = jsError.getJSONArray(1);
                String[] ret = new String[]{jsMsg.getString(0),
                                            jsMsg.getString(1)};
                return ret;
            }
        } catch (JSONException e) {
            // This is not the structure we expect
        }
        return null;
    }
    /** Check if the exception is an user warning and return the message.
     * First string is the message, second is the optionnal description,
     * which is "" when nothing.
     * Returns null if the exception is not an user error. */
    public static String[] getUserWarning(Exception error) {
        if (!(error instanceof JSONRPCException)) {
            return null;
        }
        try {
            JSONArray jsError = new JSONArray(error.getMessage());
            String type = jsError.getString(0);
            if (type.equals("UserWarning")) {
                JSONArray jsMsg = jsError.getJSONArray(1);
                String[] ret = new String[]{jsMsg.getString(0),
                                            jsMsg.getString(1)};
                return ret;
            }
        } catch (JSONException e) {
            // This is not the structure we expect
        }
        return null;
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
    public static int login(final String user, final String password,
                                final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
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
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    /** Check if the response of a call is "Not logged in".
     */
    private static boolean isNotLogged(JSONRPCException e) {
        return e.getMessage().equals("[\"NotLogged\"]");
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

    public static int getPreferences(final int userId, final String cookie,
                                         final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
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
                } catch (JSONRPCException e) {
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_PREFERENCES_NOK;
                        m.obj = e;
                    }
                } catch (Exception e) {
                    m.what = CALL_PREFERENCES_NOK;
                    m.obj = e;
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    /** Utility function to search and read models in one function call. */
    private static JSONArray search(int userId, String cookie,
                                    Preferences prefs, String model,
                                    List args, int offset, int count,
                                    boolean fullLoad)
        throws JSONRPCException, JSONException {
        if (c == null) {
            return null;
        }
        // First step: search ids
        JSONArray jsArgs = new JSONArray();
        if (args != null) {
            // TODO: convert the list of arguments to a JSONArray
            System.out.println("Giving parameters to search not supported yet");
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
            if (!fullLoad) {
                JSONArray fields = new JSONArray();
                fields.put("id");
                fields.put("rec_name");
                resp = c.call(model + ".read", userId, cookie, jsIds,
                              fields, prefs.json());
            } else {
                resp = c.call(model + ".read", userId, cookie, jsIds,
                              new JSONArray(), prefs.json());
            }
            if (resp instanceof JSONArray) {
                // We've got them!
                return (JSONArray) resp;
            }
        }
        return null;
    }

    /** Get data from ids */
    private static JSONArray read(int userId, String cookie,
                                  Preferences prefs, String model,
                                  String[] fields,
                                  List<Integer> ids)
        throws JSONRPCException, JSONException {
        if (c == null) {
            return null;
        }
        // Prepare ids
        JSONArray jsIds = new JSONArray();
        for (int id : ids) {
            jsIds.put(id);
        }
        // Read the models
        Object resp;
        if (fields == null) {
            resp = c.call(model + ".read", userId, cookie,
                          jsIds, new JSONArray(), prefs.json());
        } else {
            JSONArray jsFields = new JSONArray();
            for (String field : fields) {
                jsFields.put(field);
            }
            resp = c.call(model + ".read", userId, cookie,
                          jsIds, jsFields, prefs.json());
        }
        if (resp instanceof JSONArray) {
            // We've got them!
            return (JSONArray) resp;
        }
        return null;
    }

    /** Get the full menu as a list of top level entries with children. */
    public static int getMenus(final int userId, final String cookie,
                                   final Preferences prefs, final Handler h) {
        /* The procedure is broken into 5 parts:
         * Request the menus from the server
         * Request the icon ids and name from the server
         * Get the icons for the menu entries from the server
         * Build all menu entries individually
         * Build the tree */
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                try {
                    // Get the first "1000" menu entries
                    JSONArray jsMenus = search(userId, cookie, prefs,
                                               "model.ir.ui.menu",
                                               null,
                                               0, 1000, true);
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
                    if (isCanceled(callId)) { return; }
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
                    if (isCanceled(callId)) { return; }
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
                    if (isCanceled(callId)) { return; }
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
                    if (isCanceled(callId)) { return; }
                    // We've got our menu tree, sort it by sequence
                    MenuEntry.SequenceSorter.recursiveSort(topLevelMenu);
                    // Trick: as we can't "double click" to open the view
                    // of a parent entry we add a new child entry with the
                    // same action to open it.
                    for (int id : allMenus.keySet()) {
                        MenuEntry entry = allMenus.get(id);
                        if (entry.getChildren().size() != 0
                            && entry.getActionType() != null) {
                            MenuEntry child = new MenuEntry(entry.getId(),
                                                            entry.getLabel(),
                                                            entry.getSequence(),
                                                            entry.getActionType(),
                                                            entry.getActionId());
                            child.setIconSource(entry.getIconName(),
                                                entry.getIconSource());
                            entry.preAppendChild(child);
                        }
                    }
                    // All done, return it
                    m.what = CALL_MENUS_OK;
                    m.obj = topLevelMenu;
                } catch (JSONRPCException e) {
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_MENUS_NOK;
                        m.obj = e;
                    }
                } catch (Exception e) {
                    m.what = CALL_MENUS_NOK;
                    m.obj = e;
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    /** Get a single given view. The view isn't build. Id may be null to
     * get default view for the type. */
    private static ModelView getView(final int userId, final String cookie,
                                     final Preferences prefs,
                                     final String model,
                                     final Integer id, final String type)
        throws JSONRPCException {
        Object oView;
        if (id == null) {
            oView = c.call("model." + model + ".fields_view_get",
                           userId, cookie, false, type, prefs.json());
        } else {
            oView = c.call("model." + model + ".fields_view_get",
                                  userId, cookie, id, type, prefs.json());
        }
        if (oView instanceof JSONObject) {
            try {
                JSONObject jsFields = (JSONObject) oView;
                ModelView mView = new ModelView(jsFields);
                return mView;
            } catch (JSONException e) {}
        }
        return null;
    }

    /** Get the views and subviews for a meny entry.
     * It loads the top views and default subviews directly, build the
     * views with ArchParser which loads the missing subviews with
     * getView. */
    public static int getViews(final int userId, final String cookie,
                               final Preferences prefs,
                               final MenuEntry entry,
                               final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
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
                            if (isCanceled(callId)) { return; }
                            JSONArray jsTypeId = jsViewTypes.getJSONArray(i);
                            int viewId = jsTypeId.getInt(0);
                            String type = jsTypeId.getString(1);
                            // Get the view itself
                            ModelView mView = getView(userId, cookie, prefs,
                                                      model, viewId, type);
                            if (mView != null) {
                                // Build the view
                                ArchParser parser = new ArchParser(mView);
                                parser.buildTree();
                                // Get the views that where found during parsing
                                for (ArchParser.MissingView v : parser.getDiscovered()) {
                                    ModelView sub = getView(userId, cookie,
                                                            prefs,
                                                            v.getClassName(),
                                                            v.getId(),
                                                            v.getType());
                                    if (sub != null) {
                                        ModelViewTypes t = mView.getSubview(v.getFieldName());
                                        if (t == null) {
                                            t = new ModelViewTypes(v.getClassName());
                                            mView.getSubviews().put(v.getFieldName(), t);
                                        }
                                        t.putView(v.getType(), sub);
                                    } else {
                                        // TODO: wtf?
                                    }
                                }
                                if (isCanceled(callId)) { return; }
                                // Build all subviews
                                for (String extView : mView.getSubviews().keySet()) {
                                    ModelViewTypes t = mView.getSubviews().get(extView);
                                    for (String vt : t.getTypes()) {
                                        ModelView sub = t.getView(vt);
                                        ArchParser p = new ArchParser(sub);
                                        p.buildTree();
                                    }
                                }
                                // Register the views
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
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_VIEWS_NOK;
                        m.obj = e;
                    }
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    public static int getRelFields(final int userId, final String cookie,
                                   final Preferences prefs,
                                   final String modelName,
                                   final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                List<RelField> relFields = new ArrayList<RelField>();
                // Get field types
                try {
                    Object oFieldsRes = c.call("model." + modelName
                                               + ".fields_get", userId,
                                               cookie, JSONObject.NULL,
                                               prefs.json());
                    if (oFieldsRes instanceof JSONObject) {
                        JSONObject fieldsRes = (JSONObject) oFieldsRes;
                        JSONArray fieldNames = fieldsRes.names();
                        for (int i = 0; i < fieldNames.length(); i++) {
                            String fieldName = fieldNames.getString(i);
                            JSONObject jsData = fieldsRes.getJSONObject(fieldName);
                            String type = jsData.getString("type");
                            if (type.equals("many2many")
                                || type.equals("one2many")
                                || type.equals("many2one")
                                || type.equals("one2one")) {
                                String relModel = jsData.getString("relation");
                                RelField rel = new RelField(fieldName, type,
                                                            relModel);
                                relFields.add(rel);
                            }
                        }
                    }
                    m.what = CALL_RELFIELDS_OK;
                    m.obj = relFields;
                } catch (Exception e) {
                    m.what = CALL_RELFIELDS_NOK;
                    m.obj = e;
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }
    
    private static void getRelationnals(int userId, String cookie,
                                        Preferences prefs,
                                        List<Model> models, RelField relField) {
        if (models.size() == 0) {
            return;
        }
        String fieldName = relField.getFieldName();
        String relModelName = relField.getRelModel();
        String type = relField.getType();
        Map <Integer, List<Model>> whoWants = new HashMap<Integer,
            List<Model>>();
        List<Integer> ids = new ArrayList<Integer>();
        if (type.equals("many2one") || type.equals("one2one")) {
            for (Model m : models) {
                // Get the id of the relationnal data
                Object value = m.get(fieldName);
                if (value == null || value instanceof Model) {
                    // This one is already loaded or not defined
                    continue;
                }
                int id = (Integer) value;
                ids.add(id);
                // Register the model as requesting this id
                if (whoWants.get(id) == null) {
                    whoWants.put(id, new ArrayList<Model>());
                }
                whoWants.get(id).add(m);
            }
        } else if (type.equals("one2many") || type.equals("many2many")) {
            for (Model m : models) {
                // Get the ids of the relationnal data
                Object value = m.get(fieldName);
                if (value == null || value instanceof List) {
                    // This one is already loaded or not defined
                    continue;
                }
                JSONArray jsIds = (JSONArray) value;
                for (int i = 0; i < jsIds.length(); i++) {
                    try {
                        int id = jsIds.getInt(i);
                        ids.add(id);
                        // Register the model as requesting this id
                        if (whoWants.get(id) == null) {
                            whoWants.put(id, new ArrayList<Model>());
                        }
                        whoWants.get(id).add(m);
                    } catch (JSONException e) {
                        // TODO: json exception
                        e.printStackTrace();
                    }
                }
            }
        }
        // Get all relationnal data
        try {
            JSONArray rels = read(userId, cookie, prefs,
                                  "model." + relModelName,
                                  new String[]{"id", "rec_name"},
                                  ids);
            for (int i = 0; i < rels.length(); i++) {
                try {
                    JSONObject oModel = rels.getJSONObject(i);
                    Model model = new Model(relModelName, oModel);
                    int id = (Integer) model.get("id");
                    // Who wants this one? Take it!
                    for (Model parent : whoWants.get(id)) {
                        if (type.equals("many2one") || type.equals("one2one")) {
                            parent.set2One(fieldName, model);
                        } else {
                            parent.add2Many(fieldName, model);
                        }
                    }
                } catch (JSONException e) {
                    // TODO json error
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            // TODO rpc or json error
            e.printStackTrace();
        }
    }

    public static int getDataCount(final int userId, final String cookie,
                                   final Preferences prefs,
                                   final String modelName,
                                   final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                try {
                    Object resp = c.call("model." + modelName + ".search",
                                         userId, cookie, new JSONArray(),
                                         0, 9999999, JSONObject.NULL,
                                         prefs.json());
                    if (resp instanceof JSONArray) {
                        int count = ((JSONArray)resp).length();
                        m.what = CALL_DATACOUNT_OK;
                        m.obj = count;
                    }
                } catch (JSONRPCException e) {
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_DATACOUNT_NOK;
                        m.obj = e;
                    }
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    /** Get some data for a model */
    public static int getData(final int userId, final String cookie,
                              final Preferences prefs,
                              final String modelName,
                              final int offset, final int count,
                              final List<RelField> relFields,
                              final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                // Fields list by name
                Map<String, Model> fields = new HashMap<String, Model>();
                // Data list
                List<Model> allData = new ArrayList<Model>();
                try {
                    // Search the data and add them to a list
                    JSONArray result = search(userId, cookie, prefs,
                                              "model." + modelName,
                                              null, offset, count, true);
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject jsData = result.getJSONObject(i);
                        Model data = new Model(modelName, jsData);
                        allData.add(data);
                    }
                    if (isCanceled(callId)) { return; }
                    // Check for relational fields and load them
                    for (RelField rel : relFields) {
                        getRelationnals(userId, cookie, prefs, allData,
                                        rel);
                    }
                    // Send back the list to the handler
                    m.what = CALL_DATA_OK;
                    m.obj = allData;
                } catch (JSONRPCException e) {
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_DATA_NOK;
                        m.obj = e;
                    }
                } catch (Exception e) {
                    m.what = CALL_DATA_NOK;
                    m.obj = e;
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    /** Get data for relationnal pickup */
    public static int getRelData(final int userId, final String cookie,
                                     final Preferences prefs,
                                     final String modelName,
                                     final boolean fullLoad,
                                     final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                // Fields list by name
                Map<String, Model> fields = new HashMap<String, Model>();
                // Data list
                List<Model> allData = new ArrayList<Model>();
                try {
                    // Search the data and add them to a list
                    JSONArray result = search(userId, cookie, prefs,
                                              "model." + modelName,
                                              null, 0, 999999, fullLoad);
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject jsData = result.getJSONObject(i);
                        Model data = new Model(modelName, jsData);
                        allData.add(data);
                    }
                    // Send back the list to the handler
                    m.what = CALL_RELDATA_OK;
                    m.obj = new Object[]{modelName, allData};
                    if (fullLoad) {
                        m.arg1 = 1;
                    } else {
                        m.arg1 = 0;
                    }
                } catch (JSONRPCException e) {
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_RELDATA_NOK;
                        m.obj = e;
                    }
                } catch (Exception e) {
                    m.what = CALL_RELDATA_NOK;
                    m.obj = e;
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    /** Create or update a record. If record has no id it's a creation.
     * Handler gives back the updated/created record. */
    public static int saveData(final int userId, final String cookie,
                               final Preferences prefs,
                               final Model model, final Model oldModel,
                               final Context ctx,
                               final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                Model sendModel = FieldsConvertion.modelToSend(model, oldModel,
                                                               ctx);
                // Set attribute
                String modelName = sendModel.getClassName();
                JSONObject attrs = new JSONObject();
                for (String attr : sendModel.getAttributeNames()) {
                    try {
                        if (sendModel.get(attr) == null) {
                            attrs.put(attr, JSONObject.NULL);
                        } else {
                            attrs.put(attr, sendModel.get(attr));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                // Set action (create or write)
                boolean create = (sendModel.get("id") == null);
                String action = "model." + modelName + ".";
                if (create) {
                    action += "create";
                } else {
                    action += "write";
                }
                // Go!
                try {
                    Object oResult;
                    if (create) {
                        oResult = c.call(action, userId, cookie, attrs,
                                         prefs.json());
                    } else {
                        JSONArray id = new JSONArray();
                        id.put(model.get("id"));
                        oResult = c.call(action, userId, cookie,
                                         id, attrs, prefs.json());
                    }
                    if (create && oResult instanceof Integer) {
                        // Create done, get new record
                        int id = (Integer) oResult;
                        List<Integer> lid = new ArrayList<Integer>();
                        lid.add(id);
                        Object oModel = read(userId, cookie, prefs,
                                             "model." + modelName,
                                             null, lid);
                        if (oModel instanceof JSONArray) {
                            JSONArray jsModels = (JSONArray) oModel;
                            JSONObject jsModel = jsModels.getJSONObject(0);
                            Model updmodel = new Model(modelName, jsModel);
                            m.what = CALL_SAVE_OK;
                            m.obj = updmodel;
                        } else {
                            m.what = CALL_SAVE_OK;
                            m.obj = null;
                        }
                    } else if (!create &&
                               (oResult == JSONObject.NULL
                                || oResult instanceof Boolean)) {
                        // Update done, get updated record
                        List<Integer> lid = new ArrayList<Integer>();
                        lid.add((Integer)sendModel.get("id"));
                        Object oModel = read(userId, cookie, prefs,
                                             "model." + modelName,
                                             null, lid);
                        if (oModel instanceof JSONArray) {
                            JSONArray jsModels = (JSONArray) oModel;
                            JSONObject jsModel = jsModels.getJSONObject(0);
                            Model updmodel = new Model(modelName, jsModel);
                            m.what = CALL_SAVE_OK;
                            m.obj = updmodel;
                        } else {
                            m.what = CALL_SAVE_OK;
                            m.obj = null;
                        }
                    } else {
                        m.what = CALL_SAVE_NOK;
                        m.obj = new Exception("Unknown response type "
                                              + oResult);
                    }
                } catch (JSONRPCException e) {
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_SAVE_NOK;
                        m.obj = e;
                    }
                } catch (Exception e) {
                    m.what = CALL_SAVE_NOK;
                    m.obj = e;
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }

    public static int deleteData(final int userId, final String cookie,
                                 final Preferences prefs,
                                 final int id, final String className,
                                 final Handler h) {
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(id);
        return deleteData(userId, cookie, prefs, ids, className, h);
    }

    public static int deleteData(final int userId, final String cookie,
                                 final Preferences prefs,
                                 final List<Integer> ids,
                                 final String className,
                                 final Handler h) {
        if (c == null) {
            return -1;
        }
        final int callId = callSequence++;
        handlers.put(callId, h);
        new Thread() {
            public void run() {
                Message m = h.obtainMessage();
                try {
                    JSONArray jsIds = new JSONArray();
                    for (Integer id : ids) {
                        jsIds.put(id);
                    }
                    Object oResult = c.call("model." + className + ".delete",
                                            userId, cookie, jsIds, prefs.json());
                    if (oResult == JSONObject.NULL
                        || oResult instanceof Boolean) {
                        // Delete done
                        m.what = CALL_DELETE_OK;
                    } else {
                        m.what = CALL_DELETE_NOK;
                        m.obj = new Exception("Unknown response type "
                                              + oResult);
                    }
                } catch (JSONRPCException e) {
                    if (isNotLogged(e)) {
                        m.what = NOT_LOGGED;
                    } else {
                        m.what = CALL_DELETE_NOK;
                        m.obj = e;
                    }
                } catch (Exception e) {
                    m.what = CALL_DELETE_NOK;
                    m.obj = e;
                }
                sendMessage(callId, m);
            }
        }.start();
        return callId;
    }
}
