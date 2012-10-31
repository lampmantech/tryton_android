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
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import org.tryton.client.models.Model;
import org.tryton.client.models.ModelView;

/** Tool to build models from arch and fields. */
public class ArchParser {
    
    private static final int TYPE_UNDEFINED = 0;
    private static final int TYPE_TREE = 1;
    private static final int TYPE_FORM = 2;
    private static final int TYPE_GRAPH = 3;
    
    /** Build a tree view. It lists the fields of the ModelView in the order
     * the arch defines and add some presentation attributes. */
    public static void buildTree(ModelView view) {
        try {
            // Initialize SAX parser
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            // Set handler that triggers actions on parsing
            ArchHandler handler = new ArchHandler(view.getFields());
            xr.setContentHandler(handler);
            // Parse and set result
            xr.parse(new InputSource(new StringReader(view.getArch())));
            view.setTitle(handler.getTitle());
            view.build(handler.getResult());
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            e.printStackTrace();
        } catch (org.xml.sax.SAXException e) {
            e.printStackTrace();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private static class ArchHandler extends DefaultHandler {

        private Map<String, Model> fields;
        private List<Model> builtFields;
        private int type;
        private String title;

        public ArchHandler(Map<String, Model> fields) {
            this.fields = fields;
            this.builtFields = new ArrayList<Model>();
        }

        public String getTitle() {
            return this.title;
        }

        public List<Model> getResult() {
            return this.builtFields;
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
            }
            // Field
            if (localName.equals("field")) {
                String strName = atts.getValue("name");
                if (strName != null) {
                    Model fieldModel = this.fields.get(strName);
                    if (fieldModel != null) {
                        this.builtFields.add(fieldModel);
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
        }
    }
}