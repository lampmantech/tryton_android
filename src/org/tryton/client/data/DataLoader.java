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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tryton.client.data.MenuCache;
import org.tryton.client.models.Model;
import org.tryton.client.models.MenuEntry;
import org.tryton.client.models.ModelView;
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
    public static final int MENUDATA_OK = 1010;
    private static final int MODELDATA_OK = 1011;

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
    private static int processMessage(Message m, Context ctx) {
        int what = m.what;
        // Translate local or tryton response code to data loader code
        switch (m.what) {
        case TrytonCall.CALL_MENUS_OK:
            // Cache the menu
            @SuppressWarnings("unchecked")
            List<MenuEntry> menus = (List<MenuEntry>) m.obj;
            try {
                MenuCache.save(menus, ctx);
            } catch (IOException e) {
                Log.w("Tryton", "Unable to cache menus", e);
            }
            what = MENUS_OK;
            break;
        case TrytonCall.CALL_VIEW_OK:
            ModelView view = (ModelView)((Object[])m.obj)[1];
            DataCache db = new DataCache(ctx);
            db.storeView(view);
            what = VIEWS_OK;
            break;
        case TrytonCall.CALL_VIEWS_OK:
            @SuppressWarnings("unchecked")
            Object[] ret = (Object[]) m.obj;
            MenuEntry origin = (MenuEntry) ret[0];
            ModelViewTypes viewTypes = (ModelViewTypes) ret[1];
            db = new DataCache(ctx);
            db.storeViewTypes(origin, viewTypes);
            what = VIEWS_OK;
            break;
        case TrytonCall.CALL_DATACOUNT_OK:
            ret = (Object[]) m.obj;
            String className = (String) ret[0];
            int count = (Integer) ret[1];
            db = new DataCache(ctx);
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
        case TrytonCall.CALL_VIEWS_NOK:
        case TrytonCall.CALL_VIEW_NOK:
            what = VIEWS_NOK;
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
        return what;
    }
    /** Send the message to the right handler if it was updated or not
     * if it was canceled. Also save data in local cache when transmitting
     * server data. */
    @SuppressWarnings("unchecked")
    private static void forwardMessage(int callId, Message m, Context ctx) {
        int what = processMessage(m, ctx);
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
                DataCache db = new DataCache(ctx);
                views = db.loadViews(origin.getId());
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

    public static int loadView(final Context ctx, final String className,
                               final int viewId, final String type,
                               final Handler h, final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                // Check if the view is available from cache
                ModelView view = null;
                DataCache db = new DataCache(ctx);
                if (viewId != 0) {
                    view = db.loadView(viewId);
                } else {
                    view = db.loadDefaultView(className, type);
                }
                if (view != null) {
                    Message m = fwdHandler.obtainMessage();
                    m.what = VIEWS_OK;
                    m.obj = new Object[]{type, view};
                    m.sendToTarget();
                    return;
                } else {
                    Session s = Session.current;
                    int tcId = TrytonCall.getView(s.userId, s.cookie,
                                                  s.prefs, className, viewId,
                                                  type, fwdHandler);
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
                               final ModelView view,
                               final Handler h, final boolean forceRefresh) {
        ModelViewTypes dummy = new ModelViewTypes(view.getModelName());
        return loadData(ctx, className, offset, count, expectedCount, relFields,
                       dummy, h, forceRefresh);

    }

    public static int loadData(final Context ctx, final String className,
                               final int offset, final int count,
                               final int expectedCount,
                               final List<RelField> relFields,
                               final ModelViewTypes views,
                               final Handler h, final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                if (!forceRefresh) {
                    // Load from cache
                    DataCache db = new DataCache(ctx);
                    List<Model> data = db.getData(className, offset, count, views);
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
                                              relFields, views, fwdHandler);
                trytonCalls.put(callId, tcId);
            }
        }.start();
        return callId;
    }

    public static int loadData(final Context ctx, final String className,
                               final List<Integer> ids,
                               final List<RelField> relFields,
                               final ModelView view,
                               final Handler h, final boolean forceRefresh) {
        ModelViewTypes dummy = new ModelViewTypes(view.getModelName());
        dummy.putView("dummy", view);
        return loadData(ctx, className, ids, relFields, dummy, h, forceRefresh);
    }


    public static int loadData(final Context ctx, final String className,
                               final List<Integer> ids,
                               final List<RelField> relFields,
                               final ModelViewTypes views,
                               final Handler h, final boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        final Handler fwdHandler = newHandler(callId, ctx);
        new Thread() {
            public void run() {
                if (!forceRefresh) {
                    // Load from cache
                    DataCache db = new DataCache(ctx);
                    List<Model> data = db.getData(className, ids, views);
                    if (data.size() == ids.size()) {
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
                                              className, ids, relFields,
                                              views, fwdHandler);
                trytonCalls.put(callId, tcId);
            }
        }.start();
        return callId;
    }

    ///////////////////////////
    // Precaching operations //
    ///////////////////////////

    /** List of view id already loaded. */
    private static Set<String> loadedViews;

    private static void addLoadedView(String className, String type,
                                      int viewId) {
        loadedViews.add(className + "_" + type + "_" + viewId);
    }
    private static boolean isLoaded(String className, String type, int viewId) {
        return loadedViews.contains(className + "_" + type + "_" + viewId);
    }

    /** First level handler that waits for entry to be loaded and send back
     * the result message. */
    private static class EntryHandler extends Handler {
        private int callId;
        private Context ctx;
        private MenuEntry menu;
        private boolean forceRefresh;
        public EntryHandler(int callId, Looper loop, Context ctx,
                            MenuEntry menu, boolean forceRefresh) {
            super(loop);
            this.callId = callId;
            this.ctx = ctx;
            this.menu = menu;
            this.forceRefresh = forceRefresh;
        }
        public int start() {
            final int callId = callSequence++;
            handlers.put(callId, this);
            new ModelLoader(callId, this, this.ctx, this.menu,
                            this.forceRefresh).load();
            return callId;
        }
        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
            case MODELDATA_OK:
                Message msg = this.obtainMessage();
                msg.what = MENUDATA_OK;
                forwardMessage(this.callId, msg, this.ctx);
                break;
            }
        }
    }

    /** Second and below level handler that receives loading messages and 
     * send back to upper level when all loading steps are done.
     * It is recursive on relationnal fields. */
    private static class ModelLoader extends Handler {
        /** CallId of EntryHandler */
        private int superCallId;
        private int callId;
        private Context ctx;
        private MenuEntry entry; // For top level only
        private boolean forceRefresh;
        private String className;
        private ModelViewTypes viewTypes;
        private ModelViewTypes loadedViewTypes;
        private List<String> pendingViewTypes; // For sublevels only
        private List<String> fields;
        private int count;
        private int offset;
        private List<RelField> relFields;
        private Handler parent;
        private int subloadIndex;

        /** Constructor for top level load */
        public ModelLoader(int superCallId, Handler parent, Context ctx,
                           MenuEntry entry, boolean forceRefresh) {
            super(parent.getLooper());
            this.superCallId = superCallId;
            this.ctx = ctx;
            this.entry = entry;
            this.forceRefresh = forceRefresh;
            this.parent = parent;
        }
        /** Constructor for below levels. It uses viewTypes dumbly, make sure
         * viewTypes contains the types to load. I.e if tree is not defined,
         * it won't be loaded at all. */
        public ModelLoader(int superCallId, Handler parent, Context ctx,
                           String className, ModelViewTypes viewTypes,
                           boolean forceRefresh) {
            super(parent.getLooper());
            this.superCallId = superCallId;
            this.ctx = ctx;
            this.className = className;
            this.viewTypes = viewTypes;
            this.loadedViewTypes = viewTypes.copy();
            this.forceRefresh = forceRefresh;
            this.parent = parent;
            this.pendingViewTypes = new ArrayList<String>();
            for (String type : this.viewTypes.getTypes()) {
                if (this.viewTypes.getView(type) == null) {
                    this.pendingViewTypes.add(type);
                }
            }
        }

        public void load() {
            if (this.entry != null) {
                this.callId = loadViews(this.ctx, this.entry, this,
                                        this.forceRefresh);
            } else {
                // Check if all is not already loaded
                for (String type : this.viewTypes.getTypes()) {
                    int id = this.viewTypes.getViewId(type);
                    if (isLoaded(this.className, type, id)) {
                        // The view is already loaded
                        this.pendingViewTypes.remove(type);
                        DataCache db = new DataCache(this.ctx);
                        if (id == 0) {
                            ModelView view = db.loadDefaultView(className, type);
                            this.loadedViewTypes.putView(type, view);
                        } else {
                            ModelView view = db.loadView(id);
                            this.loadedViewTypes.putView(type, view);
                        }
                    }
                }
                if (this.pendingViewTypes.size() == 0) {
                    // Continue
                    Message msg = this.parent.obtainMessage();
                    msg.what = MODELDATA_OK;
                    msg.sendToTarget();
                } else {
                    // Load views
                    String type = this.pendingViewTypes.get(0);
                    int id = this.viewTypes.getViewId(type);
                    this.pendingViewTypes.remove(0);
                    this.callId = loadView(this.ctx, this.className, id, type,
                                           this, this.forceRefresh);
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleMessage(Message m) {
            switch (m.what) {
            case VIEWS_OK:
                if (this.pendingViewTypes == null) {
                    // Menu views ok
                    this.loadedViewTypes = (ModelViewTypes)((Object[])m.obj)[1];
                    this.className = this.loadedViewTypes.getModelName();
                } else {
                    // View loaded, check if other are pending
                    ModelView loadedView = (ModelView)((Object[])m.obj)[1];
                    String loadedType = (String)((Object[])m.obj)[0];
                    this.loadedViewTypes.putView(loadedType, loadedView);
                    if (this.pendingViewTypes.size() > 0) {
                        String type = this.pendingViewTypes.get(0);
                        int id = this.viewTypes.getViewId(type);
                        this.pendingViewTypes.remove(0);
                        this.callId = loadView(this.ctx, this.className, id,
                                               type, this, this.forceRefresh);
                        // Wait until this one is loaded
                        return;
                    }
                }
                // All done, load data
                this.callId = loadDataCount(this.ctx, this.className, this,
                                            this.forceRefresh);
                break;
            case DATACOUNT_OK:
                this.count = (Integer) ((Object[])m.obj)[1];
                loadRelFields(this.ctx, this.className, this,
                              this.forceRefresh);
                break;
            case RELFIELDS_OK:
                this.relFields = (List<RelField>) ((Object[])m.obj)[1];
                int expected = Math.min(TrytonCall.CHUNK_SIZE,
                                        this.count);
                loadData(this.ctx, this.className, 0, TrytonCall.CHUNK_SIZE,
                         expected, this.relFields, this.loadedViewTypes, this,
                         this.forceRefresh);
                break;
            case DATA_OK:
                this.offset += TrytonCall.CHUNK_SIZE;
                expected = Math.min(TrytonCall.CHUNK_SIZE,
                                    this.count - this.offset);
                if (this.offset < this.count) {
                    loadData(this.ctx, this.className, this.offset,
                             TrytonCall.CHUNK_SIZE, expected,
                             this.relFields, this.loadedViewTypes, this,
                             this.forceRefresh);
                } else {
                    for (String type : this.loadedViewTypes.getTypes()) {
                        // Mark view as loaded (id and 0 if it was loaded)
                        int id = this.loadedViewTypes.getViewId(type);
                        addLoadedView(this.className, type, id);
                        if (this.viewTypes != null) {
                            int oldId = this.viewTypes.getViewId(type);
                            addLoadedView(this.className, type, oldId);
                        }
                    }
                    loadRec();
                }
                break;
            case MODELDATA_OK:
                loadRec();
                break;
            }
        }
        private void loadRec() {
            if (this.subloadIndex < this.relFields.size()) {
                RelField rel = this.relFields.get(this.subloadIndex);
                String fieldName = rel.getFieldName();
                String type = rel.getType();
                String subclassName = rel.getRelModel();
                // Get required fields from views
                List<String> fields = null;;
                if (this.loadedViewTypes != null) {
                    fields = this.loadedViewTypes.getAllFieldNames();
                } else {
                    fields = new ArrayList<String>();
                }
                if (!fields.contains("id")) { fields.add("id"); }
                if (!fields.contains("rec_name")) { fields.add("rec_name"); }
                // Load the rel field only if it is used in the views
                if (fields.contains(fieldName)) {
                    ModelViewTypes subviewTypes = null;
                    // Subviews are used only from form views, pick it
                    if (this.loadedViewTypes != null) {
                        ModelView form = this.loadedViewTypes.getView("form");
                        if (form != null) {
                            subviewTypes = form.getSubview(fieldName);
                        }
                    }
                    // Ensure that the subviews has always tree and form
                    if (subviewTypes == null) {
                        // No view set, use tree and form
                        subviewTypes = new ModelViewTypes(subclassName);
                        subviewTypes.putViewId("tree", 0);
                        subviewTypes.putViewId("form", 0);
                    } else {
                        // 0 can means the type is not there, force it
                        if (subviewTypes.getViewId("tree") == 0) {
                            subviewTypes.putViewId("tree", 0);
                        }
                        if (subviewTypes.getViewId("form") == 0) {
                            subviewTypes.putViewId("form", 0);
                        }
                    }
                    // Prepare for next and wait model loader
                    this.subloadIndex++;
                    // Load next subview and submodels
                    new ModelLoader(this.superCallId, this, this.ctx,
                                    subclassName, subviewTypes,
                                    this.forceRefresh).load();
                } else {
                    // Continue with next
                    this.subloadIndex++;
                    this.loadRec();
                }
            } else {
                // Finished, notify parent
                Message msg = this.parent.obtainMessage();
                msg.what = MODELDATA_OK;
                msg.sendToTarget();
            }
        }
    }

    private static EntryHandler newEntryHandler(int callId, Context ctx,
                                                MenuEntry menu,
                                                boolean forceRefresh) {
        try {
            dataLoaderLoop.start();
        } catch (IllegalThreadStateException e) { /* Already started */ }
        return new EntryHandler(callId, dataLoaderLoop.getLooper(), ctx,
                                menu, forceRefresh);
    }

    public static void initEntriesLoading() {
        loadedViews = new HashSet<String>();
    }

    public static int loadFullEntry(final Context ctx, final MenuEntry entry,
                                    final Handler h, boolean forceRefresh) {
        final int callId = callSequence++;
        handlers.put(callId, h);
        String action = entry.getActionType();
        if (action != null &&
            (action.equals("ir.action.wizard")
             || action.equals("ir.action.report")
             || action.equals("ir.action.url"))) {
            System.out.println("Type " + action + " not supported yet");
            return -1;
        } else {
            final EntryHandler entryHandler = newEntryHandler(callId, ctx, entry,
                                                              forceRefresh);
            entryHandler.start();
            return callId;
        }
    }

}