package de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.saros.workbench;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;

import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.stf.server.SarosConstant;
import de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.conditions.SarosConditions;
import de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.EclipseComponent;
import de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.saros.noGUI.SarosState;

/**
 * This implementation of {@link SessionViewComponent}
 * 
 * @author Lin
 */
public class SessionViewComponentImp extends EclipseComponent implements
    SessionViewComponent {

    private static transient SessionViewComponentImp self;

    /*
     * View infos
     */
    private final static String VIEWNAME = SarosConstant.VIEW_TITLE_SHARED_PROJECT_SESSION;
    private final static String VIEWID = SarosConstant.ID_SESSION_VIEW;

    /*
     * title of shells which are pop up by performing the actions on the session
     * view.
     */
    private final static String CONFIRMLEAVINGSESSION = SarosConstant.SHELL_TITLE_CONFIRM_LEAVING_SESSION;
    private final static String CONFIRMCLOSINGSESSION = "Confirm Closing Session";
    private final static String INCOMINGSCREENSHARINGSESSION = "Incoming screensharing session";
    private final static String INVITATION = "Invitation";
    private final static String ERRORINSAROSPLUGIN = "Error in Saros-Plugin";
    private final static String CLOSINGTHESESSION = "Closing the Session";

    /*
     * Tool tip text of toolbar buttons on the session view
     */
    private final static String SHARESCREENWITHUSER = SarosConstant.TOOL_TIP_TEXT_SHARE_SCREEN_WITH_USER;
    private final static String STOPSESSIONWITHUSER = "Stop session with user";
    private final static String SENDAFILETOSELECTEDUSER = SarosConstant.TOOL_TIP_TEXT_SEND_FILE_TO_SELECTED_USER;
    private final static String STARTVOIPSESSION = SarosConstant.TOOL_TIP_TEXT_START_VOIP_SESSION;
    private final static String INCONSISTENCYDETECTED = SarosConstant.TOOL_TIP_TEXT_INCONSISTENCY_DETECTED;
    private final static String OPENINVITATIONINTERFACE = SarosConstant.TOOL_TIP_TEXT_OPEN_INVITATION_INTERFACE;
    private final static String REMOVEALLDRIVERROLES = SarosConstant.TOOL_TIP_TEXT_REMOVE_ALL_DRIVER_ROLES;
    private final static String ENABLEDISABLEFOLLOWMODE = SarosConstant.TOOL_TIP_TEXT_ENABLE_DISABLE_FOLLOW_MODE;
    private final static String LEAVETHESESSION = SarosConstant.TOOL_TIP_TEXT_LEAVE_THE_SESSION;

    // Context menu of the table on the view
    private final static String GIVEEXCLUSIVEDRIVERROLE = SarosConstant.CONTEXT_MENU_GIVE_EXCLUSIVE_DRIVER_ROLE;
    private final static String GIVEDRIVERROLE = SarosConstant.CONTEXT_MENU_GIVE_DRIVER_ROLE;
    private final static String REMOVEDRIVERROLE = SarosConstant.CONTEXT_MENU_REMOVE_DRIVER_ROLE;
    private final static String FOLLOWTHISUSER = SarosConstant.CONTEXT_MENU_FOLLOW_THIS_USER;
    private final static String STOPFOLLOWINGTHISUSER = SarosConstant.CONTEXT_MENU_STOP_FOLLOWING_THIS_USER;
    private final static String JUMPTOPOSITIONSELECTEDUSER = SarosConstant.CONTEXT_MENU_JUMP_TO_POSITION_SELECTED_USER;
    private final static String CHANGECOLOR = "Change Color";

    /**
     * {@link SessionViewComponentImp} is a singleton, but inheritance is
     * possible.
     */
    public static SessionViewComponentImp getInstance() {
        if (self != null)
            return self;
        self = new SessionViewComponentImp();
        return self;
    }

    /**************************************************************
     * 
     * exported functions
     * 
     **************************************************************/
    public boolean isInSession() throws RemoteException {
        precondition();
        return isToolbarButtonEnabled(LEAVETHESESSION);
    }

    public void openSessionView() throws RemoteException {
        if (!isSessionViewOpen())
            viewO.openViewById(VIEWID);
    }

    public boolean isSessionViewOpen() throws RemoteException {
        return viewO.isViewOpen(VIEWNAME);
    }

    public void waitUntilSessionOpen() throws RemoteException {
        waitUntil(SarosConditions.isInSession(exStateO));
    }

    public void waitUntilSessionOpenBy(SarosState stateOfPeer)
        throws RemoteException {
        waitUntil(SarosConditions.isInSession(stateOfPeer));
    }

    public void setFocusOnSessionView() throws RemoteException {
        viewO.setFocusOnViewByTitle(VIEWNAME);
        viewO.waitUntilViewActive(VIEWNAME);
    }

    public boolean isSessionViewActive() throws RemoteException {
        return viewO.isViewActive(VIEWNAME);
    }

    public void closeSessionView() throws RemoteException {
        if (isSessionViewOpen())
            viewO.closeViewById(VIEWID);
    }

    public void waitUntilSessionClosed() throws RemoteException {
        waitUntil(SarosConditions.isSessionClosed(exStateO));
    }

    public void waitUntilSessionClosedBy(SarosState stateOfPeer)
        throws RemoteException {
        waitUntil(SarosConditions.isSessionClosed(stateOfPeer));
    }

    public boolean isContactInSessionView(String contactName)
        throws RemoteException {
        precondition();
        SWTBotTable table = tableO.getTable();
        for (int i = 0; i < table.rowCount(); i++) {
            if (table.getTableItem(i).getText().equals(contactName))
                return true;
        }
        return false;
    }

    public void giveDriverRole(SarosState stateOfInvitee)
        throws RemoteException {
        JID InviteeJID = stateOfInvitee.getJID();
        if (stateOfInvitee.isDriver(InviteeJID)) {
            throw new RuntimeException(
                "User \""
                    + InviteeJID.getBase()
                    + "\" is already a driver! Please pass a correct Musician Object to the method.");
        }
        precondition();
        tableO.clickContextMenuOfTable(InviteeJID.getBase(), GIVEDRIVERROLE);
        windowO.waitUntilShellClosed(PROGRESSINFORMATION);
    }

    public void giveExclusiveDriverRole(SarosState stateOfInvitee)
        throws RemoteException {
        JID InviteeJID = stateOfInvitee.getJID();
        if (stateOfInvitee.isDriver(InviteeJID)) {
            throw new RuntimeException(
                "User \""
                    + InviteeJID.getBase()
                    + "\" is already a driver! Please pass a correct Musician Object to the method.");
        }
        precondition();
        tableO.clickContextMenuOfTable(InviteeJID.getBase(),
            GIVEEXCLUSIVEDRIVERROLE);
        windowO.waitUntilShellClosed(PROGRESSINFORMATION);
    }

    public void removeDriverRole(SarosState stateOfInvitee)
        throws RemoteException {
        JID InviteeJID = stateOfInvitee.getJID();
        if (!stateOfInvitee.isDriver(InviteeJID)) {
            throw new RuntimeException(
                "User \""
                    + InviteeJID.getBase()
                    + "\" is  no driver! Please pass a correct Musician Object to the method.");
        }
        precondition();
        tableO.clickContextMenuOfTable(InviteeJID.getBase() + ROLENAME,
            REMOVEDRIVERROLE);
        windowO.waitUntilShellClosed(PROGRESSINFORMATION);
    }

    public void followThisUser(SarosState stateOfFollowedUser)
        throws RemoteException {
        precondition();
        JID JIDOfFollowedUser = stateOfFollowedUser.getJID();
        if (exStateO.isInFollowMode()) {
            log.debug(JIDOfFollowedUser.getBase()
                + " is already followed by you.");
            return;
        }
        clickContextMenuOfSelectedUser(
            stateOfFollowedUser,
            FOLLOWTHISUSER,
            "Hi guy, you can't follow youself, it makes no sense! Please pass a correct parameter to the method.");
    }

    public boolean isInFollowMode() throws RemoteException {
        precondition();
        List<String> allContactsName = getAllContactsInSessionView();
        for (String contactName : allContactsName) {
            // SWTBotTable table = tableObject.getTable();
            if (!tableO.existsContextOfTableItem(contactName,
                STOPFOLLOWINGTHISUSER))
                continue;
            if (isStopFollowingThisUserEnabled(contactName))
                return true;
        }
        return false;
    }

    public void stopFollowing() throws RemoteException {
        JID followedUserJID = exStateO.getFollowedUserJID();
        if (followedUserJID == null) {
            log.debug(" You are not in follow mode, so you don't need to perform thhe function.");
            return;
        }
        log.debug(" JID of the followed user: " + followedUserJID.getBase());
        precondition();
        if (exStateO.isDriver(followedUserJID))
            tableO.clickContextMenuOfTable(
                followedUserJID.getBase() + ROLENAME, STOPFOLLOWINGTHISUSER);
        else
            tableO.clickContextMenuOfTable(followedUserJID.getBase(),
                STOPFOLLOWINGTHISUSER);
    }

    public void stopFollowingThisUser(SarosState stateOfFollowedUser)
        throws RemoteException {
        if (!exStateO.isInFollowMode()) {
            log.debug(" You are not in follow mode, so you don't need to perform thhe function.");
            return;
        }
        clickContextMenuOfSelectedUser(
            stateOfFollowedUser,
            STOPFOLLOWINGTHISUSER,
            "Hi guy, you can't stop following youself, it makes no sense! Please pass a correct parameter to the method.");
    }

    public boolean isStopFollowingThisUserVisible(String contactName)
        throws RemoteException {
        return tableO.isContextMenuOfTableVisible(contactName,
            STOPFOLLOWINGTHISUSER);
    }

    public boolean isStopFollowingThisUserEnabled(String contactName)
        throws RemoteException {
        return tableO.isContextMenuOfTableEnabled(contactName,
            STOPFOLLOWINGTHISUSER);
    }

    public void waitUntilIsFollowingUser(String baseJIDOfFollowedUser)
        throws RemoteException {
        waitUntil(SarosConditions.isFollowingUser(exStateO,
            baseJIDOfFollowedUser));
    }

    public void shareYourScreenWithSelectedUser(
        SarosState stateOfselectedUser) throws RemoteException {
        selectUser(
            stateOfselectedUser,
            "Hi guy, you can't share screen with youself, it makes no sense! Please pass a correct parameter to the method.");
        clickToolbarButtonWithTooltip(SHARESCREENWITHUSER);
    }

    public void stopSessionWithUser(SarosState stateOfselectedUser)
        throws RemoteException {
        selectUser(
            stateOfselectedUser,
            "Hi guy, you can't stop screen session with youself, it makes no sense! Please pass a correct parameter to the method.");
        clickToolbarButtonWithTooltip(STOPSESSIONWITHUSER);
    }

    public void sendAFileToSelectedUser(SarosState stateOfselectedUser)
        throws RemoteException {
        selectUser(
            stateOfselectedUser,
            "Hi guy, you can't send a file to youself, it makes no sense! Please pass a correct parameter to the method.");
        clickToolbarButtonWithTooltip(SENDAFILETOSELECTEDUSER);
    }

    public void startAVoIPSession(SarosState stateOfselectedUser)
        throws RemoteException {
        selectUser(
            stateOfselectedUser,
            "Hi guy, you can't start a VoIP session with youself, it makes no sense! Please pass a correct parameter to the method.");
        clickToolbarButtonWithTooltip(STARTVOIPSESSION);
        if (windowO.isShellActive(ERRORINSAROSPLUGIN)) {
            confirmErrorInSarosPluginWindow();
        }
    }

    public void inconsistencyDetected() throws RemoteException {
        precondition();
        clickToolbarButtonWithTooltip(INCONSISTENCYDETECTED);
        windowO.waitUntilShellCloses(PROGRESSINFORMATION);
    }

    public void removeAllRriverRoles() throws RemoteException {
        precondition();
        if (isRemoveAllRiverEnabled())
            clickToolbarButtonWithTooltip(REMOVEALLDRIVERROLES);
    }

    public boolean isRemoveAllRiverEnabled() throws RemoteException {
        precondition();
        return isToolbarButtonEnabled(REMOVEALLDRIVERROLES);
    }

    public void enableDisableFollowMode() throws RemoteException {
        precondition();
        if (isEnableDisableFollowModeEnabled())
            clickToolbarButtonWithTooltip(ENABLEDISABLEFOLLOWMODE);
    }

    public boolean isEnableDisableFollowModeEnabled() throws RemoteException {
        precondition();
        return isToolbarButtonEnabled(ENABLEDISABLEFOLLOWMODE);
    }

    private void leaveTheSession() {
        clickToolbarButtonWithTooltip(LEAVETHESESSION);
    }

    public void waitUntilAllPeersLeaveSession(List<JID> jidsOfAllParticipants)
        throws RemoteException {
        waitUntil(SarosConditions.existsNoParticipants(exStateO,
            jidsOfAllParticipants));
    }

    public void jumpToPositionOfSelectedUser(SarosState stateOfselectedUser)
        throws RemoteException {
        clickContextMenuOfSelectedUser(
            stateOfselectedUser,
            JUMPTOPOSITIONSELECTEDUSER,
            "Hi guy, you can't jump to the position of youself, it makes no sense! Please pass a correct parameter to the method.");
    }

    public boolean isInconsistencyDetectedEnabled() throws RemoteException {
        precondition();
        return isToolbarButtonEnabled(INCONSISTENCYDETECTED);

    }

    public void openInvitationInterface(String jidOfInvitee)
        throws RemoteException {
        precondition();
        clickToolbarButtonWithTooltip(OPENINVITATIONINTERFACE);
        comfirmInvitationWindow(jidOfInvitee);
    }

    public void comfirmInvitationWindow(String jidOfinvitee)
        throws RemoteException {
        windowO.waitUntilShellActive(INVITATION);
        windowO.confirmWindowWithCheckBox(INVITATION, FINISH, jidOfinvitee);
    }

    public void leaveTheSessionByPeer() throws RemoteException {
        precondition();
        leaveTheSession();
        windowO.confirmWindow(CONFIRMLEAVINGSESSION, YES);
        waitUntilSessionClosed();
    }

    public void leaveTheSessionByHost() throws RemoteException {
        precondition();
        leaveTheSession();
        // Util.runSafeAsync(log, new Runnable() {
        // public void run() {
        // try {
        // exWindowO.confirmWindow("Confirm Closing Session",
        // SarosConstant.BUTTON_YES);
        // } catch (RemoteException e) {
        // // no popup
        // }
        // }
        // });
        if (windowO.isShellActive(CONFIRMCLOSINGSESSION))
            windowO.confirmWindow(CONFIRMCLOSINGSESSION, YES);
        waitUntilSessionClosed();
    }

    public void confirmIncomingScreensharingSesionWindow()
        throws RemoteException {
        windowO.waitUntilShellActive(INCOMINGSCREENSHARINGSESSION);
        windowO.confirmWindow(INCOMINGSCREENSHARINGSESSION, YES);
    }

    public void confirmErrorInSarosPluginWindow() throws RemoteException {
        windowO.confirmWindow(ERRORINSAROSPLUGIN, OK);
    }

    public void confirmClosingTheSessionWindow() throws RemoteException {
        windowO.waitUntilShellActive(CLOSINGTHESESSION);
        windowO.confirmWindow(CLOSINGTHESESSION, OK);
        windowO.waitUntilShellCloses(CLOSINGTHESESSION);
    }

    /**************************************************************
     * 
     * Inner functions
     * 
     **************************************************************/

    /**
     * 
     * Define the precondition which should be guaranteed when you want to
     * perform actions within the session view.
     * 
     * @throws RemoteException
     */
    @Override
    protected void precondition() throws RemoteException {
        openSessionView();
        setFocusOnSessionView();
    }

    /**
     * 
     * It get all contact names listed in the session view and would be used by
     * {@link SessionViewComponentImp#isInFollowMode}.
     * 
     * @return list, which contain all the contact names.
     * @throws RemoteException
     */
    private List<String> getAllContactsInSessionView() throws RemoteException {
        precondition();
        List<String> allContactsName = new ArrayList<String>();
        SWTBotTable table = tableO.getTable();
        for (int i = 0; i < table.rowCount(); i++) {
            allContactsName.add(table.getTableItem(i).getText());
        }
        return allContactsName;
    }

    private void clickContextMenuOfSelectedUser(
        SarosState stateOfselectedUser, String context, String message)
        throws RemoteException {
        JID jidOfSelectedUser = stateOfselectedUser.getJID();
        if (exStateO.isSameUser(jidOfSelectedUser)) {
            throw new RuntimeException(message);
        }
        precondition();
        if (stateOfselectedUser.isDriver(jidOfSelectedUser))
            tableO.clickContextMenuOfTable(jidOfSelectedUser.getBase()
                + ROLENAME, context);
        else
            tableO
                .clickContextMenuOfTable(jidOfSelectedUser.getBase(), context);
    }

    private void selectUser(SarosState stateOfselectedUser, String message)
        throws RemoteException {
        JID jidOfSelectedUser = stateOfselectedUser.getJID();
        if (exStateO.isSameUser(jidOfSelectedUser)) {
            throw new RuntimeException(message);
        }
        precondition();
        if (stateOfselectedUser.isDriver(jidOfSelectedUser)) {
            viewO.selectTableItemWithLabelInView(VIEWNAME,
                jidOfSelectedUser.getBase() + ROLENAME);
        } else {
            viewO.selectTableItemWithLabelInView(VIEWNAME,
                jidOfSelectedUser.getBase());
        }
    }

    protected boolean isToolbarButtonEnabled(String tooltip) {
        return viewO.isToolbarInViewEnabled(VIEWNAME, tooltip);
    }

    protected void clickToolbarButtonWithTooltip(String tooltipText) {
        viewO.clickToolbarButtonWithTooltipInView(VIEWNAME, tooltipText);
    }

    protected List<SWTBotToolbarButton> getToolbarButtons() {
        return viewO.getToolbarButtonsOnView(VIEWNAME);
    }
}