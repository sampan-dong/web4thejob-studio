package org.web4thejob.studio.controller.impl;

import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.XPathContext;
import org.web4thejob.studio.controller.AbstractController;
import org.web4thejob.studio.controller.ControllerEnum;
import org.web4thejob.studio.message.Message;
import org.web4thejob.studio.message.MessageEnum;
import org.web4thejob.studio.support.StudioUtil;
import org.zkoss.web.servlet.http.Encodes;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.*;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.util.Assert.notNull;
import static org.web4thejob.studio.controller.ControllerEnum.DESIGNER_CONTROLLER;
import static org.web4thejob.studio.message.MessageEnum.*;
import static org.web4thejob.studio.support.StudioUtil.*;
import static org.zkoss.lang.Generics.cast;

/**
 * Created by Veniamin on 10/5/2014.
 */
public class DesignerController extends AbstractController {
    public static final String PARAM_HINT = "w4tjstudio_hint";
    public static final String PARAM_MESSAGE = "w4tjstudio_message";
    public static final String PARAM_WORK_FILE = "w4tjstudio_workfile";
    public static final String PARAM_PRODUCTION_FILE = "w4tjstudio_prodfile";
    public static final String PARAM_XPATH = "w4tjstudio_xpath";
    private static final String PARAM_TIMESTAMP = "w4tjstudio_timestamp";
    @Wire
    private Iframe canvasHolder;
    @Wire
    private Tabbox views;
    @Wire
    private Tab canvasView;
    @Wire
    private Tab outlineView;
    @Wire
    private Tab codeView;

    private Element selection;
    private Menupopup actionsPopup;

    @Override
    public ControllerEnum getId() {
        return DESIGNER_CONTROLLER;
    }

    @Listen("onWidgetSelected=#designer")
    public void onWidgetSelected(Event event) throws InterruptedException {
        String target = (String) ((Map) event.getData()).get("target");
        notNull(target);
        try {
            selection = getElementByUuid(target);
            publish(COMPONENT_SELECTED, selection);
        } catch (Exception e) {
            publish(COMPONENT_SELECTED);
        }
    }

