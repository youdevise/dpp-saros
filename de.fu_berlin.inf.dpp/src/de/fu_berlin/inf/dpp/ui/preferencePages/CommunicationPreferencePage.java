package de.fu_berlin.inf.dpp.ui.preferencePages;

import java.util.Collections;
import java.util.List;

import javax.sound.sampled.Mixer;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.SarosPluginContext;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.communication.audio.MixerManager;
import de.fu_berlin.inf.dpp.preferences.AudioSettings;
import de.fu_berlin.inf.dpp.preferences.PreferenceConstants;
import de.fu_berlin.inf.dpp.ui.Messages;

@Component(module = "prefs")
public class CommunicationPreferencePage extends FieldEditorPreferencePage
    implements IWorkbenchPreferencePage {

    @Inject
    protected Saros saros;

    @Inject
    protected MixerManager mixerManager;

    protected Composite parent;
    protected StringFieldEditor chatserver;
    protected BooleanFieldEditor useCustomChatServer;
    protected StringFieldEditor skypeName;
    protected BooleanFieldEditor beepUponIM;
    protected BooleanFieldEditor audio_vbr;
    protected BooleanFieldEditor audio_dtx;
    protected ComboFieldEditor audioQuality;

    protected IPreferenceStore prefs;

    private Group chatGroup;
    private Group voipGroup;
    private Composite chatServerGroup;

    private String[][] audioQualityValues = { { "0", "0" }, { "1", "1" },
        { "2", "2" }, { "3", "3" }, { "4", "4" }, { "5", "5" }, { "6", "6" },
        { "7", "7" }, { "8", "8" }, { "9", "9" }, { "10", "10" } };

    public CommunicationPreferencePage() {
        super(FieldEditorPreferencePage.GRID);
        SarosPluginContext.initComponent(this);
        setPreferenceStore(saros.getPreferenceStore());
        setDescription("Settings for Chat and VoIP Functionality.");
        this.prefs = saros.getPreferenceStore();
    }

    @Override
    protected void createFieldEditors() {

        parent = getFieldEditorParent();

        chatGroup = new Group(parent, SWT.NONE);
        voipGroup = new Group(parent, SWT.NONE);

        GridData chatGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        GridData voipGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);

        chatGridData.horizontalSpan = 2;
        voipGridData.horizontalSpan = 2;

        chatGroup.setText("Chat");
        chatGroup.setLayout(new GridLayout(2, false));

        voipGroup.setText("VoIP");
        voipGroup.setLayout(new GridLayout(2, false));

        chatGroup.setLayoutData(chatGridData);
        voipGroup.setLayoutData(voipGridData);

        chatServerGroup = new Composite(chatGroup, SWT.NONE);
        chatServerGroup.setLayout(new GridLayout(2, false));
        chatServerGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
            false));

        chatserver = new StringFieldEditor(
            PreferenceConstants.CUSTOM_MUC_SERVICE, "Custom chatserver: ",
            chatServerGroup);

        useCustomChatServer = new BooleanFieldEditor(
            PreferenceConstants.FORCE_CUSTOM_MUC_SERVICE,
            "Always use custom chatserver", chatGroup);

        beepUponIM = new BooleanFieldEditor(PreferenceConstants.SOUND_ENABLED,
            "Beep when receiving a chat message", chatGroup);
        beepUponIM.setEnabled(true, chatGroup);

        skypeName = new StringFieldEditor(PreferenceConstants.SKYPE_USERNAME,
            "Skype name:", getFieldEditorParent());

        audioQuality = new ComboFieldEditor(
            PreferenceConstants.AUDIO_QUALITY_LEVEL,
            "Audio Quality Level (0-10) - 10 is best", audioQualityValues,
            voipGroup);

        ComboFieldEditor audioSamplerate = new ComboFieldEditor(
            PreferenceConstants.AUDIO_SAMPLERATE, "Audio Samplerate (kHz)",
            get2dArray(AudioSettings.AUDIO_SAMPLE_RATE), voipGroup);

        audio_vbr = new BooleanFieldEditor(
            PreferenceConstants.AUDIO_VBR,
            "Use Variable Bitrate (gives a better quality-to-space ratio, but may introduce a delay)",
            voipGroup);

        audio_dtx = new BooleanFieldEditor(
            PreferenceConstants.AUDIO_ENABLE_DTX,
            "Use Discontinuous Transmission (silence is not transmitted - only works with variable bitrate)",
            voipGroup);

        audio_dtx.setEnabled(prefs.getBoolean(PreferenceConstants.AUDIO_VBR),
            voipGroup);

        boolean enabled = true;

        String[][] playbackMixers = getPlaybackMixerNames();

        if (playbackMixers == null) {
            playbackMixers = new String[][] { { "N/A",
                Messages.CommunicationPreferencePage_unknown } };
            enabled = false;
        }

        ComboFieldEditor audioPlaybackDevices = new ComboFieldEditor(
            PreferenceConstants.AUDIO_PLAYBACK_DEVICE, "Audio Playback Device",
            playbackMixers, voipGroup);

        audioPlaybackDevices.setEnabled(enabled, voipGroup);

        enabled = true;

        String[][] recordMixers = getRecordMixerNames();

        if (recordMixers == null) {
            recordMixers = new String[][] { { "N/A",
                Messages.CommunicationPreferencePage_unknown } };
            enabled = false;
        }

        ComboFieldEditor audioRecordDevices = new ComboFieldEditor(
            PreferenceConstants.AUDIO_RECORD_DEVICE, "Audio Record Device",
            recordMixers, voipGroup);

        audioRecordDevices.setEnabled(enabled, voipGroup);

        addField(chatserver);
        addField(useCustomChatServer);
        addField(beepUponIM);
        addField(skypeName);
        addField(audioQuality);
        addField(audioSamplerate);
        addField(audio_vbr);
        addField(audio_dtx);
        addField(audioPlaybackDevices);
        addField(audioRecordDevices);

    }

    @Override
    public void initialize() {
        super.initialize();
        if (prefs.getBoolean(PreferenceConstants.FORCE_CUSTOM_MUC_SERVICE)) {
            useCustomChatServer.setEnabled(!chatserver.getStringValue()
                .isEmpty(), chatGroup);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {

        if (event.getSource() instanceof FieldEditor) {
            FieldEditor field = (FieldEditor) event.getSource();

            if (field.getPreferenceName().equals(PreferenceConstants.AUDIO_VBR)) {
                if (event.getNewValue() instanceof Boolean) {
                    Boolean newValue = (Boolean) event.getNewValue();
                    audio_dtx.setEnabled(newValue, voipGroup);
                }
            } else if (field.getPreferenceName().equals(
                PreferenceConstants.CUSTOM_MUC_SERVICE)) {
                String serverName = event.getNewValue().toString();
                useCustomChatServer
                    .setEnabled(!serverName.isEmpty(), chatGroup);
            }
        }
    }

    protected String[][] getRecordMixerNames() {
        return getMixerNames(0);
    }

    protected String[][] getPlaybackMixerNames() {
        return getMixerNames(1);
    }

    @SuppressWarnings("unchecked")
    protected String[][] getMixerNames(int type) {
        List<Mixer.Info> mixerInfo;
        if (type == 0)
            mixerInfo = mixerManager.getRecordingMixers();
        else if (type == 1)
            mixerInfo = mixerManager.getPlaybackMixers();
        else
            mixerInfo = Collections.EMPTY_LIST;

        if (mixerInfo.isEmpty())
            return null;

        String[][] devices = new String[mixerInfo.size()][2];
        for (int i = 0; i < mixerInfo.size(); i++) {
            devices[i][0] = mixerInfo.get(i).getName();
            devices[i][1] = mixerInfo.get(i).getName();
        }

        return devices;

    }

    protected String[][] get2dArray(String[] inputArray) {
        String outputArray[][] = new String[inputArray.length][2];
        for (int i = 0; i < inputArray.length; i++) {
            outputArray[i][0] = inputArray[i];
            outputArray[i][1] = inputArray[i];
        }
        return outputArray;
    }

    @Override
    public void init(IWorkbench workbench) {
        // TODO Auto-generated method stub
    }
}
