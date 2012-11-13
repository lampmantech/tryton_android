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

import android.app.AlertDialog;
import android.content.Context;

/** Utility class to present popup messages. */
public class AlertBuilder {

    /** Show Tryton user error. Does nothing and returns false if e is
     * not a Tryton user error. */
    public static boolean showUserError(Exception e, Context ctx) {
        String [] msg = TrytonCall.getUserError(e);
        if (msg == null) {
            return false;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        if (msg[1].equals("")) {
            b.setMessage(msg[0]);
        } else {
            b.setMessage(msg[0] + "\n" + msg[1]);
        }
        b.show();
        return true;
    }

    /** Show Tryton user error. Does nothing and returns false if e is
     * not a Tryton user error. */
    public static boolean showUserWarning(Exception e, Context ctx) {
        String [] msg = TrytonCall.getUserWarning(e);
        if (msg == null) {
            return false;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        if (msg[1].equals("")) {
            b.setMessage(msg[0]);
        } else {
            b.setMessage(msg[0] + "\n" + msg[1]);
        }
        b.show();
        return true;
    }
}