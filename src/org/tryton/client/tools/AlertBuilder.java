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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import org.tryton.client.R;
import org.tryton.client.data.Session;

/** Utility class to present popup messages. */
public class AlertBuilder {

    /** Show Tryton user error. Does nothing and returns false if e is
     * not a Tryton user error. */
    public static boolean showUserError(Exception e, Context ctx) {
        return showUserError(e, ctx, null);
    }

    public static boolean showUserError(Exception e, Context ctx,
                                        DialogInterface.OnCancelListener l) {
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
        b.setOnCancelListener(l);
        b.show();
        return true;        
    }

    /** Show Tryton user error. Does nothing and returns false if e is
     * not a Tryton user error. */
    public static boolean showUserWarning(Exception e, Context ctx) {
        return showUserWarning(e, ctx, null);
    }

    public static boolean showUserWarning(Exception e, Context ctx,
                                          DialogInterface.OnCancelListener l) {
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
        b.setOnCancelListener(l);
        b.show();
        return true;
    }

    public static final int RELOG_OK = 5000;
    public static final int RELOG_CANCEL  = 5001;

    /** Show login popup for session timeout. Handler will receive a message
     * with what as RELOG_OK (success) or RELOG_CANCEL (aborded) */
    public static void showRelog(Context ctx, Handler h) {
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(R.string.login_popup_title);
        LayoutInflater li = LayoutInflater.from(ctx);
        View content = li.inflate(R.layout.login_popup, null);
        b.setView(content);
        LoginListener l = new LoginListener(content);
        b.setPositiveButton(R.string.general_ok, l);
        b.setNegativeButton(R.string.general_cancel, l);
        b.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Message m = relogHandler.obtainMessage();
                    m.what = RELOG_CANCEL;
                    m.sendToTarget();
                }
            });
        b.show();
        relogHandler = h;
    }

    private static Handler relogHandler;
    private static ProgressDialog loadingDialog;
    public static void updateRelogHandler(Handler h, Context ctx) {
        relogHandler = h;
        if (loadingDialog != null) {
            showLoadingDialog(ctx);
        }
    }
    private static void showLoadingDialog(Context ctx) {
        loadingDialog = new ProgressDialog(ctx);
        loadingDialog.setIndeterminate(true);
        loadingDialog.setCancelable(false);
        loadingDialog.setMessage(ctx.getString(R.string.login_logging_in));
        loadingDialog.show();
    }
    public static void hideLoadingDialog() {
        if (loadingDialog != null) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    private static class LoginListener
        implements DialogInterface.OnClickListener, Handler.Callback {
        private View contentView;
        private ProgressDialog loadingDialog;

        public LoginListener(View content) {
            this.contentView = content;
        }
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                EditText prompt = (EditText) this.contentView.findViewById(R.id.login_password);
                String password = prompt.getText().toString();
                TrytonCall.login(Session.current.user, password,
                                 new Handler(this));
                showLoadingDialog(this.contentView.getContext());
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                Message m = relogHandler.obtainMessage();
                m.what = RELOG_CANCEL;
                m.sendToTarget();
            }
        }

        public boolean handleMessage(Message msg) {
            hideLoadingDialog();
            switch (msg.what) {
            case TrytonCall.CALL_LOGIN_OK:
                boolean success = (msg.arg1 == 1);
                if (success) {
                    Object[] resp = (Object[]) msg.obj;
                    String cookie = (String) resp[1];
                    Session.current.cookie = cookie;
                    Message m = relogHandler.obtainMessage();
                    m.what = RELOG_OK;
                    m.sendToTarget();
                } else {
                    showRelog(this.contentView.getContext(), relogHandler);
                }
                break;
            case TrytonCall.CALL_LOGIN_NOK:
                AlertDialog.Builder b = new AlertDialog.Builder(this.contentView.getContext());
                b.setTitle(R.string.error);
                b.setMessage(R.string.network_error);
                b.show();
                ((Exception)msg.obj).printStackTrace();
                break;
            }
            return true;
        }
    }
}