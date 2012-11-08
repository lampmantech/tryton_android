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

import java.util.Map;

import org.tryton.client.models.Model;
import org.tryton.client.models.Preferences;
import org.tryton.client.tools.FieldsConvertion;

/** The user session that is stored in memory and is flushed when the
 * application is killed.
 */
public class Session {

    /** The running session */
    public static Session current = new Session();

    public String user;
    public String password;
    public int userId = -1;
    public String cookie;
    public Preferences prefs;
    
    /** Model currently edited in form view. Use editModel to set its value */
    public Model editedModel;
    public Model tempModel;

    private Session() {}

    /** Set session to edit a record. Use null to stop editing. */
    public void editModel(Model data) {
        this.editedModel = data;
        if (data != null) {
            this.tempModel = new Model(data.getClassName());
            this.tempModel.set("id", data.get("id"));
        } else {
            this.tempModel = null;
        }
    }
    /** Set session to create a new record. */
    public void editNewModel(String className) {
        this.editedModel = null;
        this.tempModel = new Model(className);
    }

    public boolean editedIsDirty() {
        if (this.editedModel == null) {
            return true;
        }
        if (this.tempModel == null) {
            return false;
        }
        for (String attr : this.tempModel.getAttributeNames()) {
            Object value = this.editedModel.get(attr);
            Object tmpValue = this.tempModel.get(attr);
            if ((tmpValue == null && value != null)
                || (tmpValue != null && !tmpValue.equals(value))) {
                // Check for tree field values
                if (value instanceof Map) {
                    Map mVal = (Map) value;
                    if (mVal.containsKey("decimal")) {
                        Map tmpVal = (Map) tmpValue;
                        // Its a numeric
                        double val = FieldsConvertion.numericToDouble(mVal);
                        double tmp = FieldsConvertion.numericToDouble(tmpVal);
                        if (val == tmp) {
                            // Not dirty
                            continue;
                        }
                    } else if (mVal.containsKey("year")) {
                        Map tmpVal = (Map) tmpValue;
                        if (mVal.containsKey("hour")) {
                            // It's a datetime
                            int[] val = FieldsConvertion.dateTimeToIntA(mVal);
                            int[] tmp = FieldsConvertion.dateTimeToIntA(tmpVal);
                            if (val[0] == tmp[0] && val[1] == tmp[1]
                                && val[2] == tmp[2] && val[3] == tmp[3]
                                && val[4] == tmp[4]) {
                                // Not dirty
                                continue;
                            }
                        } else {
                            // It's a date
                            int[] val = FieldsConvertion.dateToIntA(mVal);
                            int[] tmp = FieldsConvertion.dateToIntA(tmpVal);
                            if (val[0] == tmp[0] && val[1] == tmp[1]
                                && val[2] == tmp[2]) {
                                continue;
                            }
                        }
                    } else if (mVal.containsKey("hour")) {
                        Map tmpVal = (Map) tmpValue;
                        // It's a time
                        int[] val = FieldsConvertion.timeToIntA(mVal);
                        int[] tmp = FieldsConvertion.timeToIntA(tmpVal);
                        if (val[0] == tmp[0] && val[1] == tmp[1]) {
                            continue;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    public void clear() {
        this.user = null;
        this.password = null;
        this.userId = -1;
        this.cookie = null;
        this.prefs = null;
        this.editedModel = null;
    }

}
