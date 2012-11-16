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
package org.tryton.client.data;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tryton.client.data.MenuCache;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.tools.TrytonCall;

/** Utility class that checks for data in local cache and request the
 * server if required. */
public class DataLoader {

    public static final int MENUS_OK = 1000;
    public static final int MENUS_NOK = 1001;

    private static int callSequence = 1;
    private static Map<Integer, Handler> handlers = new HashMap<Integer, Handler>();
    private static Map<Integer, Integer> localCalls = new HashMap<Integer, Integer>();
    private static Map<Integer, Integer> trytonCalls = new HashMap<Integer, Integer>();

    private static void cancel(int callId) {
        if (localCalls.containsKey(callId)) {

        }
        if (trytonCalls.containsKey(callId)) {
            TrytonCall.cancel(trytonCalls.get(callId));
        }
        handlers.remove(callId);
        localCalls.remove(callId);
        trytonCalls.remove(callId);
    }
    /** Check if a call has been canceled or not. */
    private static boolean isCanceled(int callId) {
        return (!handlers.containsKey(callId));
    }
    public static void update(int callId, Handler h) {
        if (handlers.containsKey(callId)) {
            handlers.put(callId, h);
        }
    }
    /** Send the message to the right handler if it was updated or not
     * if it was canceled. */
    @SuppressWarnings("unchecked")
    private static void forwardMessage(int callId, Message m, Context ctx) {
        int what = m.what;
        // Translate local or tryton response code to data loader code
        switch (m.what) {
        case TrytonCall.CALL_MENUS_OK:
            // Cache the menu
            List<MenuEntry> menus = (List<MenuEntry>) m.obj;
            try {
                MenuCache.save(menus, ctx);
            } catch (IOException e) {
                Log.w("Tryton", "Unable to cache menus", e);
            }
            what = MENUS_OK;
            break;
        case TrytonCall.CALL_MENUS_NOK:
            what = MENUS_NOK;
        }
        // Forward the message
        if (handlers.containsKey(callId)) {
            Handler h = handlers.get(callId);
            Message fwd = h.obtainMessage();
            fwd.what = what;
            fwd.obj = m.obj;
            fwd.arg1 = m.arg1;
            fwd.arg2 = m.arg2;
            fwd.sendToTarget();
        }
        // Remove ids from pending ones
        handlers.remove(callId);
        localCalls.remove(callId);
        trytonCalls.remove(callId);
    }

    private static class ForwardHandler extends Handler {
        private int callId;
        private Context ctx;
        public ForwardHandler(int callId, Looper loop, Context ctx) {
            super(loop);
            this.callId = callId;
            this.ctx = ctx;
        }
        @Override
        public void handleMessage(Message m) {
            forwardMessage(this.callId, m, this.ctx);
        }
    }
    private static HandlerThread dataLoaderLoop = new HandlerThread("dataloader");
    private static Handler newHandler(int callId, Context ctx) {
        try {
            dataLoaderLoop.start();
        } catch (IllegalThreadStateException e) { /* Already started */ }
        return new ForwardHandler(callId, dataLoaderLoop.getLooper(), ctx);
    }

    public static int loadMenu(final Context ctx, final Handler h,
                               final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                List<MenuEntry> menus = null;
                try {
                    menus = MenuCache.load(ctx);
                } catch (IOException e) {
                    if (!(e instanceof FileNotFoundException)) {
                        // Ignore no cache exception
                        Log.w("Tryton", "Unable to load menu cache", e);
                    }
                }
                if (menus != null) {
                    // Got it
                    Message m = fwdHandler.obtainMessage();
                    m.what = MENUS_OK;
                    m.obj = menus;
                    m.sendToTarget();
                } else {
                    // Load from server
                    Session s = Session.current;
                    int tcId = TrytonCall.getMenus(s.userId, s.cookie, s.prefs,
                                                   fwdHandler);
                    trytonCalls.put(callId, tcId);
                }
            }
        }.start();
        return callId;
    }
}

