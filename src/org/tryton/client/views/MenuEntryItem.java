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
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.RelativeLayout;
import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

public class MenuEntryItem extends RelativeLayout {

    /** Memory cache for default icon */
    private static Drawable defaultFolderIcon = null;

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
            if (defaultFolderIcon == null) {
                SVG svg = SVGParser.getSVGFromResource(ctx.getResources(),
                                                       R.raw.tryton_open);
                defaultFolderIcon = svg.createPictureDrawable();
            }
            this.icon.setImageDrawable(defaultFolderIcon);
        }
    }

    public MenuEntry getMenuEntry() {
        return this.entry;
    }

}