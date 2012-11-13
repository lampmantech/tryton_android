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
package org.tryton.client.models;

import java.io.Serializable;

/** Structure to store relationnal field data. */
public class RelField implements Serializable {

    /** Auto generated serial UID */
    static final long serialVersionUID = -1116721211685486650L;

    private String fieldName;
    private String type;
    private String relModel;

    public RelField(String fieldName, String type, String relModel) {
        this.fieldName = fieldName;
        this.type = type;
        this.relModel = relModel;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public String getType() {
        return this.type;
    }

    public String getRelModel() {
        return this.relModel;
    }
}