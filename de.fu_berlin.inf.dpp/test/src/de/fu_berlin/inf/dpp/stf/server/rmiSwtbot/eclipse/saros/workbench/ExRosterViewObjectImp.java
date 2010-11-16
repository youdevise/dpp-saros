package de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.saros.workbench;

import java.rmi.RemoteException;

import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.stf.server.SarosConstant;
import de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.conditions.SarosConditions;
import de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.EclipseObject;
import de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.saros.noGUI.SarosStateObjectImp;
import de.fu_berlin.inf.dpp.ui.RosterView;

/**
 * This implementation of {@link ExRosterViewObject}
 * 
 * @author Lin
 */
public class ExRosterViewObjectImp extends EclipseObject implements
    ExRosterViewObject {

    // public static RosterViewObjectImp classVariable;

    private static transient ExRosterViewObjectImp self;

    /**
     * {@link ExRosterViewObjectImp} is a singleton, but inheritance is
     * possible.
     */
    public static ExRosterViewObjectImp getInstance() {
        if (self != null)
            return self;
        self = new ExRosterViewObjectImp();
        return self;
    }

    public void openRosterView() throws RemoteException {
        if (!isRosterViewOpen())
            viewO.openViewById(SarosConstant.ID_ROSTER_VIEW);
    }

    public boolean isRosterViewOpen() throws RemoteException {
        return viewO.isViewOpen(SarosConstant.VIEW_TITLE_ROSTER);
    }

    public void setFocusOnRosterView() throws RemoteException {
        viewO.setFocusOnViewByTitle(SarosConstant.VIEW_TITLE_ROSTER);
    }

    public void closeRosterView() throws RemoteException {
        viewO.closeViewById(SarosConstant.ID_ROSTER_VIEW);
    }

    public void xmppDisconnect() throws RemoteException {
        if (isConnectedByXMPP()) {
            clickTBDisconnectInRosterView();
            waitUntilDisConnected();
            // sleep(200);
        }
    }

    public SWTBotTreeItem selectBuddy(String contact) throws RemoteException {
        return viewO.selectTreeWithLabelsInView(
            SarosConstant.VIEW_TITLE_ROSTER, "Buddies", contact);
    }

    public boolean isBuddyExist(String contact) throws RemoteException {
        SWTBotTree tree = viewO.getTreeInView(SarosConstant.VIEW_TITLE_ROSTER);
        return treeO.isTreeItemWithMatchTextExist(tree, SarosConstant.BUDDIES,
            contact + ".*");
    }

    public boolean isConnectedByXmppGuiCheck() throws RemoteException {
        try {
            openRosterView();
            setFocusOnRosterView();
            SWTBotToolbarButton toolbarButton = viewO
                .getToolbarButtonWithTooltipInView(
                    SarosConstant.VIEW_TITLE_ROSTER,
                    SarosConstant.TOOL_TIP_TEXT_DISCONNECT);
            return (toolbarButton != null && toolbarButton.isVisible());
        } catch (WidgetNotFoundException e) {
            return false;
        }
    }

    /**
     * This method returns true if {@link SarosStateObjectImp} and the GUI
     * {@link RosterView} having the connected state.
     */
    public boolean isConnectedByXMPP() throws RemoteException {
        return exStateO.isConnectedByXMPP() && isConnectedByXmppGuiCheck();
    }

    public void clickTBAddANewContactInRosterView() throws RemoteException {
        openRosterView();
        setFocusOnRosterView();
        viewO.clickToolbarButtonWithTooltipInView(
            SarosConstant.VIEW_TITLE_ROSTER,
            SarosConstant.TOOL_TIP_TEXT_ADD_A_NEW_CONTACT);
    }

    /**
     * Roster must be open
     */
    public void clickTBConnectInRosterView() throws RemoteException {
        openRosterView();
        setFocusOnRosterView();
        viewO.clickToolbarButtonWithTooltipInView(
            SarosConstant.VIEW_TITLE_ROSTER,
            SarosConstant.TOOL_TIP_TEXT_CONNECT);
    }

    /**
     * Roster must be open
     */
    public boolean clickTBDisconnectInRosterView() throws RemoteException {
        openRosterView();
        setFocusOnRosterView();
        return viewO.clickToolbarButtonWithTooltipInView(
            SarosConstant.VIEW_TITLE_ROSTER,
            SarosConstant.TOOL_TIP_TEXT_DISCONNECT) != null;
    }

    public void waitUntilConnected() throws RemoteException {
        waitUntil(SarosConditions.isConnect(bot));
    }

    public void waitUntilDisConnected() throws RemoteException {
        waitUntil(SarosConditions.isDisConnected(bot));
    }

    public void addContact(JID jid) throws RemoteException {
        if (!hasContactWith(jid)) {
            openRosterView();
            setFocusOnRosterView();
            clickTBAddANewContactInRosterView();
            windowO.waitUntilShellActive(SarosConstant.SHELL_TITLE_NEW_CONTACT);
            // activateShellWithText(SarosConstant.SHELL_TITLE_NEW_CONTACT);
            bot.textWithLabel(SarosConstant.TEXT_LABEL_JABBER_ID).setText(
                jid.getBase());
            basicO.waitUntilButtonIsEnabled(SarosConstant.BUTTON_FINISH);
            bot.button(SarosConstant.BUTTON_FINISH).click();
        }
    }

    public boolean hasContactWith(JID jid) throws RemoteException {
        return exStateO.hasContactWith(jid) && isBuddyExist(jid.getBase());
    }

    /**
     * Remove given contact from Roster, if contact was added before.
     */
    public void deleteContact(JID jid) throws RemoteException {
        if (!hasContactWith(jid))
            return;
        try {
            viewO.clickContextMenuOfTreeInView(SarosConstant.VIEW_TITLE_ROSTER,
                SarosConstant.CONTEXT_MENU_DELETE, SarosConstant.BUDDIES,
                jid.getBase());
            windowO
                .waitUntilShellActive(SarosConstant.SHELL_TITLE_CONFIRM_DELETE);
            exWindowO.confirmWindow(SarosConstant.SHELL_TITLE_CONFIRM_DELETE,
                SarosConstant.BUTTON_YES);
        } catch (WidgetNotFoundException e) {
            log.info("Contact not found: " + jid.getBase(), e);
        }
    }

    public void renameContact(String contact, String newName)
        throws RemoteException {
        SWTBotTree tree = bot.viewByTitle(SarosConstant.VIEW_TITLE_ROSTER)
            .bot().tree();
        SWTBotTreeItem item = treeO.getTreeItemWithMatchText(tree,
            SarosConstant.BUDDIES + ".*", contact + ".*");
        item.contextMenu("Rename...").click();
        windowO.waitUntilShellActive("Set new nickname");
        bot.text(contact).setText(newName);
        bot.button(SarosConstant.BUTTON_OK).click();
    }

    public void xmppConnect(JID jid, String password) throws RemoteException {
        log.trace("connectedByXMPP");
        boolean connectedByXMPP = isConnectedByXMPP();
        if (!connectedByXMPP) {
            log.trace("clickTBConnectInRosterView");
            clickTBConnectInRosterView();
            bot.sleep(100);// wait a bit to check if shell
                           // pops
            // up
            log.trace("isShellActive");
            boolean shellActive = exWindowO
                .isShellActive(SarosConstant.SAROS_CONFI_SHELL_TITLE);
            if (shellActive) {
                log.trace("confirmSarosConfigurationWindow");
                exWindowO.confirmSarosConfigurationWizard(jid.getDomain(),
                    jid.getName(), password);
            }
            waitUntilConnected();
        }
    }

}