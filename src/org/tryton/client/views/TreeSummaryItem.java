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
import org.tryton.client.data.Session;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.tools.TreeViewFactory;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;

public class TreeSummaryItem extends LinearLayout {

    private Model model;
    private TextView value;

    public TreeSummaryItem(Context context, Model model) {
        super(context);
        this.setOrientation(LinearLayout.VERTICAL);
        this.value = new TextView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = (int) context.getResources().getDimension(R.dimen.expandable_list_margin);
        this.value.setLayoutParams(params);
        this.value.setMinHeight((int) context.getResources().getDimension(R.dimen.clickable_min_size));
        this.value.setGravity(Gravity.CENTER_VERTICAL);
        this.addView(value);
        this.reuse(model, context);
    }

    public void reuse(Model model, Context ctx) {
        this.model = model;
        this.value.setText(this.model.getString("rec_name"));
    }

    public Model getModel() {
        return this.model;
    }

}