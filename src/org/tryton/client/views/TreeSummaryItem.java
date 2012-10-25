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
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class TreeSummaryItem extends LinearLayout {

    public static final int FIELDS_COUNT = 2;

    private ModelView modelView;
    private Model model;
    private List<TextView> values;

    public TreeSummaryItem(Context context, ModelView modelView, Model model) {
        super(context);
        this.setOrientation(LinearLayout.VERTICAL);
        this.modelView = modelView;
        this.values = new ArrayList<TextView>();
        for (int i = 0; i < Math.min(this.modelView.getStructure().size(),
                                     FIELDS_COUNT);
             i++) {
            TextView t = new TextView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.leftMargin = (int) context.getResources().getDimension(R.dimen.expandable_list_margin);
            t.setLayoutParams(params);
            this.values.add(t);
            this.addView(t);
        }
        this.reuse(model, context);
    }

    public void reuse(Model model, Context ctx) {
        this.model = model;
        List<Model> structure = this.modelView.getStructure();
        for (int i = 0; i < Math.min(structure.size(),
                                     FIELDS_COUNT); i++) {
            TextView t = this.values.get(i);
            Model field = structure.get(i);
            String value = TreeViewFactory.getView(field, this.model,
                                                   Session.current.prefs,
                                                   ctx);
            t.setText(value);
        } 
    }

    public Model getModel() {
        return this.model;
    }

    public ModelView getModelView() {
        return this.modelView;
    }
}