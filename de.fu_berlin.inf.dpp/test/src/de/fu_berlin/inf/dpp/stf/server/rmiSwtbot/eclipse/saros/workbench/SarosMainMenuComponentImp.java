package de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.saros.workbench;

import java.rmi.RemoteException;

import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;

import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.stf.server.SarosConstant;
import de.fu_berlin.inf.dpp.stf.server.rmiSwtbot.eclipse.workbench.MainMenuComponentImp;

public class SarosMainMenuComponentImp extends MainMenuComponentImp
    implements SarosMainMenuComponent {
    // public static SarosMainMenuObjectImp classVariable;

    private static transient SarosMainMenuComponentImp self;

    /**
     * {@link SarosMainMenuComponentImp} is a singleton, but inheritance is
     * possible.
     */
    public static SarosMainMenuComponentImp getInstance() {
        if (self != null)
            return self;
        self = new SarosMainMenuComponentImp();
        return self;
    }

    public void creatNewAccount(JID jid, String password)
        throws RemoteException {
        exWorkbenchO.getEclipseShell().activate().setFocus();
        menuO.clickMenuWithTexts("Saros", "Create Account");
        confirmCreateNewUserAccountWindow(jid.getDomain(), jid.getName(),
            password);
    }

    public void confirmCreateNewUserAccountWindow(String server,
        String username, String password) throws RemoteException {
        try {
            windowO.activateShellWithText("Create New User Account");
            bot.textWithLabel("Jabber Server").setText(server);
            bot.textWithLabel("Username").setText(username);
            bot.textWithLabel("Password").setText(password);
            bot.textWithLabel("Repeat Password").setText(password);
            bot.button(SarosConstant.BUTTON_FINISH).click();
        } catch (WidgetNotFoundException e) {
            log.error("widget not found while accountBySarosMenu", e);
        }
    }

}