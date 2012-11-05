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

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.Resources;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.tryton.client.R;
import org.tryton.client.data.DataCache;
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
                Button b = new Button(ctx);
                b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                int year = -1, month = -1, day = -1;
                if (data != null) {
                    Object oval = data.get(name);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mDate = (Map<String, Object>) oval;
                    if (oval != null) {
                        year = (Integer) mDate.get("year");
                        month = (Integer) mDate.get("month");
                        day = (Integer) mDate.get("day");
                    }
                }
                // The listener also sets initial text
                b.setOnClickListener(new DateClickListener(b,
                                                           prefs.getDateFormat(),
                                                           year, month, day));
                return b;
            } else if (type.equals("datetime")) {
                Object value = null;
                Button bDate = new Button(ctx);
                bDate.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                Button bTime = new Button(ctx);
                bTime.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                int year = -1, month = -1, day = -1;
                int hour = -1, minute = -1, second = -1;
                if (data != null) {
                    Object oval = data.get(name);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mDate = (Map<String, Object>) oval;
                    if (oval != null) {
                        year = (Integer) mDate.get("year");
                        month = (Integer) mDate.get("month");
                        day = (Integer) mDate.get("day");
                        hour = (Integer) mDate.get("hour");
                        minute = (Integer) mDate.get("minute");
                        second = (Integer) mDate.get("second");
                    }
                }
                // The listener also sets initial text
                bDate.setOnClickListener(new DateClickListener(bDate,
                                                               prefs.getDateFormat(),
                                                               year, month,
                                                               day));
                bTime.setOnClickListener(new TimeClickListener(bTime, hour,
                                                               minute));
                bDate.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                bTime.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                LinearLayout layout = new LinearLayout(ctx);
                layout.addView(bDate);
                layout.addView(bTime);
                return layout;
            } else if (type.equals("time")) {
                Button b = new Button(ctx);
                b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                int hour = -1, minute = -1, second = -1;
                if (data != null) {
                    Object oval = data.get(name);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mDate = (Map<String, Object>) oval;
                    if (oval != null) {
                        hour = (Integer) mDate.get("hour");
                        minute = (Integer) mDate.get("minute");
                        second = (Integer) mDate.get("second");
                    }
                }
                // The listener also sets initial text
                b.setOnClickListener(new TimeClickListener(b, hour, minute));
                return b;
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
                if (field.getString("string") != null) {
                    s.setPrompt(field.getString("string"));
                } else {
                    s.setPrompt(field.getString("name"));
                }
                // Set value
                if (data != null) {
                    String value = data.getString(name);
                    if (value != null) {
                        for (int i = 0; i < selectValues.size(); i++) {
                            List couple = selectValues.get(i);
                            if (((String)couple.get(0)).equals(value)) {
                                s.setSelection(i + 1);
                                break;
                            }
                        }
                    }
                }
                return s;
            } else if (type.equals("reference")) {
                System.out.println("Reference type not supported yet");
            } else if (type.equals("many2one") || type.equals("one2one")) {
                // Create the widget
                Spinner s = new Spinner(ctx);
                ToOneAdapter adapt = new ToOneAdapter(field.getString("relation"), ctx);
                s.setAdapter(adapt);
                if (field.getString("string") != null) {
                    s.setPrompt(field.getString("string"));
                } else {
                    s.setPrompt(field.getString("name"));
                }
                // Set value
                if (data != null) {
                    Integer value = (Integer) data.get(name);
                    if (value != null) {
                        int iVal = value.intValue();
                        List<Integer> values = adapt.getValues();
                        for (int i = 0; i < values.size(); i++) {
                            if (iVal == values.get(i).intValue()) {
                                s.setSelection(i + 1);
                                break;
                            }
                        }
                    }
                }
                return s;
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

    /** An adapter to provide values to a select widget */
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
            return this.values.get(position - 1);
        }

        public int getCount() {
            return this.labels.size() + 1;
        }

        public View getView(int position, View convertView,
                            ViewGroup parent) {
            String label;
            if (position == 0) {
                // Special "no value" value
                label = "";
            } else {
                label = this.labels.get(position - 1);
            }
            if (convertView instanceof TextView) {
                ((TextView)convertView).setText(label);
                return convertView;
            } else {
                TextView t = new TextView(parent.getContext());
                Resources r = parent.getContext().getResources();
                t.setMinimumHeight((int)r.getDimension(R.dimen.clickable_min_size));
                t.setText(label);
                t.setGravity(Gravity.CENTER_VERTICAL);
                return t;
            }
        }
    }

    /** An adapter to provide values to a x2one widget. It assumes the local
     * database is loaded for the requested model name. */
    public static class ToOneAdapter extends BaseAdapter {

        private List<Integer> values;
        private List<String> labels;

        public ToOneAdapter(String className, Context ctx) {
            this.values = new ArrayList<Integer>();
            this.labels = new ArrayList<String>();
            DataCache db = new DataCache(ctx);
            List<Model> models = db.list(className);
            for (Model m : models) {
                values.add((Integer)m.get("id"));
                labels.add(m.getString("name"));
            }
        }

        /** Get values for selection checking */
        public List<Integer> getValues() {
            return this.values;
        }

        public long getItemId(int position) {
            return position;
        }

        public Object getItem(int position) {
            return this.values.get(position - 1);
        }

        public int getCount() {
            return this.labels.size() + 1;
        }

        public View getView(int position, View convertView,
                            ViewGroup parent) {
            String label;
            if (position == 0) {
                // Special "no value" value
                label = "";
            } else {
                label = this.labels.get(position - 1);
            }
            if (convertView instanceof TextView) {
                ((TextView)convertView).setText(label);
                return convertView;
            } else {
                TextView t = new TextView(parent.getContext());
                Resources r = parent.getContext().getResources();
                t.setMinimumHeight((int)r.getDimension(R.dimen.clickable_min_size));
                t.setText(label);
                t.setGravity(Gravity.CENTER_VERTICAL);
                return t;
            }
        }
    }


    /** Constraint to be used on float and numeric fields to apply
     * the digit attribute limitative behaviour. */
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

    /** Click listener for buttons of date type fields to show
     * a date picking poppup and update the value. */
    private static class DateClickListener
        implements View.OnClickListener, DatePickerDialog.OnDateSetListener {
        
        private Button caller;
        private String format;
        private int year, month, day;

        public DateClickListener(Button caller, String format,
                                 int year, int month, int day) {
            this.caller = caller;
            this.format = format;
            this.year = year;
            this.month = month;
            this.day = day;
            if (this.year == -1 && this.month == -1 && this.day == -1) {
                Calendar c = Calendar.getInstance();
                this.year = c.get(Calendar.YEAR);
                this.month = c.get(Calendar.MONTH) + 1;
                this.day = c.get(Calendar.DAY_OF_MONTH);
            } else {
                this.caller.setText(Formatter.formatDate(this.format, year,
                                                         month, day));
            }
        }
        
        @Override
        public void onClick(View v) {
            Context ctx = v.getContext();
            DatePickerDialog d = new DatePickerDialog(ctx, this, this.year,
                                                      this.month - 1, this.day);
            d.show();
        }

        @Override
        public void onDateSet(DatePicker p, int year, int month, int day) {
            this.caller.setText(Formatter.formatDate(this.format, year,
                                                     month + 1, day));
            this.year = year;
            this.month = month + 1;
            this.day = day;
        }
    }

    /** Click listener for buttons of time type fields to show
     * a time picking poppup and update the value. */
    private static class TimeClickListener
        implements View.OnClickListener, TimePickerDialog.OnTimeSetListener {
        
        private Button caller;
        private String format;
        private int hour, minute;

        public TimeClickListener(Button caller, int hour, int minute) {
            this.caller = caller;
            this.hour = hour;
            this.minute = minute;
            if (this.hour == -1 && this.minute == -1) {
                Calendar c = Calendar.getInstance();
                this.hour = c.get(Calendar.HOUR_OF_DAY);
                this.minute = c.get(Calendar.MINUTE);
            } else {
                this.caller.setText(String.format("%02d:%02d", hour, minute));
            }
        }
        
        @Override
        public void onClick(View v) {
            Context ctx = v.getContext();
            TimePickerDialog t = new TimePickerDialog(ctx, this, this.hour,
                                                      this.minute, true);
            t.show();
        }

        @Override
        public void onTimeSet(TimePicker t, int hour, int minute) {
            this.caller.setText(String.format("%02d:%02d", hour, minute));
            this.hour = hour;
            this.minute = minute;
        }
    }
}