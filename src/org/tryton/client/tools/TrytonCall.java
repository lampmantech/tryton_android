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
import org.alexd.jsonrpc.JSONRPCClient;
import org.alexd.jsonrpc.JSONRPCException;
import org.alexd.jsonrpc.JSONRPCParams;
import org.json.JSONArray;
import org.json.JSONObject;

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
        if (!host.startsWith("http://")) {
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

    public static boolean serverVersion(Handler h) {
        if (c == null) {
            return false;
        }
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
                m.obj = new Exception("Incorrect response type " + resp);
            }
        } catch (Exception e) {
            m.what = CALL_VERSION_NOK;
            m.obj = e;
        }
        m.sendToTarget();
        return true;
    }

    /** Call to login.
     * Message will contain success on arg1 and if success obj will be
     * a array with obj[0] as user id (integer) and obj[1] the cookie (string).
     */
    public static boolean login(String user, String password, Handler h) {
        if (c == null) {
            return false;
        }
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
        return true;
    }

    public static boolean getPreferences(int userId, String cookie, Handler h) {
        if (c == null) {
            return false;
        }
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
        return true;
    }
}