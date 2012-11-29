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
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ProgressBar;

import org.tryton.client.tools.DelayedRequester;

/** List pending requests and handles sending them all.
 * It reads data from DelayedRequester.current.  */
public class PendingRequests extends Activity {

    private TextView remaining;
    private ProgressBar progress;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        this.setContentView(R.layout.pending_requests);
        this.remaining = (TextView) this.findViewById(R.id.pending_remaining);
        this.progress = (ProgressBar) this.findViewById(R.id.pending_progress);
        int count = DelayedRequester.current.getQueueSize();
        this.progress.setMax(count);
        this.remaining.setText(String.format(this.getString(R.string.requester_pending),
                                             count));
    }
}