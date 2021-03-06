package de.fu_berlin.inf.dpp.project;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.joda.time.DateTime;

import de.fu_berlin.inf.dpp.FileList;
import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.activities.ProjectExchangeInfo;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.util.VersionManager.VersionInfo;

/**
 * An interface behind which the {@link SarosSessionManager} hides its
 * non-public methods.
 * 
 * The (I)SessionManager is responsible for providing a link between the
 * basically static world managed by PicoContainer where every class has just a
 * singleton instance which never changes and the {@link ISarosSession} which
 * can change many times during the course of the plug-in life-cycle.
 */
@Component(module = "net")
public interface ISarosSessionManager {

    /**
     * @return the active SarosSession object or <code>null</code> if there is
     *         no active session.
     */
    public ISarosSession getSarosSession();

    /**
     * Starts a new Saros session with the local user as only participant.
     * 
     * @param projectResources
     *            the local Eclipse project resources which should become
     *            shared.
     */
    public void startSession(Map<IProject, List<IResource>> projectResources);

    /**
     * Creates a Saros session. The returned session is NOT started!
     * 
     * @param host
     *            the host of the session.
     * @return the new Saros session.
     */
    public ISarosSession joinSession(JID host, int clientColor,
        DateTime sessionStart, JID inviter, int hostColor);

    /**
     * Leaves the currently active session. If the local user is the host, this
     * will close the session for everybody.
     * 
     * Has no effect if there is no open session.
     */
    public void stopSarosSession();

    /**
     * Add the given session listener.
     * 
     * @param listener
     *            the listener that is to be added.
     */
    public void addSarosSessionListener(ISarosSessionListener listener);

    /**
     * Removes the given session listener.
     * 
     * @param listener
     *            the listener that is to be removed.
     */
    public void removeSarosSessionListener(ISarosSessionListener listener);

    /**
     * Handles the negotiation process for a received invitation.
     * 
     * @param from
     *            the sender of this invitation
     * @param sessionID
     *            the unique session ID of the inviter side
     * @param invitationID
     *            a unique identifier for the negotiation process
     * @param sessionStart
     *            start time of the remote session
     * @param versionInfo
     *            information of the inviter side
     * @param description
     *            what this session invitation is about
     */
    public void invitationReceived(JID from, String sessionID,
        String invitationID, DateTime sessionStart, VersionInfo versionInfo,
        String description);

    /**
     * Will start sharing all projects of the current session with a
     * participant. This should be called after a the invitation to a session
     * was completed successfully.
     * 
     * @param user
     *            JID of session participant to share projects with
     */
    public void startSharingProjects(JID user);

    /**
     * Invites a user to the shared project.
     * 
     * @param toInvite
     *            the JID of the user that is to be invited.
     */
    public void invite(JID toInvite, String description);

    /**
     * Invites users to the shared project.
     * 
     * @param jidsToInvite
     *            the JIDs of the users that should be invited.
     */
    public void invite(Collection<JID> jidsToInvite, String description);

    /**
     * Adds project resources to an existing session.
     * 
     * @param projectResourcesMapping
     * 
     */
    public void addResourcesToSession(
        Map<IProject, List<IResource>> projectResourcesMapping);

    /**
     * This method is called when a new project was added to the session
     * 
     * @param from
     *            The one who added the project.
     * @param projectInfos
     *            what projects where added ({@link FileList}, projectName etc.)
     *            see: {@link ProjectExchangeInfo}
     * @param processID
     *            ID of the exchanging process
     */
    public void incomingProjectReceived(JID from,
        List<ProjectExchangeInfo> projectInfos, String processID);

    /**
     * Call this when a new project was added.
     * 
     * @param projectID
     *            TODO
     */
    void projectAdded(String projectID);

    /**
     * Call this before a ISarosSession is started.
     */
    void sessionStarting(ISarosSession sarosSession);

    /**
     * Call this after a ISarosSession has been started.
     */
    void sessionStarted(ISarosSession sarosSession);

    /**
     * Call this on the client after the invitation has been completed.
     */
    void preIncomingInvitationCompleted(IProgressMonitor monitor);

    /**
     * Call this on the host after the invitation was accepted and has been
     * completed.
     */
    void postOutgoingInvitationCompleted(IProgressMonitor monitor, User newUser);

    /**
     * Sets the {@link INegotiationHandler negotiation handler} that will handle
     * incoming and outgoing session and project negotiations requests.
     * 
     * @param handler
     *            the handler to handle the request or <code>null</code> if the
     *            requests should not be handled
     * 
     */
    public void setNegotiationHandler(INegotiationHandler handler);
}