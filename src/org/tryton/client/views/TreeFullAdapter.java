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
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.List;

/** Adapter for tree view displaying full information.
 * This class is responsible for giving views to a list widget
 * according to its data and position.
 * It manages MenuEntryItems.
 */
public class TreeFullAdapter extends BaseAdapter {

    private ModelView modelView;
    private List<Model> data;

    public TreeFullAdapter(ModelView modelView, List<Model> data) {
        super();
        this.modelView = modelView;
        this.data = data;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Object getItem(int position) {
        return this.data.get(position);
    }

    @Override
    public int getCount() {
        return this.data.size();
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Model m = this.data.get(position);
        if (convertView != null && convertView instanceof TreeFullItem) {
            // Reusing allows to update a view that goes off-screen to reduce
            // scrolling cpu usage (thus smoothing it).
            TreeFullItem item = (TreeFullItem) convertView;
            item.reuse(m, parent.getContext());
            return item;
        } else {
            // Not reusing. Create the view from scratch.
            Context ctx = parent.getContext();
            TreeFullItem item = new TreeFullItem(parent.getContext(),
                                                 this.modelView, m);
            return item;
        }
    }
}