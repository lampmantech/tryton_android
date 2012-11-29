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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tryton.client.R;
import org.tryton.client.models.Model;

/** Hold requests to the server to send the queue later */
public class DelayedRequester {

    private static final int NOTIFY_ID = 1337;
    private static final int CMD_CREATE = 0;
    private static final int CMD_UPDATE = 1;
    private static final int CMD_DELETE = 2;

    private class Command {
        private int cmd;
        private Model data;

        public Command(int cmd, Model data) {
            this.cmd = cmd;
            this.data = data;
        }
        public int getCmd() { return this.cmd; }
        public Model getData() { return this.data; }
    }

    public static DelayedRequester current = new DelayedRequester();

    private List<Command> queue;
    /** Map temporary ids to models */
    private Map<Integer, Model> createdIds;
    /** Temporary negative id for new models waiting for read id
     * from the server. The temporary id is saved in local database
     * and updated when the definitive one is received. The temp id can
     * appear only in updated models in the queue. */
    private int tempId;

    public DelayedRequester() {
        this.queue = new ArrayList<Command>();
        this.createdIds = new TreeMap<Integer, Model>();
        this.tempId = -1;
    }

    /** Add a create call in the queue. It edits the id of the
     * model to affect it a temporary negative one. */
    public void queueCreate(Model newModel, Context ctx) {
        newModel.set("id", tempId);
        tempId--;
        this.queue.add(new Command(CMD_CREATE, newModel));
        this.updateNotification(ctx);
    }

    public void queueUpdate(Model updatedModel, Context ctx) {
        this.queue.add(new Command(CMD_UPDATE, updatedModel));
        this.updateNotification(ctx);
    }

    public void queueDelete(Model deletedModel, Context ctx) {
        this.queue.add(new Command(CMD_DELETE, deletedModel));
        this.updateNotification(ctx);
    }

    /** Get the number of pending requests. */
    public int getQueueSize() {
        return this.queue.size();
    }

    public void sendQueue() {
        
    }

    /** Update the notfication in the status bar with the number of
     * pending requests. */
    private void updateNotification(Context ctx) {
        Notification n = new Notification();
        String tickerText = null;
        if (this.queue.size() == 1) {
            tickerText = ctx.getString(R.string.requester_pending_one);
        } else {
            tickerText = String.format(ctx.getString(R.string.requester_pending),
                                       this.queue.size());
        }
        String message = ctx.getString(R.string.requester_message);
        Intent i = new Intent(ctx, org.tryton.client.Start.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);
        n.icon = R.drawable.tryton_notification;
        n.setLatestEventInfo(ctx, tickerText, message, pi);
        NotificationManager m = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        m.notify(NOTIFY_ID, n);
        System.out.println("notified");
    }
}