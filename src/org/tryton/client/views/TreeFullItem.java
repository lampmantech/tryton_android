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
import java.util.ArrayList;
import java.util.List;

public class TreeFullItem extends LinearLayout {

    private ModelView modelView;
    private Model model;
    private List<TextView> values;

    public TreeFullItem(Context context, ModelView modelView, Model model) {
        super(context);
        this.setOrientation(LinearLayout.VERTICAL);
        this.setMinimumHeight((int)context.getResources().getDimension(R.dimen.clickable_min_size));
        this.setGravity(Gravity.CENTER_VERTICAL);
        this.modelView = modelView;
        this.values = new ArrayList<TextView>();
        if (this.modelView != null) {
            for (int i = 0; i < this.modelView.getStructure().size(); i++) {
                Model field = this.modelView.getStructure().get(i);
                if (TreeViewFactory.isFieldView(field)) {
                    TextView t = new TextView(context);
                    this.values.add(t);
                    this.addView(t);
                }
            }
        } else {
            // No view, use only rec_name
            TextView t = new TextView(context);
            this.values.add(t);
            this.addView(t);
        }
        this.reuse(model, context);
    }

    public void reuse(Model model, Context ctx) {
        this.model = model;
        if (this.modelView != null) {
            List<Model> structure = this.modelView.getStructure();
            int innerIndex = 0;
            for (int i = 0; i < structure.size(); i++) {
                Model field = structure.get(i);
                if (TreeViewFactory.isFieldView(field)) {
                    TextView t = this.values.get(innerIndex);
                    String fieldName = (String) field.get("name");
                    String name = (String) field.get("string");
                    if (name == null) {
                        name = (String) field.get("name");
                    }
                    String value = TreeViewFactory.getView(field, this.model,
                                                           Session.current.prefs,
                                                           ctx);
                    t.setText(name + " " + value);
                    innerIndex++;
                }
            } 
        } else {
            TextView t = this.values.get(0);
            Model recName = new Model("ir.ui.field");
            recName.set("name", "rec_name");
            recName.set("type", "char");
            String value = TreeViewFactory.getView(recName, this.model,
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