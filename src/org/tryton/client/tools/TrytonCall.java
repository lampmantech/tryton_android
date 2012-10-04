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
import org.json.JSONObject;

/** Tryton requester.
 * It makes asynchronous calls to the tryton server set by setHost.
 * Responses are given back to caller through handlers. */
public class TrytonCall {

    public static final int CALL_VERSION_OK = 0;

    private static String host;
    private static final JSONRPCParams.Versions version =
        JSONRPCParams.Versions.VERSION_2;
    private static int timeout = 20000;
    private static int soTimeout = 30000;

    public static void setHost(String host) {
        TrytonCall.host = host;
    }

    public static void serverVersion(Handler h) {
        JSONRPCClient c = JSONRPCClient.create(TrytonCall.host,
                                               TrytonCall.version);
        c.setConnectionTimeout(timeout);
        c.setSoTimeout(soTimeout);
        try {
            String version = c.callString("common.server.version",
                                          JSONObject.NULL, JSONObject.NULL);
            // Send back the response to the handler
            Message m = h.obtainMessage();
            m.what = CALL_VERSION_OK;
            m.obj = version;
            m.sendToTarget();
        } catch (JSONRPCException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}