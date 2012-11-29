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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;
import org.tryton.client.models.ModelViewTypes;

/** Tool to build models from arch and fields. */
public class ArchParser {
    
    private static final int TYPE_UNDEFINED = 0;
    private static final int TYPE_TREE = 1;
    private static final int TYPE_FORM = 2;
    private static final int TYPE_GRAPH = 3;
    
    private ModelView view;

    public ArchParser(ModelView view) {
        this.view = view;
    }

    /** Build a tree view. It lists the fields of the ModelView in the order
     * the arch defines and add some presentation attributes. */
    public void buildTree() {
        try {
            // Initialize SAX parser
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            // Set handler that triggers actions on parsing
            ArchHandler handler = new ArchHandler(view);
            xr.setContentHandler(handler);
            // Parse and set result
            xr.parse(new InputSource(new StringReader(view.getArch())));
            view.setTitle(handler.getTitle());
            view.setSubtype(handler.getSubtype());
            view.build(handler.getResult());
            // Merge subviews with discovered subviews
            for (String fieldName : handler.getSubviews().keySet()) {
                ModelViewTypes subviews = view.getSubview(fieldName);
                ModelViewTypes newSubviews = handler.getSubviews().get(fieldName);
                if (subviews == null) {
                    view.setSubviews(fieldName, newSubviews);
                } else {
                    for (String type : newSubviews.getTypes()) {
                        if (subviews.getViewId(type) == 0) {
                            subviews.putViewId(type, newSubviews.getViewId(type));
                        }
                    }
                }
            }
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            e.printStackTrace();
        } catch (org.xml.sax.SAXException e) {
            e.printStackTrace();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private static class ArchHandler extends DefaultHandler {

        private static final int STATE_DONTCARE = 0;
        private static final int STATE_AXIS = 1;

        private ModelView view;
        private Map<String, Model> fields;
        private List<Model> builtFields;
        private int type;
        private String subtype;
        private String title;
        private Map<String, ModelViewTypes> subviews;
        private List<Model> buildStack;
        private int state;

        private Model stackTop() {
            return this.buildStack.get(this.buildStack.size() - 1);
        }
        private void stackPush(Model m) {
            this.buildStack.add(m);
        }
        private void stackPop() {
            this.buildStack.remove(this.buildStack.size() - 1);
        }

        public ArchHandler(ModelView view) {
            this.view = view;
            this.fields = this.view.getFields();
            this.builtFields = new ArrayList<Model>();
            this.subviews = new TreeMap<String, ModelViewTypes>();
            this.buildStack = new ArrayList<Model>();
        }

        public String getTitle() {
            return this.title;
        }

        public String getSubtype() {
            return this.subtype;
        }
        
        public List<Model> getResult() {
            return this.builtFields;
        }

        public Map<String, ModelViewTypes> getSubviews() {
            return this.subviews;
        }

        private void registerSubview(String fieldName, Model fieldModel,
                                     String type, int id) {
            if (this.subviews.get(fieldName) == null) {
                String className = fieldModel.getClassName();
                ModelViewTypes vt = new ModelViewTypes(className);
                this.subviews.put(fieldName, vt);
            }
            ModelViewTypes vt = this.subviews.get(fieldName);
            vt.putViewId(type, id);
        }

        @Override
        public void startDocument() throws SAXException {
            // Set up prior to parsing a doc
        }

        @Override
        public void endDocument() throws SAXException {
            // Clean up after parsing a doc
        }
        
        @Override
        public void startElement(String namespaceURI, String localName,
                                 String qName, Attributes atts)
            throws SAXException {
            // Detect type and title
            if (localName.equals("tree")) {
                if (type == TYPE_UNDEFINED) {
                    this.type = TYPE_TREE;
                    this.title = atts.getValue("string");
                }
            } else if (localName.equals("form")
                       && this.type == TYPE_UNDEFINED) {
                this.type = TYPE_FORM;
                this.title = atts.getValue("string");
            } else if (localName.equals("graph")
                       && this.type == TYPE_UNDEFINED) {
                this.type = TYPE_GRAPH;
                this.title = atts.getValue("string");
                this.subtype = atts.getValue("type");
            }
            // Field
            if (localName.equals("field")) {
                String fieldName = atts.getValue("name");
                if (fieldName != null) {
                    if (this.state == STATE_DONTCARE) {
                        Model fieldModel = this.fields.get(fieldName);
                        if (fieldModel != null) {
                            this.builtFields.add(fieldModel);
                            // Check for subviews
                            String viewIds = atts.getValue("view_ids");
                            String mode = atts.getValue("mode");
                            String modelName = fieldModel.getString("relation");
                            if (mode == null || mode.equals("")) {
                                // No mode, use id as tree view
                                if (viewIds != null && !viewIds.equals("")) {
                                    String[] ids = viewIds.split(",");
                                    int id = new Integer(ids[0]);
                                    // Register subview id
                                    this.registerSubview(fieldName, fieldModel,
                                                         "tree", id);
                                
                                }
                            } else {
                                // One2Many field with modes
                                if (viewIds != null && !viewIds.equals("")) {
                                    String[] ids = viewIds.split(",");
                                    String[] modes = mode.split(",");
                                    for (int i = 0; i < modes.length; i++) {
                                        String type = modes[i];
                                        int id = 0;
                                        if (ids.length > i && !ids[i].equals("")) {
                                            id = new Integer(ids[i]);
                                        }
                                        // Register subview id
                                        this.registerSubview(fieldName, fieldModel,
                                                             type, id);
                                    }
                                }
                            }
                        }
                    } else if (this.state == STATE_AXIS) {
                        Model fieldModel = this.fields.get(fieldName);
                        if (atts.getValue("fill") != null) {
                            fieldModel.set("fill", atts.getValue("fill"));
                        }
                        if (atts.getValue("color") != null) {
                            fieldModel.set("color", atts.getValue("color"));
                        }
                        @SuppressWarnings("unchecked")
                        List<Model> axis = (List<Model>) this.stackTop().get("axis");
                        axis.add(fieldModel);
                    } else {
                        // TODO: parse error
                    }
                }
            } else if (localName.equals("label")) {
                Model labelModel = new Model("label");
                String strName = atts.getValue("name");
                if (strName != null) {
                    labelModel.set("name", strName);
                }
                String string = atts.getValue("string");
                if (string != null) {
                    labelModel.set("string", string);
                }
                this.builtFields.add(labelModel);
            } else if (localName.equals("x")) {
                Model axis = new Model("graph.axis.x");
                axis.set("axis", new ArrayList<Model>());
                this.stackPush(axis);
                this.state = STATE_AXIS;
            } else if (localName.equals("y")) {
                Model axis = new Model("graph.axis.y");
                axis.set("axis", new ArrayList<Model>());
                this.stackPush(axis);
                this.state = STATE_AXIS;
            } else {
                // TODO: parse error
            }
        }

        @Override
        public void characters(char ch[], int start, int length) {
            // no-op
        }

        @Override
        public void endElement(String namespaceURI, String localName,
                               String qName)
            throws SAXException {
            if (localName.equals("x") || localName.equals("y")) {
                this.builtFields.add(this.stackTop());
                this.stackPop();
            }
        }
    }
}