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
package org.tryton.client.views;

import org.tryton.client.R;
import org.tryton.client.models.MenuEntry;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.RelativeLayout;

public class MenuEntryItem extends RelativeLayout {

    private MenuEntry entry;

    private TextView label;

    public MenuEntryItem(Context context, MenuEntry menu) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.menu_item,
                                             this,
                                             true);
        this.label = (TextView) this.findViewById(R.id.menu_label);
        this.reuse(menu);
    }

    public void reuse(MenuEntry menu) {
        this.entry = menu;
        this.label.setText(this.entry.getLabel());
    }

    public MenuEntry getMenuEntry() {
        return this.entry;
    }

}