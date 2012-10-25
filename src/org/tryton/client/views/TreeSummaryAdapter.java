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
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import java.util.List;

/** Adapter for tree view displaying full information.
 * This class is responsible for giving views to a list widget
 * according to its data and position.
 * It manages MenuEntryItems.
 */
public class TreeSummaryAdapter extends BaseExpandableListAdapter {

    private ModelView modelView;
    private List<Model> data;

    public TreeSummaryAdapter(ModelView modelView, List<Model> data) {
        super();
        this.modelView = modelView;
        this.data = data;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }
    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return this.data.get(groupPosition);
    }
    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return groupPosition * 1000 + childPosition;
    }
    @Override
    public int getChildrenCount(int groupPosition) {
        return 1;
    }
    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
    @Override
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild, View convertView,
                             ViewGroup parent) {
        Model m = this.data.get(groupPosition);
        if (convertView != null
            && convertView instanceof TreeSumExtItem
            && ((TreeSumExtItem)convertView).getModelView().equals(this.modelView) ) {
            // Reusing allows to update a view that goes off-screen to reduce
            // scrolling cpu usage (thus smoothing it).
            TreeSumExtItem item = (TreeSumExtItem) convertView;
            item.reuse(m, parent.getContext());
            return item;
        } else {
            // Not reusing. Create the view from scratch.
            Context ctx = parent.getContext();
            TreeSumExtItem item = new TreeSumExtItem(parent.getContext(),
                                                     this.modelView, m);
            return item;
        }
    }

    @Override
    public Object getGroup(int groupPosition) {
        return this.data.get(groupPosition);
    }
    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }
    @Override
    public int getGroupCount() {
        return this.data.size();
    }
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        Model m = this.data.get(groupPosition);
        if (convertView != null && convertView instanceof TreeSummaryItem
            && ((TreeSummaryItem)convertView).getModelView().equals(this.modelView) ) {
            // Reusing allows to update a view that goes off-screen to reduce
            // scrolling cpu usage (thus smoothing it).
            TreeSummaryItem item = (TreeSummaryItem) convertView;
            item.reuse(m, parent.getContext());
            return item;
        } else {
            // Not reusing. Create the view from scratch.
            Context ctx = parent.getContext();
            TreeSummaryItem item = new TreeSummaryItem(parent.getContext(),
                                                       this.modelView, m);
            return item;
        }
    }

}