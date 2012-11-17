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
import org.tryton.client.models.Model;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.models.RelField;
import org.tryton.client.tools.TrytonCall;

/** Utility class that checks for data in local cache and request the
 * server if required. */
public class DataLoader {

    public static final int MENUS_OK = 1000;
    public static final int MENUS_NOK = 1001;
    public static final int VIEWS_OK = 1002;
    public static final int VIEWS_NOK = 1003;
    public static final int DATACOUNT_OK = 1004;
    public static final int DATACOUNT_NOK = 1005;
    public static final int RELFIELDS_OK = 1006;
    public static final int RELFIELDS_NOK = 1007;
    public static final int DATA_OK = 1008;
    public static final int DATA_NOK = 1009;

    private static int callSequence = 1;
    private static Map<Integer, Handler> handlers = new HashMap<Integer, Handler>();
    private static Map<Integer, Integer> localCalls = new HashMap<Integer, Integer>();
    private static Map<Integer, Integer> trytonCalls = new HashMap<Integer, Integer>();

    public static void cancel(int callId) {
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
     * if it was canceled. Also save data in local cache when transmitting
     * server data. */
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
        case TrytonCall.CALL_VIEWS_OK:
            @SuppressWarnings("unchecked")
            Object[] ret = (Object[]) m.obj;
            MenuEntry origin = (MenuEntry) ret[0];
            ModelViewTypes viewTypes = (ModelViewTypes) ret[1];
            try {
                ViewCache.save(origin, viewTypes, ctx);
            } catch (IOException e) {
                Log.w("Tryton",
                      "Unable to cache view data for " + origin, e);
            }
            what = VIEWS_OK;
            break;
        case TrytonCall.CALL_DATACOUNT_OK:
            ret = (Object[]) m.obj;
            String className = (String) ret[0];
            int count = (Integer) ret[1];
            DataCache db = new DataCache(ctx);
            db.setDataCount(className, count);
            what = DATACOUNT_OK;
            break;
        case TrytonCall.CALL_RELFIELDS_OK:
            ret = (Object[]) m.obj;
            className = (String) ret[0];
            @SuppressWarnings("unchecked")
            List<RelField> rel = (List<RelField>)ret[1];
            db = new DataCache(ctx);
            db.storeRelFields(className, rel);
            what = RELFIELDS_OK;
            break;
        case TrytonCall.CALL_DATA_OK:
            ret = (Object[]) m.obj;
            className = (String) ret[0];
            @SuppressWarnings("unchecked")
            List<Model> data = (List<Model>)ret[1];
            db = new DataCache(ctx);
            db.storeData(className, data);
            what = DATA_OK;
            break;
        case TrytonCall.CALL_MENUS_NOK:
            what = MENUS_NOK;
            break;
        case TrytonCall.CALL_DATACOUNT_NOK:
            what = DATACOUNT_NOK;
            break;
        case TrytonCall.CALL_RELFIELDS_NOK:
            what = RELFIELDS_NOK;
            break;
        case TrytonCall.CALL_DATA_NOK:
            what = DATA_NOK;
            break;
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

    public static int loadViews(final Context ctx, final MenuEntry origin,
                                 final Handler h,
                                 final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                // Check if views are available from cache
                ModelViewTypes views = null;
                try {
                    views = ViewCache.load(origin, ctx);
                } catch (IOException e) {
                    if (!(e instanceof FileNotFoundException)) {
                        // Ignore no cache exception   
                        Log.i("Tryton",
                              "Unable to load view cache for " + origin, e);
                    }
                }
                if (views != null) {
                    Message m = fwdHandler.obtainMessage();
                    m.what = VIEWS_OK;
                    m.obj = new Object[] {origin, views};
                    m.sendToTarget();
                    return;
                } else {
                    Session s = Session.current;
                    int tcId = TrytonCall.getViews(s.userId, s.cookie,
                                                          s.prefs,
                                                          origin,
                                                          fwdHandler);
                    trytonCalls.put(callId, tcId);
                }
            }
        }.start();
        return callId;
    }

    public static int loadDataCount(final Context ctx, final String className,
                                    final Handler h,
                                    final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                if (!forceRefresh) {
                    // Load from cache
                    int count;
                    DataCache db = new DataCache(ctx);
                    count = db.getDataCount(className);
                    if (count != -1) {
                        Message m = fwdHandler.obtainMessage();
                        m.what = DATACOUNT_OK;
                        m.obj = new Object[]{className, count};
                        m.sendToTarget();
                        return;
                    }
                }
                // Not in cache, load from server
                Session s = Session.current;
                int tcId = TrytonCall.getDataCount(s.userId, s.cookie, s.prefs,
                                                   className, fwdHandler);
                trytonCalls.put(callId, tcId);
            }
        }.start();
        return callId;
    }

    public static int loadRelFields(final Context ctx, final String className,
                                    final Handler h,
                                    final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                if (!forceRefresh) {
                    // Load from cache
                    DataCache db = new DataCache(ctx);
                    List<RelField> relFields = db.getRelFields(className);
                    if (relFields != null) {
                        Message m = fwdHandler.obtainMessage();
                        m.what = RELFIELDS_OK;
                        m.obj = new Object[]{className, relFields};
                        m.sendToTarget();
                        return;
                    }
                }
                // Not in cache, load from server
                Session s = Session.current;
                int tcId = TrytonCall.getRelFields(s.userId, s.cookie, s.prefs,
                                                   className, fwdHandler);
                trytonCalls.put(callId, tcId);
            }
        }.start();
        return callId;
    }

    public static int loadData(final Context ctx, final String className,
                               final int offset, final int count,
                               final int expectedCount,
                               final List<RelField> relFields,
                               final Handler h, final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                if (!forceRefresh) {
                    // Load from cache
                    DataCache db = new DataCache(ctx);
                    List<Model> data = db.getData(className, offset, count);
                    if (data.size() == expectedCount) {
                        Message m = fwdHandler.obtainMessage();
                        m.what = DATA_OK;
                        m.obj = new Object[]{className, data};
                        m.sendToTarget();
                        return;
                    }
                }
                // Load from server
                Session s = Session.current;
                int tcId = TrytonCall.getData(s.userId, s.cookie, s.prefs,
                                              className, offset, count,
                                              relFields, fwdHandler);
                trytonCalls.put(callId, tcId);
            }
        }.start();
        return callId;
    }

}

