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

import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.tryton.client.tools.TrytonCall;

/** Configuration activity.
 * The configuration screen is mainly loaded from the xml resource.
 * Still it shares some keys set in the xml file to easily retreive
 * the user's values.
 *
 * As a PreferenceActivity all the hard stuff is automatically done by loading
 * the preferences through the xml layout.
 */
public class Configure extends PreferenceActivity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Load preferences from xml
        this.addPreferencesFromResource(R.layout.configure);
    }

    /** Called when the activity is interrupted (or quitted). */
    public void onPause() {
        super.onPause();
        // Refresh TrytonCall with the new values
        TrytonCall.setup(Configure.getHost(this), Configure.getDatabase(this));
    }

    /** Check if the application is configured. */
    public static boolean isConfigured(Context ctx) {
        // Get the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        // Checks that mandatory values are set.
        return !prefs.getString("host", "").equals("")
               && !prefs.getString("database", "").equals("");
    }

    /** Get host value.
     * @return The user's host value, empty string by default.
     */
    public static String getHost(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("host", "");
    }

    /** Get database value
     * @return The user's database value, empty string by default.
     */
    public static String getDatabase(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("database", "");
    }
}
