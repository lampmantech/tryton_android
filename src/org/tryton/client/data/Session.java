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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.tryton.client.models.Model;
import org.tryton.client.models.Preferences;
import org.tryton.client.tools.DelayedRequester;
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
    
    /** Model currently edited in form view. Use editModel to set its value.
     * EditedModel is null when creating a new record.*/
    public Model editedModel;
    /** Model with edited values. Never null except when nothing
     * is beeing edited. TempModel always has the id of the original model
     * when editing and does not have an id when creating. */
    public Model tempModel;
    /** Relationnal field name in parent */
    public String linkToParent;
    /** Many2one field in current linked to parent's one2many field */
    public String linkToSelf;
    /** Stack to allow to edit multiple models at once (for relationnal).
     * The top of the stack is always editedModel, tempModel and linkField. */
    private List editStack;
    /** Index of the bloc in stack that correspond to the edited command */
    private int editedCmdIndex;
    
    private Session() {
        this.editStack = new ArrayList();
        this.editedCmdIndex = -1;
    }

    /** Push editing models on stack */
    @SuppressWarnings("unchecked")
    private void pushStack() {
        this.editStack.add(editedModel);
        this.editStack.add(tempModel);
        this.editStack.add(linkToParent);
        this.editStack.add(linkToSelf);
    }
    /** Pop editing stack to set editing models with previous values. */
    private void popStack() {
        if (this.editStack.size() > 2) {
            // Pop
            this.editStack.remove(this.editStack.size() - 1);
            this.editStack.remove(this.editStack.size() - 1);
            this.editStack.remove(this.editStack.size() - 1);
            this.editStack.remove(this.editStack.size() - 1);
        }
        int size = this.editStack.size();
        if (size > 3) {
            // Set editing models if there are still one
            this.editedModel = (Model) this.editStack.get(size - 4);
            this.tempModel = (Model) this.editStack.get(size - 3);
            this.linkToParent = (String) this.editStack.get(size - 2);
            this.linkToSelf = (String) this.editStack.get(size - 1);
        } else {
            // End of edit, stack is empty
            this.editedModel = null;
            this.tempModel = null;
            this.linkToParent = null;
            this.linkToSelf = null;
        }
        if (this.editedCmdIndex >= this.editStack.size() / 4) {
            this.editedCmdIndex = -1;
        }
    }

    /** Set session to edit a record. */
    public void editModel(Model data) {
        this.editedModel = data;
        this.tempModel = new Model(data.getClassName());
        this.tempModel.set("id", data.get("id"));
        this.linkToParent = null;
        this.linkToSelf = null;
        this.pushStack();
    }
    /** Set session to edit a command.
     * Only one command can be edited in session. */
    public void editCommand(DelayedRequester.Command cmd) {
        this.editedCmdIndex = this.editStack.size() / 4;
        this.editModel(cmd.getData());
    }
    /** Set session to edit a one2many subrecord. ParentField is the 
     * name of the field that links to new model. */
    public void editOne2Many(Model data, String parentField,
                             String childField) {
        this.tempModel = new Model(data.getClassName());
        if (data.hasAttribute("id")) {
            // When editing a new one2many record, the record has no id
            this.tempModel.set("id", data.get("id"));
        }
        if (this.editedModel != null && this.editedModel.hasAttribute("id")) {
            this.tempModel.set(childField, (Integer) this.editedModel.get("id"));
        }
        this.editedModel = data;
        this.linkToParent = parentField;
        this.linkToSelf = childField;
        this.pushStack();
    }
    /** Set session to edit a many2many subrecord. */
    public void editMany2Many(Model data, String parentField) {
        this.tempModel = new Model(data.getClassName());
        this.tempModel.set("id", data.get("id"));
        this.editedModel = data;
        this.linkToParent = parentField;
        this.linkToSelf = null;
        this.pushStack();
    }
    /** Set session to create a new record. */
    public void editNewModel(String className) {
        this.editedModel = null;
        this.tempModel = new Model(className);
        this.linkToParent = null;
        this.linkToSelf = null;
        this.pushStack();
    }
    /** Set session to create a new many2many subrecord. */
    public void editNewMany2Many(String className, String parentField) {
        this.tempModel = new Model(className);
        this.linkToParent = parentField;
        this.linkToSelf = null;
        this.editedModel = null;
        this.pushStack();
    }
    /** Set session to create a new one2many subrecord. */
    public void editNewOne2Many(String className, String parentField,
                                String childField) {
        this.tempModel = new Model(className);
        if (this.editedModel != null && this.editedModel.hasAttribute("id")) {
            // When creating record the parent has no id, it will be given
            // when the whole is saved. When the parent exists the id can be
            // propagated
            this.tempModel.set(childField, (Integer) this.editedModel.get("id"));
        }
        this.linkToParent = parentField;
        this.linkToSelf = childField;
        this.editedModel = null;
        this.pushStack();
    }
    /** Update the currently edited model with new values.
     * Mainly for updating from tree to form view. */
    @SuppressWarnings("unchecked")
    public void updateEditedModel(Model updated) {
        this.editedModel = updated;
        this.editStack.remove(this.editStack.size() - 4);
        this.editStack.add(this.editStack.size() - 3, this.editedModel);
    }

    /** Finish editing the current model
     * and return back to the previous, if any */
    public void finishEditing() {
        this.popStack();
    }

    /** Add a value for the edited Many2Many/One2Many parent field. */
    @SuppressWarnings("unchecked")
    public void addToParent(int newId) {
        Model parent = (Model) this.editStack.get(this.editStack.size() - 8);
        Model tmpParent = (Model) this.editStack.get(this.editStack.size() - 7);
        if (tmpParent.get(this.linkToParent) == null) {
            // Get original id list and add the new to it
            List<Integer> ids = new ArrayList<Integer>();
            if (parent.get(this.linkToParent) != null) {
                List<Integer> pIds = (List<Integer>)parent.get(this.linkToParent);
                ids.addAll(pIds);
            }
            if (!ids.contains(newId)) {
                ids.add(newId);
                tmpParent.set(this.linkToParent, ids);
            }
        } else {
            // Ids already set, just add it to the list
            List<Integer> ids = (List<Integer>)tmpParent.get(this.linkToParent);
            if (!ids.contains(newId)) {
                ids.add(newId);
            }
        }
    }

    /** Add a one2many to be saved along the parent */
    public void addOne2Many() {
        Model parent = (Model) this.editStack.get(this.editStack.size() - 8);
        Model tmpParent = (Model) this.editStack.get(this.editStack.size() - 7);
        Model submodel = this.tempModel;
        // Make sure parent is up-to-date with the field ids to add the new one
        if (tmpParent.get(this.linkToParent) == null) {
            if (parent != null && parent.get(this.linkToParent) != null) {
                @SuppressWarnings("unchecked")
                List<Integer> ids = (List<Integer>) parent.get(this.linkToParent);
                List<Integer> tmpIds = new ArrayList<Integer>();
                tmpIds.addAll(ids);
                tmpParent.set(this.linkToParent, tmpIds);
            }
        }
        tmpParent.addNewOne2Many(this.linkToParent, submodel);
    }
    /** Update a one2many to be saved along the parent */
    public void updateOne2Many() {
        Model parent = (Model) this.editStack.get(this.editStack.size() - 8);
        Model tmpParent = (Model) this.editStack.get(this.editStack.size() - 7);
        Model oldSubmodel = this.editedModel;
        Model submodel = this.tempModel;
        // Set the field in parent to mark it as dirty
        if (tmpParent.get(this.linkToParent) == null) {
            if (parent != null && parent.get(this.linkToParent) != null) {
                @SuppressWarnings("unchecked")
                List<Integer> ids = (List<Integer>) parent.get(this.linkToParent);
                List<Integer> tmpIds = new ArrayList<Integer>();
                tmpIds.addAll(ids);
                tmpParent.set(this.linkToParent, tmpIds);
            }
        }
        tmpParent.editOne2Many(this.linkToParent, oldSubmodel, submodel);
    }
    
    public void deleteOne2Many() {
        deleteOne2Many(this.editedModel);
    }
    public void deleteOne2Many(Model m) {
        Model parent = (Model) this.editStack.get(this.editStack.size() - 8);
        Model tmpParent = (Model) this.editStack.get(this.editStack.size() - 7);
        // Make sure parent is up-to-date with the field ids to remove one
        if (tmpParent.get(this.linkToParent) == null) {
            if (parent != null && parent.get(this.linkToParent) != null) {
                @SuppressWarnings("unchecked")
                List<Integer> ids = (List<Integer>) parent.get(this.linkToParent);
                List<Integer> tmpIds = new ArrayList<Integer>();
                tmpIds.addAll(ids);
                tmpParent.set(this.linkToParent, tmpIds);
            }
        }
        tmpParent.deleteOne2Many(this.linkToParent, m);
    }

    public boolean isEditingTop() {
        return this.editStack.size() <= 4;
    }

    /** Check if current edited model is a subrecord of a main record.
     * I.e. the parent model has a link to the edited model. */
    public boolean isEditingSub() {
        return this.linkToParent != null;
    }

    public boolean isEditingOne2Many() {
        return this.linkToParent != null && this.linkToSelf != null;
    }
    
    public boolean isEditingMany2Many() {
        return this.linkToParent != null && this.linkToSelf == null;
    }

    public boolean isEditingCommand() {
        return this.editedCmdIndex != -1;
    }
    /** Check if a new model is being created. If true it also means that
     * editedModel cannot be used. */
    public boolean isCreatingModel() {
        return this.editedModel == null;
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
            // one2many operations
            if (this.tempModel.getOne2ManyOperations(attr) != null
                && this.tempModel.getOne2ManyOperations(attr).size() > 0) {
                return true;
            }
            // Special case of decimal null representation
            if (value instanceof Map && ((Map)value).containsKey("decimal")
                && FieldsConvertion.numericToDouble((Map)value) == null) {
                value = null;
            }
            if (tmpValue instanceof Map
                && ((Map)tmpValue).containsKey("decimal")
                && FieldsConvertion.numericToDouble((Map)tmpValue) == null) {
                tmpValue = null;
            }
            // Null comparisons
            if ((tmpValue == null && value != null)
                || (tmpValue != null && value == null)) {
                return true;
            }
            // Values comparison
            if ((tmpValue == null && value != null)
                || (tmpValue != null && !tmpValue.equals(value))) {
                // Check for tree field values
                if (value instanceof Map || tmpValue instanceof Map) {
                    if (value instanceof Double) {
                        Double tmp = FieldsConvertion.numericToDouble((Map)tmpValue);
                        if ((value == null && tmp == null)
                            || (value != null && value.equals(tmp))
                            || (tmp != null && tmp.equals(value))) {
                            // Not dirty
                            continue;
                        }
                    }
                    Map mVal = (Map) value;
                    Map tmpVal = (Map) tmpValue;
                    if (mVal.containsKey("decimal")) {
                        // Its a numeric
                        Double val = FieldsConvertion.numericToDouble(mVal);
                        Double tmp = FieldsConvertion.numericToDouble(tmpVal);
                        if ((val == null && tmp == null)
                            || (val != null && val.equals(tmp))
                            || (tmp != null && tmp.equals(val))) {
                            // Not dirty
                            continue;
                        }
                    } else if (mVal.containsKey("year")) {
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