    @Listen("onActionsClicked=#designer")
    public void onActionsClicked(Event event) {
        if (actionsPopup == null) {
            actionsPopup = new Menupopup();
            actionsPopup.setId("actionsMenupopup");
            actionsPopup.setMold("w4tjstudio");
            actionsPopup.setSclass("custom-menupopup");
            actionsPopup.setPage(event.getTarget().getPage());
        } else {
            actionsPopup.getChildren().clear();
        }

        int x = Integer.valueOf(((Map) event.getData()).get("right").toString());
        actionsPopup.open("auto", ((Map) event.getData()).get("top").toString() + "px");
        actionsPopup.setStyle("right:" + x + "px");


        if (selection == null) {
            final Menuitem nosel = new Menuitem("No current selection");
            nosel.setParent(actionsPopup);
            nosel.setDisabled(true);
            return;
        }


        final Menuitem id = new Menuitem(selection.getLocalName() + " [" + selection.getAttributeValue("uuid") + "]");
        id.setParent(actionsPopup);
        id.setDisabled(true);

        new Menuseparator().setParent(actionsPopup);

        final Menuitem cut = new Menuitem("Cut");
        cut.setParent(actionsPopup);
        cut.setIconSclass("z-icon-cut");
        cut.setAttribute("target", selection);
        cut.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                Element element = (Element) cut.getAttribute("target");
                element.detach();

                Element cutElement = (Element) element.copy();
                Executions.getCurrent().getDesktop().setAttribute("cutcopy", cutElement);
                publish(MessageEnum.COMPONENT_SELECTED);
                publish(MessageEnum.COMPONENT_DETACHED, element);
            }
        });


        final Menuitem copy = new Menuitem("Copy");
        copy.setParent(actionsPopup);
        copy.setIconSclass("z-icon-copy");
        copy.setAttribute("target", selection);
        copy.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                Element element = (Element) copy.getAttribute("target");
                Executions.getCurrent().getDesktop().setAttribute("cutcopy", element);
            }
        });

        new Menuseparator().setParent(actionsPopup);

        final Menuitem pasteInside = new Menuitem("Paste inside");
        pasteInside.setParent(actionsPopup);
        pasteInside.setIconSclass("z-icon-paste");
        pasteInside.setAttribute("target", selection);
        pasteInside.setDisabled(!Executions.getCurrent().getDesktop().hasAttribute("cutcopy"));
        pasteInside.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                Element parent = (Element) pasteInside.getAttribute("target");
                Element child = (Element) ((Element) Executions.getCurrent().getDesktop().getAttribute("cutcopy")).copy();
                if (acceptsChild(parent, child)) {
                    parent.appendChild(child);
                    publish(COMPONENT_SELECTED, child);
                    publish(EVALUATE_ZUL);
                }
            }
        });


        final Menuitem pasteBefore = new Menuitem("Paste before");
        pasteBefore.setParent(actionsPopup);
        pasteBefore.setIconSclass("z-icon-paste");
        pasteBefore.setAttribute("target", selection);
        pasteBefore.setDisabled(!Executions.getCurrent().getDesktop().hasAttribute("cutcopy"));
        pasteBefore.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                Element before = ((Element) pasteBefore.getAttribute("target"));
                Element parent = (Element) before.getParent();
                int pos = 0;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    if (parent.getChild(i).equals(before)) {
                        pos = i;
                        break;
                    }
                }
                Element child = (Element) ((Element) Executions.getCurrent().getDesktop().getAttribute("cutcopy")).copy();
                if (acceptsChild(parent, child)) {
                    parent.insertChild(child, pos);
                    publish(COMPONENT_SELECTED, child);
                    publish(EVALUATE_ZUL);
                }
            }
        });


        new Menuseparator().setParent(actionsPopup);

        final Menuitem detach = new Menuitem("Detach");
        detach.setParent(actionsPopup);
        detach.setIconSclass("z-icon-trash-o");
        detach.setAttribute("target", selection);
        detach.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                Element element = (Element) detach.getAttribute("target");
                element.detach();
                publish(MessageEnum.COMPONENT_SELECTED);
                publish(MessageEnum.COMPONENT_DETACHED, element);
            }
        });


    }

    @Listen("onCanvasAddition=#designer")
    public void onCanvasAddition(Event event) {
        publish(COMPONENT_ADDED, event.getData());
        clearCanvasBusy(null);
    }

    @Listen("onCanvasSucceeded=#designer")
    public void onCanvasSucceeded(Event event) {
//        Clients.clearBusy();
        clearAlerts();

        Map<String, String> data = cast(event.getData());

        String message = null, workFile = null;
        if (data != null) {
            message = data.get(PARAM_MESSAGE);
            workFile = data.get(PARAM_WORK_FILE);
        }

        if (message == null) {
            //this is the initial canvas load
            publish(RESET, workFile);
        } else {
            MessageEnum id = MessageEnum.valueOf(message);
            switch (id) {
                case EVALUATE_ZUL:
                    data.remove(PARAM_MESSAGE);
                    publish(ZUL_EVAL_SUCCEEDED, data);
                    break;
            }
        }
    }

    @Listen("onCanvasFailed=#designer")
    public void onCanvasFailed(Event event) {
        Clients.clearBusy();
        clearAlerts();

        Map<String, String> data = cast(event.getData());
        String message = null;
        if (data != null) {
            message = data.get(PARAM_MESSAGE);
        }
        if (message == null) {
            publish(ZUL_EVAL_FAILED, "Unspecified error occurred");
            return;
        }

        MessageEnum id = MessageEnum.valueOf(message);
        switch (id) {
            case EVALUATE_ZUL:
                publish(ZUL_EVAL_FAILED, data.get("exception"));
                break;
        }
    }

    @Override
    public void process(Message message) {
        switch (message.getId()) {
            case EVALUATE_ZUL:
                Clients.showBusy("Parsing your zul...");

                Map<String, String> params = new LinkedHashMap<>();

                //1. Message id
                params.put(PARAM_MESSAGE, EVALUATE_ZUL.name());

                //2. Possible Hints
                if (message.getData() != null) {
                    if (message.getData() instanceof Map) {
                        params.putAll(message.<Map<String, String>>getData());
                    } else {
                        params.put(PARAM_HINT, message.getData().toString());
                    }
                }


                //3. Timestamp of the request to prevent caching (redundant?)
                params.put(PARAM_TIMESTAMP, Long.valueOf(new Date().getTime()).toString());

                //4. Working file, this file will be read by CanvasUiFactory.getPageDefinition()
                params.put(PARAM_WORK_FILE, StudioUtil.buildWorkingFile(StudioUtil.getCode()).getAbsolutePath());
                try {
                    String src = getCanvasHolderURI();
                    canvasHolder.removeAttribute("src");
                    canvasHolder.setSrc(Encodes.setToQueryString(new StringBuffer(src), params).toString());
                    Clients.evalJavaScript("w4tjStudioDesigner.monitorCanvasHealth()");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case XML_EVAL_SUCCEEDED:
                publish(EVALUATE_ZUL);
                break;
            case ZUL_EVAL_SUCCEEDED:
                canvasView.setDisabled(false);
                outlineView.setDisabled(false);
                if (message.getData(PARAM_HINT) == null) { //no hint, parse zul was clicked
                    Clients.clearBusy();
                    Clients.evalJavaScript("w4tjStudioDesigner.codeSuccessEffect()");
                } else if (message.getData(PARAM_HINT).equals(MessageEnum.COMPONENT_ADDED.name())) {
                    String xpath = message.getData(PARAM_XPATH);
                    if (xpath != null) {
                        Nodes nodes = getCode().query(xpath, XPathContext.makeNamespaceContext(getCode().getRootElement()));
                        if (nodes.size() != 1) break;
                        selection = (Element) nodes.get(0);
                        publish(COMPONENT_SELECTED, selection);
                        Clients.clearBusy();
                    }
                }
                break;
            case XML_EVAL_FAILED:
                canvasView.setDisabled(true);
                outlineView.setDisabled(true);
                Clients.clearBusy();
                clearAlerts();
                showError((Exception) message.getData(), false);
                break;
            case ZUL_EVAL_FAILED:
                if (!codeView.isSelected()) {
                    codeView.setSelected(true);
                    Clients.evalJavaScript("w4tjStudioDesigner.refreshCode()"); //setSelected does not
                    // trigger onSelect on client ?!?
                }
                canvasView.setDisabled(true);
                outlineView.setDisabled(true);
                if (message.getData() != null) {
                    showError(message.getData().toString(), false);
                }
//                publish(COMPONENT_SELECTED); //deselect
                break;
            case CODE_CHANGED:
                codeView.setSelected(true);
                canvasView.setDisabled(true);
                outlineView.setDisabled(true);
                break;
            case COMPONENT_SELECTED:
                Element newSelection = message.getData();
                if ((newSelection != null && selection != null)) {
                    Clients.evalJavaScript("w4tjStudioDesigner.selectCanvasWidget('" + newSelection.getAttributeValue("uuid") + "')");
                } else if (newSelection == null && selection != null)
                    Clients.evalJavaScript("w4tjStudioDesigner.selectCanvasWidget()");

                selection = newSelection;
                break;
            case NON_ZK_PAGE_VISITED:
                codeView.setDisabled(true);
                outlineView.setDisabled(true);
                break;
            case ZK_PAGE_VISITED:
                codeView.setDisabled(false);
                outlineView.setDisabled(false);
                break;
            case RESET:
                selection = null;
                break;
            default:
                break;
        }


    }

    @Listen("onParseZulClicked=#designer")
    public void onParseZulClicked() {
        publish(EVALUATE_XML);
    }

    @Listen("onCanvasHang=#designer")
    public void onCanvasHang() {
        Clients.clearBusy();
        publish(ZUL_EVAL_FAILED);
    }

    @Listen("onURIChange=#canvasHolder")
    public void onCanvasURIChanged(URIEvent event) {
        canvasHolder.setAttribute("src", event.getURI());
    }

    private String getCanvasHolderURI() {
        String src = (String) canvasHolder.getAttribute("src");
        if (src == null) {
            src = canvasHolder.getSrc();
        }

        try {
            src = Encodes.removeFromQueryString(src, PARAM_MESSAGE);
            src = Encodes.removeFromQueryString(src, PARAM_HINT);
            src = Encodes.removeFromQueryString(src, PARAM_WORK_FILE);
            src = Encodes.removeFromQueryString(src, PARAM_TIMESTAMP);
            src = Encodes.removeFromQueryString(src, PARAM_PRODUCTION_FILE);
            src = Encodes.removeFromQueryString(src, PARAM_XPATH);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return src;
    }

    @Listen("onNonZKPage=#designer")
    public void onNonZKPage() {
        publish(NON_ZK_PAGE_VISITED);
    }

    @Listen("onZKPage=#designer")
    public void onZKPage() {
        Clients.clearBusy();
        publish(ZK_PAGE_VISITED);
    }

    @Listen("onSelect=#views")
    public void onTabSelected(SelectEvent e) {
        Tab tab = (Tab) e.getTarget();
        if (tab.equals(canvasView)) {
            publish(DESIGNER_ACTIVATED);
        } else if (tab.equals(outlineView)) {
            publish(OUTLINE_ACTIVATED);
        } else if (tab.equals(codeView)) {
            publish(CODE_ACTIVATED);
        }

    }
}
