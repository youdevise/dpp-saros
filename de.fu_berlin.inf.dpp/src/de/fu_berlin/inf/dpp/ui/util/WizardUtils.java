package de.fu_berlin.inf.dpp.ui.util;

import java.util.concurrent.Callable;

import org.apache.log4j.Logger;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.NewProjectAction;

import de.fu_berlin.inf.dpp.accountManagement.XMPPAccount;
import de.fu_berlin.inf.dpp.ui.wizards.AddBuddyWizard;
import de.fu_berlin.inf.dpp.ui.wizards.AddXMPPAccountWizard;
import de.fu_berlin.inf.dpp.ui.wizards.ConfigurationWizard;
import de.fu_berlin.inf.dpp.ui.wizards.CreateXMPPAccountWizard;
import de.fu_berlin.inf.dpp.ui.wizards.EditXMPPAccountWizard;
import de.fu_berlin.inf.dpp.ui.wizards.ShareProjectAddBuddiesWizard;
import de.fu_berlin.inf.dpp.ui.wizards.ShareProjectAddProjectsWizard;
import de.fu_berlin.inf.dpp.ui.wizards.ShareProjectWizard;

/**
 * Utility class for {@link IWizard}s
 */
public class WizardUtils {
    private static final Logger log = Logger.getLogger(WizardUtils.class
        .getName());

    /**
     * Open a wizard in the SWT thread and returns the {@link WizardDialog}'s
     * return code.
     * 
     * @param parentShell
     * @param wizard
     * @return
     */
    public static Integer openWizard(final Shell parentShell,
        final Wizard wizard) {
        try {
            return SWTUtils.runSWTSync(new Callable<Integer>() {
                @Override
                public Integer call() {
                    WizardDialog wizardDialog = new WizardDialog(parentShell,
                        wizard);
                    wizardDialog.setHelpAvailable(false);
                    return wizardDialog.open();
                }
            });
        } catch (Exception e) {
            log.warn("Error opening wizard " + wizard.getWindowTitle(), e);
        }
        return null;
    }

    /**
     * Open a wizard in the SWT thread and returns the {@link WizardDialog}'s
     * reference to the {@link Wizard} in case of success.
     * 
     * @param wizard
     * 
     * @return the wizard if it was successfully finished; null otherwise
     */
    public static <W extends Wizard> W openWizardSuccessfully(
        final Shell parentShell, final W wizard) {
        Integer returnCode = openWizard(parentShell, wizard);
        return (returnCode != null && returnCode == Window.OK) ? wizard : null;
    }

    /**
     * Open a wizard in the SWT thread and returns the {@link WizardDialog}'s
     * reference to the {@link Wizard} in case of success.
     * 
     * @param wizard
     * 
     * @return the wizard if it was successfully finished; null otherwise
     */
    public static <W extends Wizard> W openWizardSuccessfully(final W wizard) {
        return openWizardSuccessfully(null, wizard);
    }

    /**
     * Runs the {@link NewProjectAction} in the SWT thread in order to create a
     * new project wizard.
     */
    public static void openNewProjectWizard() {
        SWTUtils.runSafeSWTSync(log, new Runnable() {
            @Override
            public void run() {
                IWorkbenchWindow window = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow();
                NewProjectAction newProjectAction = new NewProjectAction(window);
                newProjectAction.run();
            }
        });
    }

    /**
     * Opens a {@link ConfigurationWizard} in the SWT thread and returns the
     * displayed instance in case of success.
     * 
     * @return the wizard if it was successfully finished; null otherwise
     */
    public static ConfigurationWizard openSarosConfigurationWizard() {
        return openWizardSuccessfully(new ConfigurationWizard());
    }

    /**
     * Opens a {@link AddXMPPAccountWizard} in the SWT thread and returns the
     * displayed instance in case of success.
     * 
     * @return the wizard if it was successfully finished; null otherwise
     */
    public static AddXMPPAccountWizard openAddXMPPAccountWizard() {
        return openWizardSuccessfully(new AddXMPPAccountWizard());
    }

    /**
     * Opens a {@link CreateXMPPAccountWizard} in the SWT thread and returns the
     * displayed instance in case of success.
     * 
     * @param showUseNowButton
     * @return the wizard if it was successfully finished; null otherwise
     */
    public static CreateXMPPAccountWizard openCreateXMPPAccountWizard(
        boolean showUseNowButton) {
        return openWizardSuccessfully(new CreateXMPPAccountWizard(
            showUseNowButton));
    }

    /**
     * Opens a {@link EditXMPPAccountWizard} in the SWT thread and returns the
     * displayed instance in case of success.
     * 
     * @param account
     *            to be edited; null if the current account should be edited
     *            (creates one if no active account is set)
     * @return the wizard if it was successfully finished; null otherwise
     */
    public static EditXMPPAccountWizard openEditXMPPAccountWizard(
        XMPPAccount account) {
        return openWizardSuccessfully(new EditXMPPAccountWizard(account));
    }

    /**
     * Opens a {@link AddBuddyWizard} in the SWT thread and returns the
     * displayed instance in case of success.
     */
    public static AddBuddyWizard openAddBuddyWizard() {
        return openWizardSuccessfully(new AddBuddyWizard());
    }

    /**
     * Opens a {@link ShareProjectWizard} in the SWT thread and returns the
     * displayed instance in case of success.
     */
    public static ShareProjectWizard openShareProjectWizard() {
        return openWizardSuccessfully(new ShareProjectWizard());
    }

    /**
     * Opens a {@link ShareProjectAddProjectsWizard} in the SWT thread and
     * returns the displayed instance in case of success.
     */
    public static ShareProjectAddProjectsWizard openShareProjectAddProjectsWizard() {
        return openWizardSuccessfully(new ShareProjectAddProjectsWizard());
    }

    /**
     * Opens a {@link ShareProjectAddBuddiesWizard} in the SWT thread and
     * returns the displayed instance in case of success.
     */
    public static ShareProjectAddBuddiesWizard openShareProjectAddBuddiesWizard() {
        return openWizardSuccessfully(new ShareProjectAddBuddiesWizard());
    }
}
