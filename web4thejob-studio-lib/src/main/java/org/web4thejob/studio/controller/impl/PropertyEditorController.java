/*
 * Copyright 2014 Veniamin Isaias
 *
 * This file is part of Web4thejob Studio.
 *
 * Web4thejob Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Web4thejob Studio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Web4thejob Studio.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.web4thejob.studio.controller.impl;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.XPathContext;
import org.web4thejob.studio.controller.AbstractController;
import org.web4thejob.studio.controller.ControllerEnum;
import org.web4thejob.studio.message.Message;
import org.web4thejob.studio.message.MessageEnum;
import org.web4thejob.studio.support.ChildDelegate;
import org.web4thejob.studio.support.StudioUtil;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.*;
import org.zkoss.zk.ui.metainfo.ComponentDefinition;
import org.zkoss.zk.ui.metainfo.LanguageDefinition;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;
import org.zkoss.zul.impl.InputElement;

import java.util.*;

import static org.apache.commons.lang3.Validate.notNull;
import static org.web4thejob.studio.controller.ControllerEnum.PROPERTY_EDITOR_CONTROLLER;
import static org.web4thejob.studio.message.MessageEnum.ATTRIBUTE_CHANGED;
import static org.web4thejob.studio.support.StudioUtil.*;
import static org.web4thejob.studio.support.ZulXsdUtil.*;

/**
 * Created by e36132 on 16/5/2014.
 */
public class PropertyEditorController extends AbstractController {
    private static final OnOKHandler OK_HANDLER = new OnOKHandler();
    private static final OnCodeEditorHandler CODE_EDITOR_HANDLER = new OnCodeEditorHandler();
    private final SaveProperties SAVE_CHANGES_HANDLER = new SaveProperties();
    @Wire
    private Tabpanel properties;
    @Wire
    private Tabpanel events;
    @Wire
    private Tabpanel source;
    @Wire
    private Html editorSelection;
    private Element selection;

    private static boolean isEvent(String name) {
        return name.startsWith("on") && name.length() > 2 && name.codePointAt(2) >= "A".codePointAt(0);
    }

    @Override
    public ControllerEnum getId() {
        return PROPERTY_EDITOR_CONTROLLER;
    }

    public Element getSelection() {
        return selection;
    }

