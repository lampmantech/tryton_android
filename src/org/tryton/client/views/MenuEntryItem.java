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
import org.tryton.client.tools.SVGFactory;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.RelativeLayout;

public class MenuEntryItem extends RelativeLayout {

    private MenuEntry entry;

    private TextView label;
    private ImageView icon;

    public MenuEntryItem(Context context, MenuEntry menu) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.menu_item,
                                             this,
                                             true);
        this.label = (TextView) this.findViewById(R.id.menu_label);
        this.icon = (ImageView) this.findViewById(R.id.menu_icon);
        this.reuse(menu, context);
    }

    public void reuse(MenuEntry menu, Context ctx) {
        this.entry = menu;
        this.label.setText(this.entry.getLabel());
        if (this.entry.getIcon() != null) {
            this.icon.setImageDrawable(this.entry.getIcon());
        } else {
            this.setDefaultIcon(ctx);
        }
    }

    private void setDefaultIcon(Context ctx) {
        if (this.entry.getChildren().size() > 0) {
            Drawable folderIcon = SVGFactory.getDrawable("tryton-open",
                                                         R.raw.tryton_open,
                                                         ctx);
            this.icon.setImageDrawable(folderIcon);
        } else if (this.entry.getActionType() != null) {
            if (this.entry.getActionType().equals("ir.action.wizard")) {
                Drawable wizardIcon = SVGFactory.getDrawable("tryton-executable",
                                                             R.raw.tryton_executable,
                                                             ctx);
                this.icon.setImageDrawable(wizardIcon);
            } else if (this.entry.getActionType().equals("ir.action.report")) {
                Drawable reportIcon = SVGFactory.getDrawable("tryton-print",
                                                             R.raw.tryton_print,
                                                             ctx);
                this.icon.setImageDrawable(reportIcon);
            } else if (this.entry.getActionType().equals("ir.action.url")) {
                Drawable urlIcon = SVGFactory.getDrawable("tryton-web-browser",
                                                          R.raw.tryton_web_browser,
                                                          ctx);
                this.icon.setImageDrawable(urlIcon);
            } else {
                this.icon.setImageDrawable(null);
            }
        } else {
            this.icon.setImageDrawable(null);
        }
    }

    public MenuEntry getMenuEntry() {
        return this.entry;
    }

}