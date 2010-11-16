package de.fu_berlin.inf.dpp.ui.actions;

import java.awt.Toolkit;

import org.eclipse.jface.action.Action;
import org.eclipse.ui.PlatformUI;

import de.fu_berlin.inf.dpp.preferences.PreferenceConstants;
import de.fu_berlin.inf.dpp.ui.SarosUI;

public class IMBeepAction extends Action {
    public IMBeepAction(String text) {
        super(text);
        updateIcon();
    }

    @Override
    public void run() {
        PlatformUI.getPreferenceStore();

        this.setOn(!this.isOn());

        this.updateIcon();
    }

    protected boolean isOn() {
        return PlatformUI.getPreferenceStore().getBoolean(
            PreferenceConstants.BEEP_UPON_IM);
    }

    protected void setOn(boolean on) {
        PlatformUI.getPreferenceStore().setValue(
            PreferenceConstants.BEEP_UPON_IM, on);
    }

    public void beep() {
        if (this.isOn())
            Toolkit.getDefaultToolkit().beep();
    }

    public void updateIcon() {
        if (this.isOn()) {
            this.setImageDescriptor(SarosUI
                .getImageDescriptor("/icons/speaker_on.png"));
        } else {
            this.setImageDescriptor(SarosUI
                .getImageDescriptor("/icons/speaker_off.png"));
        }
    }
}