    @Override
    public void process(Message message) {
        switch (message.getId()) {
            case COMPONENT_SELECTED:
                Element element = message.getData();
                if (element != null) {
                    selection = element;
                    try {
                        refresh();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    clear();
                }
                Clients.evalJavaScript("zAu.cmd0.clearBusy(zk('$propertyeditor').$())");
                break;
            case RESET:
                clear();
                break;
            case XML_EVAL_FAILED:
                clear();
                break;
            case ZUL_EVAL_FAILED:
                clear();
                break;
            case ZUL_EVAL_SUCCEEDED:
                if (selection != null) {
                    String xpath = getXPath(selection);
                    Nodes nodes = getCode().query(xpath, XPathContext.makeNamespaceContext(selection));
                    if (nodes.size() != 1) break;
                    if (selection.getQualifiedName().equals(((Element) nodes.get(0)).getQualifiedName())) {
                        publish(MessageEnum.COMPONENT_SELECTED, nodes.get(0));
                    }
                }
                break;
        }
    }

    public void clear() {
        editorSelection.setContent(null);
        properties.getChildren().clear();
        properties.getLinkedTab().setVisible(true);
        properties.getLinkedTab().setSelected(true);
        events.getChildren().clear();
        events.getLinkedTab().setVisible(true);
        source.getChildren().clear();
        source.getLinkedTab().setVisible(false);

        properties.setAttribute("prevSelection", null);
        new Include("~./include/nocurrentselection.zul").setParent(properties);
        new Include("~./include/nocurrentselection.zul").setParent(events);
    }

    public void refresh() throws Exception {
        events.getChildren().clear();
        source.getChildren().clear();

        Element prevSelection = (Element) properties.getAttribute("prevSelection");
        properties.setAttribute("prevSelection", selection);

        if (selection == null || prevSelection == null) {
            properties.getChildren().clear();
            editorSelection.setContent(null);
            if (selection == null) return;
        }

        if (prevSelection != null && !prevSelection.getLocalName().equals(selection.getLocalName())) {
            properties.getChildren().clear();
        }

        editorSelection.setContent(describeElement(selection));

        if ("attribute".equals(selection.getLocalName())) {
            properties.getLinkedTab().setVisible(false);
            events.getLinkedTab().setVisible(false);
            source.getLinkedTab().setVisible(true);
            source.getLinkedTab().setSelected(true);

            if ("attribute".equals(selection.getLocalName())) {
                boolean isServerSide = true;
                Attribute name = selection.getAttribute("name");
                if (name == null) {
                    String clientNS = StudioUtil.getClientNamespace((org.web4thejob.studio.dom.Element) selection);
                    name = selection.getAttribute("name", clientNS);
                    isServerSide = false;
                }

                source.getLinkedTab().setLabel(isServerSide ? "Java" : "Javascript");
                Map<String, Object> data = new HashMap<>();
                data.put("element", selection.getParent());
                data.put("size", "xs");
                data.put("mode", isServerSide ? "text/x-java" : "javascript");
                data.put("event", name.getValue());
                Executions.getCurrent().createComponents("~./include/codemirror.zul", source, data);
            }
        } else if ("zscript".equals(selection.getLocalName())) {
            properties.getLinkedTab().setVisible(false);
            events.getLinkedTab().setVisible(false);
            source.getLinkedTab().setVisible(true);
            source.getLinkedTab().setSelected(true);
            source.getLinkedTab().setLabel("Java");

            Map<String, Object> data = new HashMap<>();
            data.put("element", selection);
            data.put("size", "xs");
            data.put("mode", "text/x-java");
            Executions.getCurrent().createComponents("~./include/codemirror.zul", source, data);

        } else if (!isBaseGroupElement(selection) && !isNative(selection)) {
            properties.getLinkedTab().setVisible(true);
            events.getLinkedTab().setVisible(true);
            source.getLinkedTab().setVisible(false);

            Tab tab = properties.getTabbox().getSelectedTab();
            refreshComponent();

            String tag = selection.getLocalName();

            if (tag.equals("style") || tag.equals("script") || tag.equals("html")) {
                Map<String, Object> data = new HashMap<>();
                data.put("element", selection);
                data.put("size", "xs");

                source.getLinkedTab().setVisible(true);
                switch (selection.getLocalName()) {
                    case "style":
                        data.put("mode", "css");
                        source.getLinkedTab().setLabel("CSS");
                        break;
                    case "script":
                        data.put("mode", "javascript");
                        source.getLinkedTab().setLabel("Javascript");
                        break;
                    case "html":
                        data.put("mode", "text/html");
                        source.getLinkedTab().setLabel("HTML");
                        break;
                }
                Executions.getCurrent().createComponents("~./include/codemirror.zul", source, data);
            }

            if (tab == null || (tab.isSelected() && !tab.isVisible()))
                properties.getLinkedTab().setSelected(true);

        } else {
            clear();
            editorSelection.setContent(describeElement(selection));
        }


    }

    private void refreshComponent() {
        SortedMap<String, SortedSet<Element>> propsMap = getWidgetDescription(selection.getLocalName());
        refreshComponentProperties(propsMap);
        refreshComponentEvents(propsMap);
    }

    private void refreshComponentEvents(SortedMap<String, SortedSet<Element>> propsMap) {
        Grid grid = new Grid();
        grid.setParent(events);
        grid.setVflex("true");
        grid.setSpan(true);
        new Columns().setParent(grid);
        grid.getColumns().setSizable(true);
        new Column("Name").setParent(grid.getColumns());
        Column colServer = new Column("Server");
        colServer.setParent(grid.getColumns());
        colServer.setAlign("center");
        colServer.setWidth("60px");
        Column colClient = new Column("Client");
        colClient.setParent(grid.getColumns());
        colClient.setAlign("center");
        colClient.setWidth("60px");

        new Rows().setParent(grid);

        for (String group : propsMap.keySet()) {
            for (Element property : propsMap.get(group)) {
                String propertyName = property.getAttributeValue("name");
                if (isEvent(propertyName)) {
                    Row row = new Row();
                    row.setParent(grid.getRows());

                    Label name = new Label(propertyName);
                    name.setParent(row);

                    Button btn = new Button();
//                    btn.setMold("bs");
                    btn.setParent(row);
                    btn.setIconSclass("z-icon-bolt");
                    btn.setAttribute("mode", "text/x-java");
                    btn.setAttribute("event", propertyName);
                    btn.setAttribute("element", selection);
                    btn.addEventListener(Events.ON_CLICK, CODE_EDITOR_HANDLER);
                    if (getEventCodeNode(selection, propertyName, true) != null) {
                        btn.setZclass("btn btn-xs btn-primary");
                    } else {
                        btn.setZclass("btn btn-xs btn-default");
                    }
                    btn.setWidth("32px");
                    btn.setHeight("25px");

                    btn = new Button();
//                    btn.setMold("bs");
                    btn.setParent(row);
                    btn.setIconSclass("z-icon-bolt");
                    btn.setAttribute("mode", "javascript");
                    btn.setAttribute("event", propertyName);
                    btn.setAttribute("element", selection);
                    btn.addEventListener(Events.ON_CLICK, CODE_EDITOR_HANDLER);
                    if (getEventCodeNode(selection, propertyName, false) != null) {
                        btn.setZclass("btn btn-xs btn-primary");
                    } else {
                        btn.setZclass("btn btn-xs btn-default");
                    }
                    btn.setWidth("32px");
                    btn.setHeight("25px");
                }
            }
        }
    }


    private Groupbox buildGroupbox(String group) {
        Groupbox groupbox = new Groupbox();
        groupbox.setAttribute("group", group);
        groupbox.setOpen(selection.getLocalName().equals(group));
        groupbox.setMold("3d");
        String groupName = group.replaceAll("AttrGroup", "");
        groupName = groupName.replaceAll("Component", "");
        Caption caption = new Caption(groupName);
        caption.setParent(groupbox);

        Grid grid = new Grid();
        grid.setParent(groupbox);
        grid.setSpan(true);
        new Columns().setParent(grid);
        new Column().setParent(grid.getColumns());
        new Column().setParent(grid.getColumns());
        new Rows().setParent(grid);
        ((Column) grid.getColumns().getFirstChild()).setWidth("160px");

        return groupbox;
    }

    private Groupbox findGroup(String group) {
        for (Component comp : properties.getChildren()) {
            if (group.equals(comp.getAttribute("group"))) {
                return (Groupbox) comp;
            }
        }
        return null;
    }

    private void refreshComponentProperties(SortedMap<String, SortedSet<Element>> propsMap) {
        ComponentDefinition componentDefinition = getDefinitionByTag(selection.getLocalName());
        List<Row> bindings = new ArrayList<>(1);


        for (String group : propsMap.keySet()) {
            Groupbox groupbox = findGroup(group);
            Row row;
            if (groupbox == null) {
                groupbox = buildGroupbox(group);
            }
            Grid grid = (Grid) groupbox.getLastChild();

            int num = 0;
            for (Element property : propsMap.get(group)) {
                String propertyName = property.getAttributeValue("name");
                if (isEvent(propertyName)) continue;

                Object val = null;
                Attribute attribute = selection.getAttribute(propertyName);
                if (attribute != null) {
                    val = attribute.getValue();
                }

                InputElement editor = findEditor(selection, propertyName);
                if (editor == null) {
                    row = new Row();
                    row.setAttribute("property", propertyName);
                    row.setWidgetAttribute("w4tjstudio-property", propertyName);
                    row.setParent(grid.getRows());

                    Label name = new Label(propertyName);
                    name.setParent(row);

                    name.setValue(propertyName);

                    editor = createEditor(componentDefinition, property, val);
                    editor.setAttribute("property", propertyName);
                    editor.setAttribute("element", selection);
                    editor.addEventListener(Events.ON_OK, OK_HANDLER);
                    editor.setParent(row);
                } else {
                    editor.setAttribute("element", selection);
                    editor.setRawValue(val);
                    row = (Row) editor.getParent();
                }

                if (val instanceof String) {
                    String s = (String) val;
                    if (s.contains("@bind(") || s.contains("@load(") || s.contains("@save(")) {
                        Row brow = (Row) row.clone();
                        ((Label) brow.getFirstChild()).setSclass("label label-primary");
                        String bval = ((InputElement) brow.getLastChild()).getRawValue().toString();
                        brow.getLastChild().detach();
                        new Html("<span class=\"label label-success z-label\">" + bval + "</span>").setParent(brow);
                        bindings.add(brow);
                    }
                }

                num++;
            }

            if (num > 0) {
                groupbox.setParent(properties);
            }//else discard

        }


        if (!bindings.isEmpty()) {
            Groupbox groupbox = findGroup("bindings");
            Grid grid;
            if (groupbox == null) {
                groupbox = buildGroupbox("bindings");
                properties.insertBefore(groupbox, properties.getFirstChild());
                groupbox.setOpen(true);
            }
            grid = (Grid) groupbox.getLastChild();
            grid.getRows().getChildren().clear();
            for (Row row : bindings) {
                row.setParent(grid.getRows());
            }
        } else {
            Groupbox groupbox = findGroup("bindings");
            if (groupbox != null) groupbox.detach();
        }

        Clients.evalJavaScript("w4tjStudioDesigner.decoratePropertyCaptions();");
    }


    private InputElement createEditor(ComponentDefinition definition, Element element, Object value) {

        //check for mold
        if ("mold".equals(element.getAttributeValue("name"))) {
            Combobox combobox = new Combobox();
            combobox.setHflex("true");
            combobox.addEventListener(Events.ON_CHANGE, SAVE_CHANGES_HANDLER);
            for (String moldName : definition.getMoldNames()) {
                if ("w4tjstudio".equals(moldName)) continue;

                Comboitem comboitem = new Comboitem(moldName);
                comboitem.setParent(combobox);

                if (moldName.equals(value)) {
                    combobox.setSelectedItem(comboitem);
                }
            }
            return combobox;
        }

        //check if constraint
        String type = element.getAttributeValue("type");
        List<String> restrictions = getConstraintForAttributeType(type);
        if (!restrictions.isEmpty()) {
            Combobox combobox = new Combobox();
            combobox.setHflex("true");
            combobox.addEventListener(Events.ON_CHANGE, SAVE_CHANGES_HANDLER);

            for (String enumeration : restrictions) {
                Comboitem comboitem = new Comboitem(enumeration);
                comboitem.setParent(combobox);

                if (enumeration.equals(value)) {
                    combobox.setSelectedItem(comboitem);
                }
            }

            if (combobox.getSelectedItem() == null) {
                combobox.setValue((String) value);
            }

            return combobox;
        }

        Textbox textbox = new Textbox();
        textbox.setValue(value != null ? value.toString() : "");
        textbox.setHflex("true");
        textbox.addEventListener(Events.ON_CHANGE, SAVE_CHANGES_HANDLER);
        return textbox;


//        return new Label((String) value);
    }

    public void SaveChanges(Element element, String propertyName, String value, String oldValue) {
        InputElement editor = findEditor(element, propertyName);
        Events.sendEvent(new InputEvent(Events.ON_CHANGE, editor, value, oldValue));
        editor.setRawValue(value);
    }

    private InputElement findEditor(final Element element, final String propertyName) {
        final InputElement[] editor = new InputElement[]{null};

        traverseChildren(properties, null, new ChildDelegate<Component>() {
            @Override
            public void onChild(Component child, Map<String, Object> params) {
                if (editor[0] != null) return;

                Element attached = (Element) child.getAttribute("element");
                if (attached != null && element.getLocalName().equals(attached.getLocalName()) && propertyName.equals
                        (child.getAttribute
                                ("property"))) {
                    editor[0] = (InputElement) child;
                }
            }
        });

        return editor[0];
    }

    private static class OnOKHandler implements EventListener<KeyEvent> {

        @Override
        public void onEvent(KeyEvent event) throws Exception {
            //do nothing, needed only for Enter to fire onChange
        }
    }

    private static class OnCodeEditorHandler implements EventListener<MouseEvent> {

        @Override
        public void onEvent(MouseEvent event) throws Exception {
            Map<String, Object> args = new HashMap<>();
            args.put("mode", event.getTarget().getAttribute("mode"));
            args.put("element", event.getTarget().getAttribute("element"));
            args.put("event", event.getTarget().getAttribute("event"));
            Executions.createComponents("~./include/codedialog.zul", null, args);
        }
    }

    private class SaveProperties implements EventListener<InputEvent> {
        @Override
        public void onEvent(InputEvent event) throws Exception {
            Element selected = (Element) event.getTarget().getAttribute("element");
            String propertyName = event.getTarget().getAttribute("property").toString();
            String uuid = selected.getAttributeValue("uuid");
            notNull(uuid);
//            Component component = null;
//            if (uuid != null) {
//                component = getCanvasComponentByUuid(uuid);
//            }

            String value = event.getValue();
//            Object previousValue = event.getPreviousValue();

            if (value != null) {
                value = value.trim();
            } else {
                value = "";
            }

            boolean isBoolean = "booleanType".equals(getTypeOfAttribute(selected.getLocalName(), propertyName));
            boolean isDefaultValue = false;

            LanguageDefinition languageDefinition = LanguageDefinition.getByExtension("zul");
            ComponentDefinition componentDefinition = languageDefinition.getComponentDefinitionIfAny(selected.getLocalName());
            if (componentDefinition != null) {
                Component component = componentDefinition.newInstance(null, null);
                isDefaultValue = component != null && isDefaultValueForProperty(component, propertyName, value,
                        isBoolean);
            }


            Attribute attribute = selected.getAttribute(propertyName);
            if (attribute == null && value.length() > 0) {
                selected.addAttribute(new Attribute(propertyName, value));
            } else if (attribute != null && (value.length() == 0 || isDefaultValue)) {
                selected.removeAttribute(attribute);
            } else if (attribute != null && value.length() > 0) {

                if (value.equals(attribute.getValue())) return;

                if (!isDefaultValue) {
                    attribute.setValue(value);
                } else {
                    selected.removeAttribute(attribute);
                }
            }

            Clients.evalJavaScript("w4tjStudioDesigner.highlight('" + event.getTarget().getParent().getUuid() + "');");

            publish(ATTRIBUTE_CHANGED);

        }
    }


}

