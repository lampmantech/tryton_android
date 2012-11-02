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
package org.tryton.client.tools;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.tryton.client.R;
import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.Preferences;

/** Factory to convert various data types to widgets for tree views. */
public class FormViewFactory {

    public static View getView(Model field, ModelView view, Model data,
                               Preferences prefs, Context ctx) {
        String className = field.getClassName();
        if (className.equals("label")) {
            // Label widget
            if (field.getString("string") != null) {
                // Use the string attribute as label
                TextView label = new TextView(ctx);
                label.setText(field.getString("string"));
                return label;
            } else if (field.getString("name") != null) {
                Model ref = view.getField(field.getString("name"));
                String name = ref.getString("string");
                if (name == null) {
                    name = ref.getString("name");
                }
                TextView label = new TextView(ctx);
                label.setText(name);
                return label;
            } else {
                Log.w("Tryton", "Label with neither string nor label");
            }
        } else {
            // Value widget, must check field type to get the appropriate one
            String name = field.getString("name");
            String type = field.getString("type");
            if (type.equals("char") || type.equals("text")
                || type.equals("integer") || type.equals("biginteger")
                || type.equals("float") || type.equals("numeric")) {
                String value = "";
                if (data != null) {
                    if (type.equals("char") || type.equals("text")) {
                        value = data.getString(name);
                    } else if (type.equals("integer")
                               || type.equals("biginteger")) {
                        value = String.valueOf(((Integer)data.get(name)));
                    } else if (type.equals("float") || type.equals("numeric")) {
                        Object oval = data.get(name);
                        double dval = 0;
                        if (oval instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mVal = (Map<String, Object>) oval;
                            dval = Double.parseDouble((String)mVal.get("decimal"));
                        } else {
                            dval = (Double)oval;
                        }
                        value = String.valueOf(dval);
                    }
                }
                EditText edit = new EditText(ctx);
                // Set lines and input type according to type
                if (type.equals("char")) {
                    edit.setSingleLine(true);
                } else if (type.equals("text")) {
                    edit.setLines(4);
                } else if (type.equals("integer")
                           || type.equals("biginteger")) {
                    edit.setSingleLine(true);
                    edit.setInputType(InputType.TYPE_CLASS_NUMBER
                                      | InputType.TYPE_NUMBER_FLAG_SIGNED);
                } else if (type.equals("float") || type.equals("numeric")) {
                    edit.setSingleLine(true);
                    edit.setInputType(InputType.TYPE_CLASS_NUMBER
                                      | InputType.TYPE_NUMBER_FLAG_SIGNED
                                      | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    // Set digits constraint
                    String digits = (String) field.get("digits");
                    if (digits != null) {
                        digits = digits.substring(1, digits.length() - 1);
                        String[] split = digits.split(",");
                        int intNum = Integer.parseInt(split[0].trim());
                        int decNum = Integer.parseInt(split[1].trim());
                        DigitsConstraint c = new DigitsConstraint(intNum,
                                                                  decNum);
                        edit.addTextChangedListener(c);
                    }
                }
                // Set maximum size if any (usefull only for char and text)
                String strSize = field.getString("size");
                int maxSize = -1;
                if (strSize != null) {
                    maxSize = Integer.parseInt(strSize);
                }
                if (maxSize != -1) {
                    InputFilter f = new InputFilter.LengthFilter(maxSize);
                    edit.setFilters(new InputFilter[]{f});
                }
                edit.setText(value);
                return edit;
            } else if (type.equals("boolean")) {
                boolean value = false;
                if (data != null) {
                    value = (Boolean)data.get(name);
                }
                CheckBox cb = new CheckBox(ctx);
                cb.setChecked(value);
                return cb;
            } else if (type.equals("sha")) {
                System.out.println("Sha type not supported yet");
            } else if (type.equals("date")) {
                Object value = null;
                DatePicker p = new DatePicker(ctx);
                if (data != null) {
                    Object oval = data.get(name);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mDate = (Map<String, Object>) oval;
                    int year = 0, month = 0, day = 0;
                    if (oval != null) {
                        year = (Integer) mDate.get("year");
                        month = (Integer) mDate.get("month");
                        day = (Integer) mDate.get("day");
                        p.updateDate(year, month - 1, day);
                    }
                }
                return p;
                // TODO: It would be better if picker was in a popup
            } else if (type.equals("datetime")) {
                System.out.println(type + " not supported yet");
            } else if (type.equals("time")) {
                TimePicker t = new TimePicker(ctx);
                if (data != null) {
                    Object oval = data.get(name);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mDate = (Map<String, Object>) oval;
                    int hour = 0, minute = 0, second = 0;
                    if (oval != null) {
                        hour = (Integer) mDate.get("hour");
                        minute = (Integer) mDate.get("minute");
                        second = (Integer) mDate.get("second");
                        t.setCurrentHour(hour);
                        t.setCurrentMinute(minute);
                    }
                }
                return t;
                // TODO: It would be better if picker was in a popup
                // TODO: TimePicker doesn't support seconds
            } else if (type.equals("binary")) {
                System.out.println(type + " not supported yet");
            } else if (type.equals("selection")) {
                // Create the widget
                Spinner s = new Spinner(ctx);
                @SuppressWarnings("unchecked")
                List<List> selectValues = (List<List>) field.get("selection");
                SelectAdapter adapt = new SelectAdapter(selectValues);
                s.setAdapter(adapt);
                // Set value
                if (data != null) {
                    String value = data.getString(name);
                    if (value != null) {
                        for (int i = 0; i < selectValues.size(); i++) {
                            List couple = selectValues.get(i);
                            if (((String)couple.get(0)).equals(value)) {
                                s.setSelection(i);
                                break;
                            }
                        }
                    }
                }
                return s;
            } else if (type.equals("reference")) {
                System.out.println("Reference type not supported yet");
            } else if (type.equals("many2one") || type.equals("one2one")) {
                System.out.println(type + " not supported yet");
            } else if (type.equals("many2many") || type.equals("one2many")) {
                System.out.println(type + " not supported yet");
            } else if (type.equals("function")) {
                System.out.println("Function type not supported yet");
            } else if (type.equals("property")) {
                System.out.println("Property type not supported yet");
            } else {
                System.out.println("Unknown type " + type);
            }

        }
        return new View(ctx);
    }

    /** An adapter to provide values to a select widget
        (either for a selection field or a X2one field) */
    public static class SelectAdapter extends BaseAdapter {

        private List<String> values;
        private List<String> labels;

        public SelectAdapter(List<List> selection) {
            this.values = new ArrayList<String>();
            this.labels = new ArrayList<String>();
            for (List couple : selection) {
                String value = (String) couple.get(0);
                String label = (String) couple.get(1);
                values.add(value);
                labels.add(label);
            }
        }

        public long getItemId(int position) {
            return position;
        }

        public Object getItem(int position) {
            return this.values.get(position);
        }

        public int getCount() {
            return this.labels.size();
        }

        public View getView(int position, View convertView,
                            ViewGroup parent) {
            String label = this.labels.get(position);
            if (convertView instanceof TextView) {
                ((TextView)convertView).setText(label);
                return convertView;
            } else {
                TextView t = new TextView(parent.getContext());
                t.setText(label);
                return t;
            }
        }
    }

    public static class DigitsConstraint implements TextWatcher {

        private int intNum;
        private int decNum;
        private String oldValue;

        public DigitsConstraint(int intNum, int decNum) {
            this.intNum = intNum;
            this.decNum = decNum;
        }

        public void afterTextChanged(Editable s) {
            String text = s.toString();
            String[] split = text.split("\\."); // this is a regex
            String[] oldSplit = this.oldValue.split("\\.");
            switch (split.length) {
            case 1:
                // Only integer part
                if (text.length() > this.intNum && !text.endsWith(".")) {
                    // Keep old value
                    s.replace(0, text.length(), this.oldValue);
                }
                break;
            case 2:
                // Integer and decimal parts
                String newString;
                if (split[0].length() > this.intNum) {
                    newString = oldSplit[0];
                } else {
                    newString = split[0];
                }
                newString += ".";
                if (split[1].length() > this.decNum) {
                    newString += oldSplit[1];
                } else {
                    newString += split[1];
                }
                if (!newString.equals(text)) {
                    s.replace(0, text.length(), newString);
                }
                break;
            }
        }
        
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
            this.oldValue = s.toString();
        }
        public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {}
    }
}