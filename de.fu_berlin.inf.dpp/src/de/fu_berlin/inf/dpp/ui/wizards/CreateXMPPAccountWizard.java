/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universität Berlin - Fachbereich Mathematik und Informatik - 2006
 * (c) Riad Djemili - 2006
 * (c) Christopher Oezbek - 2006
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 1, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package de.fu_berlin.inf.dpp.ui.wizards;

import java.lang.reflect.InvocationTargetException;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.SarosPluginContext;
import de.fu_berlin.inf.dpp.accountManagement.XMPPAccount;
import de.fu_berlin.inf.dpp.accountManagement.XMPPAccountStore;
import de.fu_berlin.inf.dpp.net.util.RosterUtils;
import de.fu_berlin.inf.dpp.preferences.PreferenceUtils;
import de.fu_berlin.inf.dpp.ui.ImageManager;
import de.fu_berlin.inf.dpp.ui.Messages;
import de.fu_berlin.inf.dpp.ui.util.DialogUtils;
import de.fu_berlin.inf.dpp.ui.wizards.pages.CreateXMPPAccountWizardPage;

/**
 * @JTourBusStop 4, The Interface Tour:
 * 
 *               Another important element to the Saros interface is the
 *               Wizard. Eclipse supplies an abstract Wizard class that can
 *               be extended with your concrete functionality.
 * 
 *               In this example, the CreateXMPPAccountWizard allows the user
 *               to enter the details of a new account, validate them and 
 *               store them in our account store.
 */

/**
 * A wizard that is used to create XMPP accounts.
 * 
 * @author rdjemili
 * @author coezbek
 * @author bkahlert
 */
public class CreateXMPPAccountWizard extends Wizard {
    private static final Logger log = Logger
        .getLogger(CreateXMPPAccountWizard.class);

    @Inject
    protected Saros saros;

    @Inject
    protected PreferenceUtils preferenceUtils;

    @Inject
    protected XMPPAccountStore accountStore;

    protected final CreateXMPPAccountWizardPage createXMPPAccountPage;

    /*
     * Fields are cached in order to make the values accessible in case the
     * controls are already disposed. This is the case when the Wizard finished
     * or WizardDialog closed the Wizard.
     */
    protected String cachedServer;
    protected String cachedUsername;
    protected String cachedPassword;

    protected XMPPAccount createdXMPPAccount;

    public CreateXMPPAccountWizard(boolean showUseNowButton) {

        SarosPluginContext.initComponent(this);

        setWindowTitle(Messages.CreateXMPPAccountWizard_title);
        setDefaultPageImageDescriptor(ImageManager.WIZBAN_CREATE_XMPP_ACCOUNT);

        this.createXMPPAccountPage = new CreateXMPPAccountWizardPage(
            showUseNowButton);
        setNeedsProgressMonitor(true);
        setHelpAvailable(true);
    }

    @Override
    public void addPages() {
        addPage(this.createXMPPAccountPage);
    }

    /**
     * @JTourBusStop 5, The Interface Tour:
     * 
     *               The performFinish() method is run when the user clicks the
     *               finish button on the wizard.
     */
    @Override
    public boolean performFinish() {
        this.cachedServer = this.getServer();
        this.cachedUsername = this.getUsername();
        this.cachedPassword = this.getPassword();

        try {
            // fork a new thread to prevent the GUI from hanging
            getContainer().run(true, false, new IRunnableWithProgress() {
                public void run(IProgressMonitor monitor)
                    throws InvocationTargetException {
                    RosterUtils.createAccount(cachedServer, cachedUsername,
                        cachedPassword, monitor);
                    log.debug("Account creation succeeded: username="
                        + cachedUsername + ", server=" + cachedServer);
                }
            });
        } catch (InvocationTargetException e) {
            log.warn(e.getCause().getMessage(), e.getCause());

            this.createXMPPAccountPage.setErrorMessage(e.getMessage());

            // Leave the wizard open
            return false;
        } catch (InterruptedException e) {
            log.error("An internal error occurred: InterruptedException"
                + " thrown from uninterruptable method", e);
            this.createXMPPAccountPage.setErrorMessage(e.getCause()
                .getMessage());
            return false;
        }

        // add account to the accountStore
        this.createdXMPPAccount = accountStore.createAccount(cachedUsername,
            cachedPassword, cachedServer.toLowerCase(), "", 0, true, true);

        // reconnect if user wishes
        if (createXMPPAccountPage.useNow()) {
            boolean reconnect = true;
            if (saros.getSarosNet().isConnected()) {
                reconnect = DialogUtils.openQuestionMessageDialog(getShell(),
                    Messages.CreateXMPPAccountWizard_already_connected,
                    Messages.CreateXMPPAccountWizard_already_connected_text);
            }

            if (reconnect) {
                accountStore.setAccountActive(this.createdXMPPAccount);
                saros.connect(false);
            }
        }

        return true;
    }

    /*
     * Wizard Results
     */

    /**
     * Returns the server (used) for account creation.
     * 
     * @return
     */
    protected String getServer() {
        try {
            return this.createXMPPAccountPage.getServer();
        } catch (Exception e) {
            return cachedServer;
        }
    }

    /**
     * Returns the username (used) for account creation.
     * 
     * @return
     */
    protected String getUsername() {
        try {
            return this.createXMPPAccountPage.getUsername();
        } catch (Exception e) {
            return cachedUsername;
        }
    }

    /**
     * Returns the password (used) for account creation.
     * 
     * @return
     */
    protected String getPassword() {
        try {
            return this.createXMPPAccountPage.getPassword();
        } catch (Exception e) {
            return cachedPassword;
        }
    }

    /**
     * Returns the created {@link XMPPAccount}.
     * 
     * @return null if the {@link XMPPAccount} has not (yet) been created.
     */
    public XMPPAccount getCreatedXMPPAccount() {
        return this.createdXMPPAccount;
    }
}
