/**
 * 
 */
package de.fu_berlin.inf.dpp.feedback;

import java.util.Date;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.window.Window;
import org.picocontainer.Startable;

import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.editor.internal.EditorAPI;
import de.fu_berlin.inf.dpp.preferences.PreferenceConstants;
import de.fu_berlin.inf.dpp.project.ISarosSessionManager;
import de.fu_berlin.inf.dpp.ui.dialogs.FeedbackDialog;
import de.fu_berlin.inf.dpp.ui.util.SWTUtils;
import de.fu_berlin.inf.dpp.util.Utils;

/**
 * The FeedbackManager registers himself as a listener with the
 * {@link ISarosSessionManager} to show a {@link FeedbackDialog} at the end of a
 * session. But before he actually shows anything, it is determined from the
 * global preferences if the user wants to participate in general and in which
 * interval.
 * 
 * @author Lisa Dohrmann
 */
@Component(module = "feedback")
public class FeedbackManager extends AbstractFeedbackManager implements
    Startable {
    /** the URL to the website that contains our survey */
    public static final String SURVEY_URL = "http://saros-build.imp.fu-berlin.de/phpESP/public/survey.php?name=SarosFastUserFeedback_1";

    /** the text to show in the first FeedbackDialog */
    public static final String FEEDBACK_REQUEST = Messages
        .getString("feedback.dialog.request.general"); //$NON-NLS-1$

    public static final String FEEDBACK_REQUEST_SHORT = Messages
        .getString("feedback.dialog.request.short"); //$NON-NLS-1$

    public static final long MIN_SESSION_TIME = 5 * 60; // 5 min.

    public static final int FEEDBACK_ENABLED = 1;
    public static final int FEEDBACK_DISABLED = 2;

    public static final int BROWSER_EXT = 0;
    public static final int BROWSER_INT = 1;
    public static final int BROWSER_NONE = 2;

    protected static final Logger log = Logger.getLogger(FeedbackManager.class
        .getName());

    @Override
    public void start() {
        startTime = new Date();
    }

    @Override
    public void stop() {
        sessionTime = (new Date().getTime() - startTime.getTime()) / 1000;
        log.info(String.format("Session lasted %s min %s s", sessionTime / 60,
            sessionTime % 60));

        // If the -ea switch is enabled don't use MIN_SESSION_TIME
        assert debugSessionTime();

        // don't show the survey if session was very short
        if (sessionTime < MIN_SESSION_TIME)
            return;

        // decrement session until next
        int sessionsUntilNext = getSessionsUntilNext() - 1;
        setSessionsUntilNext(sessionsUntilNext);

        if (!showNow()) {
            log.info("Sessions until next survey: " + sessionsUntilNext);
            return;
        }

        /*
         * The following is executed asynchronously, because
         * showFeedbackDialog() is blocking, and other SessionListeners would be
         * blocked as long as the user doesn't answer the dialog. NOTE: If one
         * ever wants to count the number of declined dialogs, threading
         * problems must be newly considered.
         */
        Utils.runSafeAsync("ShowFeedbackDialog", log, new Runnable() {

            @Override
            public void run() {
                if (showFeedbackDialog(FEEDBACK_REQUEST)) {
                    int browserType = showSurvey();
                    log.info("Asking for feedback survey: User agreed ("
                        + getBrowserTypeAsString(browserType) + ")");
                } else {
                    log.info("Asking for feedback survey: User declined");
                }
            }

        });
    }

    protected IPropertyChangeListener propertyListener = new IPropertyChangeListener() {

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (event.getProperty().equals(
                PreferenceConstants.FEEDBACK_SURVEY_INTERVAL)) {
                /*
                 * each time the interval changes, reset the number of sessions
                 * until the next request is shown
                 */
                resetSessionsUntilNextToInterval();
            } else if (event.getProperty().equals(
                PreferenceConstants.FEEDBACK_SURVEY_DISABLED)) {
                Object value = event.getNewValue();
                int disabled = ((Integer) value).intValue();
                // if it changed to enabled, reset interval as well
                if (disabled == FEEDBACK_ENABLED) {
                    resetSessionsUntilNextToInterval();
                }
            }
        }

    };

    protected Date startTime;
    protected long sessionTime;

    public FeedbackManager(final Saros saros) {
        super(saros);
        // listen for feedback preference changes
        saros.getPreferenceStore().addPropertyChangeListener(propertyListener);
    }

    @Override
    protected void ensureConsistentPreferences() {
        makePrefConsistent(PreferenceConstants.FEEDBACK_SURVEY_DISABLED);
        makePrefConsistent(PreferenceConstants.FEEDBACK_SURVEY_INTERVAL);
    }

    /**
     * Number of sessions until the {@link FeedbackDialog} is shown.
     * 
     * @return a positive number if the value exists in the preferences or the
     *         default value -1
     */
    public int getSessionsUntilNext() {
        return saros.getConfigPrefs().getInt(
            PreferenceConstants.SESSIONS_UNTIL_NEXT, -1);
    }

    /**
     * Saves the number of session until the next {@link FeedbackDialog} is
     * shown in the global preferences.
     * 
     * @param untilNext
     */
    public void setSessionsUntilNext(int untilNext) {
        setSessionsUntilNext(saros, untilNext);
    }

    public static void setSessionsUntilNext(Saros saros, int untilNext) {
        if (untilNext < 0)
            untilNext = -1;
        saros.getConfigPrefs().putInt(PreferenceConstants.SESSIONS_UNTIL_NEXT,
            untilNext);
        saros.saveConfigPrefs();
    }

    /**
     * Returns whether the feedback is disabled or enabled by the user. The
     * global preferences have priority but if the value wasn't found there the
     * value from the PreferenceStore (with fall back to the default) is used.
     * 
     * @return 0 - undefined, 1 - enabled, 2 - disabled
     */
    public int getFeedbackStatus() {
        return getFeedbackStatus(saros);
    }

    /**
     * Returns whether the feedback is disabled or enabled by the user. The
     * global preferences have priority but if the value wasn't found there the
     * value from the PreferenceStore (with fall back to the default) is used.
     * 
     * @return 0 - undefined, 1 - enabled, 2 - disabled
     */
    public static int getFeedbackStatus(Saros saros) {
        int disabled = saros.getConfigPrefs().getInt(
            PreferenceConstants.FEEDBACK_SURVEY_DISABLED, -1);

        if (disabled == -1)
            disabled = saros.getPreferenceStore().getInt(
                PreferenceConstants.FEEDBACK_SURVEY_DISABLED);
        return disabled;
    }

    /**
     * Returns if the feedback is disabled as a boolean.
     * 
     * @return true if it is disabled
     */
    public boolean isFeedbackDisabled() {
        return isFeedbackDisabled(saros);
    }

    public static boolean isFeedbackDisabled(Saros saros) {
        return getFeedbackStatus(saros) != FEEDBACK_ENABLED;
    }

    /**
     * Saves in the global preferences and in the workspace if the feedback is
     * disabled or not. <br>
     * <br>
     * Note: It must be set globally first, so the PropertyChangeListener for
     * the local setting is working with latest global data.
     * 
     * @param disabled
     */
    public void setFeedbackDisabled(boolean disabled) {
        setFeedbackDisabled(saros, disabled);
    }

    /**
     * Saves in the global preferences and in the workspace if the feedback is
     * disabled or not. <br>
     * <br>
     * Note: It must be set globally first, so the PropertyChangeListener for
     * the local setting is working with latest global data.
     * 
     * @param disabled
     */
    public static void setFeedbackDisabled(Saros saros, boolean disabled) {
        int status = disabled ? FEEDBACK_DISABLED : FEEDBACK_ENABLED;

        saros.getConfigPrefs().putInt(
            PreferenceConstants.FEEDBACK_SURVEY_DISABLED, status);
        saros.saveConfigPrefs();
        saros.getPreferenceStore().setValue(
            PreferenceConstants.FEEDBACK_SURVEY_DISABLED, status);
    }

    /**
     * Returns the interval in which the survey should be shown. The global
     * preferences have priority but if the value wasn't found there the value
     * from the PreferenceStore (with fall back to the default) is used.
     * 
     * @return
     */
    public int getSurveyInterval() {
        return getSurveyInterval(saros);
    }

    /**
     * Returns the interval in which the survey should be shown. The global
     * preferences have priority but if the value wasn't found there the value
     * from the PreferenceStore (with fall back to the default) is used.
     * 
     * @return
     */
    public static int getSurveyInterval(Saros saros) {
        int interval = saros.getConfigPrefs().getInt(
            PreferenceConstants.FEEDBACK_SURVEY_INTERVAL, -1);

        if (interval == -1)
            interval = saros.getPreferenceStore().getInt(
                PreferenceConstants.FEEDBACK_SURVEY_INTERVAL);
        return interval;
    }

    /**
     * Stores the interval globally and per workspace.<br>
     * <br>
     * Note: It must be set globally first, so the PropertyChangeListener for
     * the local setting is working with latest global data.
     * 
     * @param interval
     */
    public void setSurveyInterval(int interval) {
        setSurveyInterval(saros, interval);
    }

    /**
     * Stores the interval globally and per workspace.<br>
     * <br>
     * Note: It must be set globally first, so the PropertyChangeListener for
     * the local setting is working with latest global data.
     * 
     * @param interval
     */
    public static void setSurveyInterval(Saros saros, int interval) {
        saros.getConfigPrefs().putInt(
            PreferenceConstants.FEEDBACK_SURVEY_INTERVAL, interval);
        saros.saveConfigPrefs();
        saros.getPreferenceStore().setValue(
            PreferenceConstants.FEEDBACK_SURVEY_INTERVAL, interval);
    }

    /**
     * Resets the counter of sessions until the survey request is shown the next
     * time to the current interval length.
     */
    public void resetSessionsUntilNextToInterval() {
        resetSessionsUntilNextToInterval(saros);
    }

    /**
     * Resets the counter of sessions until the survey request is shown the next
     * time to the current interval length.
     */
    public static void resetSessionsUntilNextToInterval(Saros saros) {
        setSessionsUntilNext(saros, getSurveyInterval(saros));
    }

    /**
     * Shows the FeedbackDialog with the given message and returns whether the
     * user answered it with yes or no and resets the sessions until the next
     * dialog is shown.
     * 
     * @param message
     * @return true, if the user clicked yes, otherwise false
     * 
     * @blocking
     */
    public boolean showFeedbackDialog(final String message) {
        resetSessionsUntilNextToInterval();

        try {
            return SWTUtils.runSWTSync(new Callable<Boolean>() {

                @Override
                public Boolean call() {
                    Dialog dialog = new FeedbackDialog(EditorAPI.getShell(),
                        saros, FeedbackManager.this, message);
                    return dialog.open() == Window.OK;
                }

            });
        } catch (Exception e) {
            log.error("Exception when trying to open FeedbackDialog.", e);
            return false;
        }
    }

    /**
     * Tries to open the survey in the default external browser. If this method
     * fails Eclipse's internal browser is tried to use. If both methods failed,
     * a message dialog that contains the survey URL is shown.<br>
     * <br>
     * The number of sessions until the next reminder is shown is reset to the
     * current interval length on every call.
     * 
     * @return which browser was used to open the survey as one of the
     *         FeedbackManagers constants (BROWSER_EXT, BROWSER_INT or
     *         BROWSER_NONE)
     */
    public static int showSurvey() {
        int browserType = BROWSER_EXT;

        if (!SWTUtils.openExternalBrowser(SURVEY_URL)) {
            browserType = BROWSER_INT;

            if (!SWTUtils.openInternalBrowser(SURVEY_URL,
                Messages.getString("feedback.dialog.title"))) {
                browserType = BROWSER_NONE;
                // last resort: present a link to the survey
                // TODO user should be able to copy&paste the link easily
                MessageDialog.openWarning(EditorAPI.getShell(),
                    "Opening survey failed",
                    "Your browser couldn't be opend. Please visit "
                        + SURVEY_URL + " yourself.");
            }
        }
        return browserType;
    }

    /**
     * Determines from the users preferences if the survey should be shown now.
     * 
     * @return true if the survey should be shown now, false otherwise
     */
    public boolean showNow() {
        if (isFeedbackDisabled()) {
            return false;
        }
        if (getSessionsUntilNext() > 0) {
            return false;
        }
        return true;
    }

    /**
     * Only for debugging.
     * 
     * @return
     */
    protected boolean debugSessionTime() {
        sessionTime = MIN_SESSION_TIME + 1;
        // Always returns true...
        return true;
    }

    /**
     * Convenience method to get a string that describes the given browser type.
     * 
     * @param browserType
     * @return a string that describes the browser
     */
    protected String getBrowserTypeAsString(int browserType) {
        String browser = "None";

        switch (browserType) {
        case FeedbackManager.BROWSER_EXT:
            browser = "external browser";
            break;
        case FeedbackManager.BROWSER_INT:
            browser = "internal browser";
            break;
        default:
            browser = "no browser";
        }
        return browser;
    }

}
