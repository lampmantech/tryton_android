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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class About extends Activity {

    public void onCreate(Bundle state) {
        super.onCreate(state);
        this.setContentView(R.layout.about);
        TextView version = (TextView) this.findViewById(R.id.about_version);
        try {
            PackageManager pm = this.getPackageManager();
            PackageInfo info = pm.getPackageInfo(this.getPackageName(), 0);
            String v = info.versionName;
            String name = this.getString(R.string.app_name);
            version.setText(name + " " + v);
        } catch (PackageManager.NameNotFoundException nnfe) {
            // unreachable
        }
    }
}