/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universität Berlin - Fachbereich Mathematik und Informatik - 2006
 * (c) Riad Djemili - 2006
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
package de.fu_berlin.inf.dpp.project.internal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.joda.time.DateTime;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.ISarosContext;
import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.User.Permission;
import de.fu_berlin.inf.dpp.activities.SPath;
import de.fu_berlin.inf.dpp.activities.business.ChecksumActivity;
import de.fu_berlin.inf.dpp.activities.business.EditorActivity;
import de.fu_berlin.inf.dpp.activities.business.FileActivity;
import de.fu_berlin.inf.dpp.activities.business.FileActivity.Purpose;
import de.fu_berlin.inf.dpp.activities.business.FolderActivity;
import de.fu_berlin.inf.dpp.activities.business.IActivity;
import de.fu_berlin.inf.dpp.activities.business.IResourceActivity;
import de.fu_berlin.inf.dpp.activities.business.JupiterActivity;
import de.fu_berlin.inf.dpp.activities.business.NOPActivity;
import de.fu_berlin.inf.dpp.activities.business.TextSelectionActivity;
import de.fu_berlin.inf.dpp.activities.business.ViewportActivity;
import de.fu_berlin.inf.dpp.activities.serializable.IActivityDataObject;
import de.fu_berlin.inf.dpp.concurrent.management.ConcurrentDocumentClient;
import de.fu_berlin.inf.dpp.concurrent.management.ConcurrentDocumentServer;
import de.fu_berlin.inf.dpp.concurrent.watchdog.ConsistencyWatchdogHandler;
import de.fu_berlin.inf.dpp.editor.EditorManager;
import de.fu_berlin.inf.dpp.feedback.DataTransferCollector;
import de.fu_berlin.inf.dpp.feedback.ErrorLogManager;
import de.fu_berlin.inf.dpp.feedback.FeedbackManager;
import de.fu_berlin.inf.dpp.feedback.FollowModeCollector;
import de.fu_berlin.inf.dpp.feedback.JumpFeatureUsageCollector;
import de.fu_berlin.inf.dpp.feedback.ParticipantCollector;
import de.fu_berlin.inf.dpp.feedback.PermissionChangeCollector;
import de.fu_berlin.inf.dpp.feedback.ProjectCollector;
import de.fu_berlin.inf.dpp.feedback.SelectionCollector;
import de.fu_berlin.inf.dpp.feedback.SessionDataCollector;
import de.fu_berlin.inf.dpp.feedback.StatisticManager;
import de.fu_berlin.inf.dpp.feedback.TextEditCollector;
import de.fu_berlin.inf.dpp.feedback.VoIPCollector;
import de.fu_berlin.inf.dpp.invitation.ProjectNegotiation;
import de.fu_berlin.inf.dpp.net.ITransmitter;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.SarosNet;
import de.fu_berlin.inf.dpp.net.internal.DataTransferManager;
import de.fu_berlin.inf.dpp.net.internal.extensions.KickUserExtension;
import de.fu_berlin.inf.dpp.net.internal.extensions.SarosLeaveExtension;
import de.fu_berlin.inf.dpp.observables.ProjectNegotiationObservable;
import de.fu_berlin.inf.dpp.observables.SessionIDObservable;
import de.fu_berlin.inf.dpp.preferences.PreferenceUtils;
import de.fu_berlin.inf.dpp.project.IActivityListener;
import de.fu_berlin.inf.dpp.project.IActivityProvider;
import de.fu_berlin.inf.dpp.project.ISarosSession;
import de.fu_berlin.inf.dpp.project.ISharedProjectListener;
import de.fu_berlin.inf.dpp.project.SharedProject;
import de.fu_berlin.inf.dpp.project.SharedResourcesManager;
import de.fu_berlin.inf.dpp.synchronize.StopManager;
import de.fu_berlin.inf.dpp.synchronize.UISynchronizer;
import de.fu_berlin.inf.dpp.ui.util.CollaborationUtils;
import de.fu_berlin.inf.dpp.util.ArrayUtils;
import de.fu_berlin.inf.dpp.util.StackTrace;
import de.fu_berlin.inf.dpp.util.Utils;

/**
 * TODO Review if SarosSession, ConcurrentDocumentManager, ActivitySequencer all
 * honor start() and stop() semantics.
 */
public final class SarosSession implements ISarosSession {

    private static final Logger log = Logger.getLogger(SarosSession.class);

    public static final int MAX_USERCOLORS = 5;

    @Inject
    private UISynchronizer synchronizer;

    /* Dependencies */
    @Inject
    private Saros saros;

    @Inject
    private ITransmitter transmitter;

    @Inject
    private SarosNet sarosNet;

    @Inject
    private PreferenceUtils preferenceUtils;

    @Inject
    private DataTransferManager transferManager;

    @Inject
    private ProjectNegotiationObservable projectNegotiationObservable;

    @Inject
    private EditorManager editorManager;

    private final ISarosContext sarosContext;

    private final ConcurrentDocumentClient concurrentDocumentClient;

    private final ConcurrentDocumentServer concurrentDocumentServer;

    private final ActivityHandler activityHandler;

    private final CopyOnWriteArrayList<IActivityProvider> activityProviders = new CopyOnWriteArrayList<IActivityProvider>();

    /* Instance fields */
    private final User localUser;

    private final ConcurrentHashMap<JID, User> participants = new ConcurrentHashMap<JID, User>();

    private final SharedProjectListenerDispatch listenerDispatch = new SharedProjectListenerDispatch();

    private final User hostUser;

    private final DateTime sessionStart;

    private final SarosProjectMapper projectMapper;

    private boolean useVersionControl = true;

    // KARL HELD YOU ARE MY WTF GUY !!!
    private List<IResource> selectedResources = new ArrayList<IResource>();

    /** Files shared with NeedBased feature **/
    private Set<SPath> needBasedPathsList = new HashSet<SPath>();

    private final MutablePicoContainer sessionContainer;

    private final StopManager stopManager;

    private final ChangeColorManager changeColorManager;

    private final PermissionManager permissionManager;

    private final ActivitySequencer activitySequencer;

    private final UserInformationHandler userListHandler;

    private final String sessionID;

    private boolean started = false;
    private boolean stopped = false;

    private final ActivityQueuer activityQueuer;

    private final IActivityListener activityListener = new IActivityListener() {

        /**
         * @JTourBusStop 5, Activity sending, Forwarding the IActivity:
         * 
         *               This is where the SarosSession will receive the
         *               activity, it is not part of the ISarosSession interface
         *               to avoid misuse.
         */
        @Override
        public void activityCreated(final IActivity activity) {
            if (activity == null)
                throw new NullPointerException("activity is null");

            activityHandler.handleOutgoingActivities(Collections
                .singletonList(activity));
        }
    };

    private final IActivityHandlerCallback activityCallback = new IActivityHandlerCallback() {

        @Override
        public void send(List<User> recipients, IActivity activity) {
            sendActivity(recipients, activity);
        }

        @Override
        public void execute(IActivity activity) {
            for (IActivityProvider executor : activityProviders) {
                executor.exec(activity);
                updatePartialSharedResources(activity);
            }
        }
    };

    /**
     * Constructor for host.
     */
    public SarosSession(int localColorID, DateTime sessionStart,
        ISarosContext sarosContext) {

        this(sarosContext, sessionStart, /* unused */null, localColorID, /* unused */
        -1);
    }

    /**
     * Constructor for client.
     */
    public SarosSession(JID hostJID, int localColorID, DateTime sessionStart,
        ISarosContext sarosContext, JID inviterID, int inviterColorID) {

        this(sarosContext, sessionStart, hostJID, localColorID, inviterColorID);

        assert inviterID.equals(hostJID) : "non host inviting is disabled";
    }

    @Override
    public void addSharedResources(IProject project, String projectID,
        List<IResource> dependentResources) {
        if (!isCompletelyShared(project) && dependentResources != null) {
            for (IResource iResource : dependentResources) {
                if (iResource instanceof IFolder) {
                    addMembers(iResource, dependentResources);
                }
            }
            if (selectedResources != null) {
                selectedResources.removeAll(dependentResources);
                dependentResources.addAll(selectedResources);
                selectedResources.clear();
            }
        }

        if (!projectMapper.isShared(project)) {
            projectMapper.addProject(projectID, project,
                dependentResources != null);

            projectMapper.addOwnership(getLocalUser().getJID(), project);

            if (dependentResources != null)
                projectMapper.addResources(project, dependentResources);

        } else {
            if (dependentResources == null)
                // upgrade the project to a completely shared project
                projectMapper.addProject(projectID, project, false);
            else
                projectMapper.addResources(project, dependentResources);
        }
    }

    @Override
    public List<User> getUsers() {
        return new ArrayList<User>(participants.values());
    }

    @Override
    public List<User> getRemoteUsersWithReadOnlyAccess() {
        List<User> result = new ArrayList<User>();
        for (User user : getUsers()) {
            if (user.isLocal())
                continue;
            if (user.hasReadOnlyAccess())
                result.add(user);
        }
        return result;
    }

    @Override
    public List<User> getUsersWithReadOnlyAccess() {
        List<User> result = new ArrayList<User>();
        for (User user : getUsers()) {
            if (user.hasReadOnlyAccess())
                result.add(user);
        }
        return result;
    }

    @Override
    public List<User> getUsersWithWriteAccess() {
        List<User> result = new ArrayList<User>();
        for (User user : getUsers()) {
            if (user.hasWriteAccess())
                result.add(user);
        }
        return result;
    }

    public List<User> getRemoteUsersWithWriteAccess() {
        List<User> result = new ArrayList<User>();
        for (User user : getUsers()) {
            if (user.isLocal())
                continue;
            if (user.hasWriteAccess())
                result.add(user);
        }
        return result;
    }

    @Override
    public List<User> getRemoteUsers() {
        List<User> result = new ArrayList<User>();
        for (User user : getUsers()) {
            if (user.isRemote())
                result.add(user);
        }
        return result;
    }

    @Override
    public boolean userHasProject(User user, IProject project) {
        return projectMapper.userHasProject(user, project);
    }

    @Override
    public void initiatePermissionChange(final User user,
        final Permission newPermission, IProgressMonitor progress)
        throws CancellationException, InterruptedException {

        if (!localUser.isHost())
            throw new IllegalStateException(
                "only the host can initiate permission changes");

        permissionManager.initiatePermissionChange(user, newPermission,
            progress, synchronizer);
    }

    @Override
    public void setPermission(final User user, final Permission permission) {

        if (user == null || permission == null)
            throw new IllegalArgumentException();

        synchronizer.syncExec(Utils.wrapSafe(log, new Runnable() {
            @Override
            public void run() {
                user.setPermission(permission);
                listenerDispatch.permissionChanged(user);
            }
        }));

        log.info("user " + user + " is now a " + permission);
    }

    /**
     * Returns the id of the current session.
     * 
     * @return the id of the current session
     */
    public String getID() {
        return sessionID;
    }

    @Override
    public User getHost() {
        return hostUser;
    }

    @Override
    public boolean isHost() {
        return localUser.isHost();
    }

    @Override
    public boolean hasWriteAccess() {
        return localUser.hasWriteAccess();
    }

    @Override
    public boolean hasExclusiveWriteAccess() {
        if (!hasWriteAccess()) {
            return false;
        }
        for (User user : getUsers()) {
            if (user.isRemote() && user.hasWriteAccess()) {
                return false;
            }
        }
        return true;
    }

    /*
     * FIXME only accept a JID or create a method session.createUser to ensure
     * proper initialization etc. of User objects !
     */
    @Override
    public void addUser(final User user) {

        // TODO synchronize this method !

        JID jid = user.getJID();
        user.setInSession(true);
        if (participants.putIfAbsent(jid, user) != null) {
            log.error("user " + Utils.prefix(jid)
                + " added twice to SarosSession", new StackTrace());
            throw new IllegalArgumentException();
        }

        /*
         * 
         * as long as we do not know when something is send to someone this will
         * always produce errors ... swapping synchronizeUserList and userJoined
         * can produce different results
         */

        if (isHost()) {

            activitySequencer.registerUser(user);

            List<User> timedOutUsers = userListHandler.synchronizeUserList(
                getUsers(), null, getRemoteUsers());

            if (!timedOutUsers.isEmpty()) {
                activitySequencer.unregisterUser(user);
                participants.remove(jid);
                // FIXME do not throw a runtime exception here
                throw new RuntimeException(
                    "could not synchronize user list, following users did not respond: "
                        + StringUtils.join(timedOutUsers, ", "));
            }
        }

        synchronizer.syncExec(Utils.wrapSafe(log, new Runnable() {
            @Override
            public void run() {
                listenerDispatch.userJoined(user);
            }
        }));

        log.info("user " + Utils.prefix(jid) + " joined session");
    }

    @Override
    public void userStartedQueuing(final User user) {

        log.info("user " + user
            + " started queuing projects and can receive IResourceActivities");

        /**
         * Updates the projects for the given user, so that host knows that he
         * can now send ever Activity
         */
        projectMapper.addMissingProjectsToUser(user);

        synchronizer.syncExec(Utils.wrapSafe(log, new Runnable() {
            @Override
            public void run() {
                listenerDispatch.userStartedQueuing(user);
            }
        }));
    }

    @Override
    public void userFinishedProjectNegotiation(final User user) {

        log.info("user " + user
            + " now has Projects and can process IResourceActivities");

        synchronizer.syncExec(Utils.wrapSafe(log, new Runnable() {
            @Override
            public void run() {
                listenerDispatch.userFinishedProjectNegotiation(user);
            }
        }));

        if (isHost()) {

            JID jid = user.getJID();
            /**
             * This informs all participants, that a user is now able to process
             * IRessourceActivities. After receiving this message the
             * participants will send their awareness-informations.
             */
            userListHandler.sendUserFinishedProjectNegotiation(
                getRemoteUsers(), jid);
        }
    }

    @Override
    public void removeUser(final User user) {
        JID jid = user.getJID();
        if (participants.remove(jid) == null) {
            log.warn("tried to remove user who was not in participants:"
                + Utils.prefix(jid));
            return;
        }

        user.setInSession(false);

        activitySequencer.unregisterUser(user);

        projectMapper.userLeft(user);

        List<User> currentRemoteUsers = getRemoteUsers();

        if (isHost() && !currentRemoteUsers.isEmpty()) {

            List<User> timedOutUsers = userListHandler.synchronizeUserList(
                null, Collections.singletonList(user), currentRemoteUsers);

            if (!timedOutUsers.isEmpty()) {
                log.error("could not synchronize user list properly, following users did not respond: "
                    + StringUtils.join(timedOutUsers, ", "));
            }
        }

        synchronizer.syncExec(Utils.wrapSafe(log, new Runnable() {
            @Override
            public void run() {
                listenerDispatch.userLeft(user);
            }
        }));

        // TODO what is to do here if no user with write access exists anymore?

        // Disconnect bytestream connection when user leaves session to
        // prevent idling connection when not needed anymore.
        transferManager.closeConnection(ISarosSession.SESSION_CONNECTION_ID,
            jid);

        log.info("user " + Utils.prefix(jid) + " left session");
    }

    @Override
    public void kickUser(final User user) {

        if (!isHost())
            throw new IllegalStateException(
                "only the host can kick users from the current session");

        if (user.equals(getLocalUser()))
            throw new IllegalArgumentException(
                "the local user cannot kick itself out of the session");

        try {
            transmitter.sendToSessionUser(SESSION_CONNECTION_ID, user.getJID(),
                KickUserExtension.PROVIDER
                    .create(new KickUserExtension(getID())));
        } catch (IOException e) {
            log.warn("could not kick user "
                + user
                + " from the session because the connection to the user is already lost");
        }

        removeUser(user);
    }

    @Override
    public void addListener(ISharedProjectListener listener) {
        listenerDispatch.add(listener);
    }

    @Override
    public void removeListener(ISharedProjectListener listener) {
        listenerDispatch.remove(listener);
    }

    @Override
    public Set<IProject> getProjects() {
        return projectMapper.getProjects();
    }

    // FIMXE synchronization
    @Override
    public void start() {
        if (started || stopped) {
            throw new IllegalStateException();
        }

        started = true;
        sessionContainer.start();

        for (User user : getRemoteUsers())
            activitySequencer.registerUser(user);

    }

    // FIMXE synchronization
    @Override
    public void stop() {
        if (!started || stopped) {
            throw new IllegalStateException();
        }

        stopped = true;
        sarosContext.removeChildContainer(sessionContainer);
        sessionContainer.stop();
        sessionContainer.dispose();

        List<User> usersToNotify;

        if (isHost())
            usersToNotify = getRemoteUsers();
        else
            usersToNotify = Collections.singletonList(getHost());

        for (User user : usersToNotify) {
            try {
                transmitter.sendToSessionUser(SESSION_CONNECTION_ID, user
                    .getJID(), SarosLeaveExtension.PROVIDER
                    .create(new SarosLeaveExtension(getID())));
            } catch (IOException e) {
                log.warn("failed to notify user " + user
                    + " about local session stop", e);
            }
        }

        for (User user : getRemoteUsers())
            transferManager.closeConnection(
                ISarosSession.SESSION_CONNECTION_ID, user.getJID());
    }

    @Override
    public User getUser(JID jid) {

        if (jid == null)
            throw new IllegalArgumentException("jid is null");

        if (jid.isBareJID())
            throw new IllegalArgumentException(
                "JID is not resource qualified: " + jid);

        User user = participants.get(jid);

        if (user == null || !user.getJID().strictlyEquals(jid))
            return null;

        return user;
    }

    @Override
    public JID getResourceQualifiedJID(JID jid) {

        if (jid == null)
            throw new IllegalArgumentException("jid is null");

        User user = participants.get(jid);

        if (user == null)
            return null;

        return user.getJID();
    }

    @Override
    public User getLocalUser() {
        return localUser;
    }

    @Override
    public ConcurrentDocumentClient getConcurrentDocumentClient() {
        return concurrentDocumentClient;
    }

    @Override
    public ConcurrentDocumentServer getConcurrentDocumentServer() {
        if (!isHost())
            throw new IllegalStateException(
                "the session is running in client mode");

        return concurrentDocumentServer;
    }

    /**
     * @JTourBusStop 7, Activity sending, Incoming activities:
     * 
     *               The ActivitySequencer will call this function for new
     *               activities, they are transformed again, forwarded and put
     *               into the queue of the activity dispatcher.
     * 
     */

    @Override
    public void exec(List<IActivityDataObject> ados) {
        final List<IActivity> activities = new ArrayList<IActivity>();

        for (IActivityDataObject ado : activityQueuer.process(ados)) {
            try {
                activities.add(ado.getActivity(this));
            } catch (IllegalArgumentException e) {
                log.error("could not deserialize activity data object: " + ado,
                    e);
            }
        }

        activityHandler.handleIncomingActivities(activities);
    }

    /*
     * FIXME most (if not all checks) to send or not activities should be
     * handled by the activity handler and not here !
     */
    private void sendActivity(final List<User> recipients,
        final IActivity activity) {
        if (recipients == null)
            throw new IllegalArgumentException();

        if (activity == null)
            throw new IllegalArgumentException();
        /*
         * If we don't have any sharedProjects don't send File-, Folder- or
         * EditorActivities.
         */
        if (projectMapper.size() == 0
            && (activity instanceof EditorActivity
                || activity instanceof FolderActivity || activity instanceof FileActivity)) {
            return;
        }

        /*
         * If the need based synchronization is not disabled, process this
         * JupiterActivity.
         */
        if (activity instanceof JupiterActivity)
            needBasedSynchronization(((JupiterActivity) activity), recipients);

        // avoid consistency control during project negotiation to relieve the
        // general transmission process
        if (isInProjectNegotiation(activity)
            && activity instanceof ChecksumActivity)
            return;

        // avoid sending of unwanted editor related activities

        if (activity instanceof IResourceActivity
            && (activity instanceof TextSelectionActivity
                || activity instanceof ViewportActivity || activity instanceof JupiterActivity)) {
            IResourceActivity resActivity = (IResourceActivity) activity;
            if (!needBasedPathsList.contains(resActivity.getPath())
                && !isShared(resActivity.getPath().getResource()))
                return;
        }

        boolean send = true;
        // handle FileActivities and FolderActivities to update ProjectMapper
        if (activity instanceof FolderActivity
            || activity instanceof FileActivity) {
            send = updatePartialSharedResources(activity);
        }

        if (!send)
            return;

        try {
            activitySequencer.sendActivity(recipients,
                activity.getActivityDataObject(this));
        } catch (IllegalArgumentException e) {
            log.warn("could not serialize activity: " + activity, e);
        }
    }

    /**
     * Convenient method to determine if Project of given {@link IActivity} is
     * currently transmitted.
     * 
     * @param activity
     * @return <b>true</b> if the activity is send during a project transmission<br>
     *         <b>false</b> if no transmission is running
     */
    private boolean isInProjectNegotiation(IActivity activity) {
        if (activity == null)
            throw new IllegalArgumentException();

        if (!(activity instanceof IResourceActivity))
            return false;

        SPath path = ((IResourceActivity) activity).getPath();
        if (path == null)
            return false;

        // determine if we are in a transmission process with our project
        Collection<ProjectNegotiation> projectNegotiations = projectNegotiationObservable
            .getProcesses().values();
        Set<String> projectIDs = null;
        for (ProjectNegotiation projectNegotiation : projectNegotiations) {
            projectIDs = projectNegotiation.getProjectNames().keySet();
        }
        if (projectIDs != null) {
            return projectIDs.contains(getProjectID(path.getProject()));
        }
        return false;
    }

    /**
     * Method to enable the need based sync.
     * 
     * @param jupiterActivity
     *            {@link JupiterActivity} that triggers the need based
     *            synchronization
     * @param toWhom
     */
    private void needBasedSynchronization(JupiterActivity jupiterActivity,
        List<User> toWhom) {

        if (jupiterActivity == null)
            throw new IllegalArgumentException();

        final SPath path = jupiterActivity.getPath();
        final IProject project = path.getProject();
        final String needBasedSetting = preferenceUtils
            .isNeedsBasedSyncEnabled().toLowerCase();

        if (!isOwnedProject(project))
            return;

        if (isShared(path.getFile()))
            return;

        /* FIMXE CONSTANT ! */
        if (needBasedSetting.equals("false"))
            return;

        if (needBasedPathsList.contains(path))
            return;

        /* FIMXE CONSTANT ! */
        if (needBasedSetting.equals("undefined")
        /* FIMXE opens dialog & static* method ! */
        && (!CollaborationUtils.activateNeedBasedSynchronization(saros)))
            return;

        needBasedPathsList.add(path);

        try {
            sendSingleFile(path);
        } catch (IOException e) {
            needBasedPathsList.remove(path);
            log.error("file could not be found or read, despite existing: "
                + path, e);
        }
    }

    /**
     * 
     * This Method enables a reliable way to automatically synchronize single
     * Files to all other session participants.
     * 
     * @param path
     *            identifies the file to synchronize to all session participants
     * @throws IOException
     *             if the file determined by the path could not be read
     * @throws FileNotFoundException
     *             if the file determined by the path does not exist
     */
    private void sendSingleFile(final SPath path) throws IOException {

        if (path == null)
            throw new IllegalArgumentException("path is null");

        final FileActivity needBasedFileActivity = FileActivity.created(
            getLocalUser(), path, Purpose.NEEDS_BASED_SYNC);

        synchronizer.syncExec(Utils.wrapSafe(log, new Runnable() {

            @Override
            public void run() {
                for (final User recipient : getRemoteUsers()) {

                    if (isHost())
                        concurrentDocumentServer.reset(recipient.getJID(), path);
                    else
                        concurrentDocumentClient.reset(path);

                    // TODO do not bypass the ActivityHandler !
                    sendActivity(Collections.singletonList(recipient),
                        needBasedFileActivity);

                    /*
                     * Notify the session participants of your activated editor.
                     * This is mandatory to avoid inconsistencies with the
                     * editor manager and by that with the follow mode.
                     */
                    editorManager.sendPartActivated();
                }
            }
        }));
    }

    /**
     * Method to update the project mapper when changes on shared files oder
     * folders happened.
     * 
     * @param activity
     *            {@link FileActivity} or {@link FolderActivity} to handle
     * @return <code>true</code> if the activity should be send to the user,
     *         <code>false</code> otherwise
     */
    protected boolean updatePartialSharedResources(IActivity activity) {
        if (!(activity instanceof FileActivity)
            && !(activity instanceof FolderActivity))
            return true;

        if (activity instanceof FileActivity) {
            FileActivity fileActivity = ((FileActivity) activity);
            SPath path = fileActivity.getPath();
            IFile file = path.getFile();

            if (isInProjectNegotiation(fileActivity)
                && !fileActivity.isNeedBased()) {
                return false;
            }

            if (file == null)
                return true;

            IProject project = file.getProject();

            switch (fileActivity.getType()) {
            case CREATED:
                if (!file.exists())
                    return true;

                if (projectMapper.isPartiallyShared(project))
                    projectMapper.addResources(project,
                        Collections.singletonList(file));
                break;
            case REMOVED:
                if (!isShared(file))
                    return false;

                if (projectMapper.isPartiallyShared(project))
                    projectMapper.removeResources(project,
                        Collections.singletonList(file));

                break;
            case MOVED:
                IFile oldFile = fileActivity.getOldPath().getFile();
                if (oldFile == null || !isShared(oldFile))
                    return false;

                if (projectMapper.isPartiallyShared(project)) {
                    projectMapper.removeAndAddResources(project,
                        Collections.singletonList(oldFile),
                        Collections.singletonList(file));
                }

                break;
            }
        } else if (activity instanceof FolderActivity) {
            FolderActivity folderActivity = ((FolderActivity) activity);
            IFolder folder = folderActivity.getPath().getFolder();

            if (folder == null)
                return true;

            IProject project = folder.getProject();

            switch (folderActivity.getType()) {
            case CREATED:
                if (projectMapper.isPartiallyShared(project)
                    && isShared(folder.getParent()))
                    projectMapper.addResources(project,
                        Collections.singletonList(folder));
                break;
            case REMOVED:
                if (!isShared(folder))
                    return false;

                if (projectMapper.isPartiallyShared(project))
                    projectMapper.removeResources(project,
                        Collections.singletonList(folder));
            }
        }
        return true;
    }

    @Override
    public void addActivityProvider(IActivityProvider provider) {
        if (activityProviders.addIfAbsent(provider))
            provider.addActivityListener(this.activityListener);
    }

    @Override
    public void removeActivityProvider(IActivityProvider provider) {
        activityProviders.remove(provider);
        provider.removeActivityListener(this.activityListener);
    }

    @Override
    public DateTime getSessionStart() {
        return sessionStart;
    }

    @Override
    public boolean isShared(IResource resource) {
        return projectMapper.isShared(resource);
    }

    @Override
    public List<IResource> getSharedResources() {
        return projectMapper.getPartiallySharedResources();
    }

    private void addMembers(IResource iResource,
        List<IResource> dependentResources) {
        if (iResource instanceof IFolder || iResource instanceof IProject) {

            if (!isShared(iResource)) {
                selectedResources.add(iResource);
            } else {
                return;
            }
            List<IResource> childResources = null;
            try {
                childResources = ArrayUtils.getAdaptableObjects(
                    ((IContainer) iResource).members(), IResource.class,
                    Platform.getAdapterManager());
            } catch (CoreException e) {
                log.debug("Can't get children of Project/Folder. ", e);
            }
            if (childResources != null && (childResources.size() > 0)) {
                for (IResource childResource : childResources) {
                    addMembers(childResource, dependentResources);
                }
            }
        } else if (iResource instanceof IFile) {
            if (!isShared(iResource)) {
                selectedResources.add(iResource);
            }
        }
    }

    @Override
    public boolean useVersionControl() {
        /*
         * It is not possible to enable version control support during a
         * session.
         */
        if (!useVersionControl)
            return false;
        return useVersionControl = preferenceUtils.useVersionControl();
    }

    @Override
    public SharedProject getSharedProject(IProject project) {
        if (!isShared(project))
            return null;
        return projectMapper.getSharedProject(projectMapper.getID(project));
    }

    @Override
    public List<SharedProject> getSharedProjects() {
        return projectMapper.getSharedProjects();
    }

    @Override
    public String getProjectID(IProject project) {
        return projectMapper.getID(project);
    }

    @Override
    public IProject getProject(String projectID) {
        return projectMapper.getProject(projectID);
    }

    @Override
    public Map<IProject, List<IResource>> getProjectResourcesMapping() {
        return projectMapper.getProjectResourceMapping();
    }

    @Override
    public List<IResource> getSharedResources(IProject project) {
        return projectMapper.getProjectResourceMapping().get(project);
    }

    @Override
    public boolean isCompletelyShared(IProject project) {
        return projectMapper.isCompletelyShared(project);
    }

    private boolean isOwnedProject(IProject iProject) {
        List<IProject> ownedProjects = projectMapper
            .getOwnedProjects(getLocalUser().getJID());

        if (ownedProjects == null)
            return false;

        return ownedProjects.contains(iProject);
    }

    @Override
    public void addProjectOwnership(String projectID, IProject project,
        JID ownerJID) {
        if (projectMapper.getSharedProject(projectID) == null) {
            projectMapper.addProject(projectID, project, true);
            projectMapper.addOwnership(ownerJID, project);
        }
    }

    @Override
    public void removeProjectOwnership(String projectID, IProject project,
        JID ownerJID) {
        if (projectMapper.getSharedProject(projectID) != null) {
            projectMapper.removeOwnership(ownerJID, project);
            projectMapper.removeProject(projectID);
        }
    }

    @Override
    public StopManager getStopManager() {
        return stopManager;
    }

    @Override
    public void changeColor(int colorID) {
        if (colorID < 0 || colorID >= MAX_USERCOLORS)
            throw new IllegalArgumentException("color id '" + colorID
                + "'  must be in range of 0 <= id < " + MAX_USERCOLORS);

        changeColorManager.changeColorID(colorID);
    }

    @Override
    public Set<Integer> getAvailableColors() {
        return changeColorManager.getAvailableColors();
    }

    @Override
    public void enableQueuing(String projectId) {
        activityQueuer.enableQueuing(projectId);
    }

    @Override
    public void disableQueuing() {
        activityQueuer.disableQueuing();
        // send us a dummy activity to ensure the queues get flushed
        sendActivity(Collections.singletonList(localUser), new NOPActivity(
            localUser, localUser, 0));
    }

    private SarosSession(ISarosContext context, DateTime sessionStart,
        JID host, int localColorID, int hostColorID) {

        context.initComponent(this);
        this.sessionID = context.getComponent(SessionIDObservable.class)
            .getValue();
        this.projectMapper = new SarosProjectMapper(this);
        this.activityQueuer = new ActivityQueuer();
        this.sarosContext = context;
        this.sessionStart = sessionStart;

        // FIXME that should be passed in !
        JID localUserJID = sarosNet.getMyJID();

        assert localUserJID != null;

        localUser = new User(localUserJID, host == null, true, localColorID,
            localColorID);

        localUser.setInSession(true);

        if (host == null) {
            hostUser = localUser;
            participants.put(hostUser.getJID(), hostUser);
        } else {
            hostUser = new User(host, true, false, hostColorID, hostColorID);
            hostUser.setInSession(true);
            participants.put(hostUser.getJID(), hostUser);
            participants.put(localUser.getJID(), localUser);
        }

        sessionContainer = context.createSimpleChildContainer();
        sessionContainer.addComponent(ISarosSession.class, this);
        sessionContainer.addComponent(StopManager.class);
        sessionContainer.addComponent(ActivitySequencer.class);

        // Concurrent Editing

        sessionContainer.addComponent(ConcurrentDocumentClient.class);
        /*
         * as Pico Container complains about null, just add the server even in
         * client mode as it will not matter because it is not accessed
         */
        sessionContainer.addComponent(ConcurrentDocumentServer.class);

        // Classes belonging to a session

        // Core Managers
        sessionContainer.addComponent(ChangeColorManager.class);
        sessionContainer.addComponent(SharedResourcesManager.class);
        sessionContainer.addComponent(PermissionManager.class);

        // Statistic collectors. Make sure to add new collectors to the
        // StatisticCollectorTest as well
        sessionContainer.addComponent(StatisticManager.class);
        sessionContainer.addComponent(DataTransferCollector.class);
        sessionContainer.addComponent(PermissionChangeCollector.class);
        sessionContainer.addComponent(ParticipantCollector.class);
        sessionContainer.addComponent(SessionDataCollector.class);
        sessionContainer.addComponent(TextEditCollector.class);
        sessionContainer.addComponent(JumpFeatureUsageCollector.class);
        sessionContainer.addComponent(FollowModeCollector.class);
        sessionContainer.addComponent(SelectionCollector.class);
        sessionContainer.addComponent(VoIPCollector.class);
        sessionContainer.addComponent(ProjectCollector.class);

        // Feedback
        sessionContainer.addComponent(ErrorLogManager.class);
        sessionContainer.addComponent(FeedbackManager.class);

        // Handlers
        sessionContainer.addComponent(ConsistencyWatchdogHandler.class);
        // transforming - thread access
        sessionContainer.addComponent(ActivityHandler.class);
        sessionContainer.addComponent(IActivityHandlerCallback.class,
            activityCallback);
        sessionContainer.addComponent(UserInformationHandler.class);

        // Force the creation of the above components.
        sessionContainer.getComponents();

        concurrentDocumentServer = sessionContainer
            .getComponent(ConcurrentDocumentServer.class);

        concurrentDocumentClient = sessionContainer
            .getComponent(ConcurrentDocumentClient.class);

        activityHandler = sessionContainer.getComponent(ActivityHandler.class);

        stopManager = sessionContainer.getComponent(StopManager.class);

        changeColorManager = sessionContainer
            .getComponent(ChangeColorManager.class);

        permissionManager = sessionContainer
            .getComponent(PermissionManager.class);

        activitySequencer = sessionContainer
            .getComponent(ActivitySequencer.class);

        userListHandler = sessionContainer
            .getComponent(UserInformationHandler.class);

        // ensure that the container uses caching
        assert sessionContainer.getComponent(ActivityHandler.class) == sessionContainer
            .getComponent(ActivityHandler.class) : "container is wrongly configurated - no cache support";
    }

    /**
     * This method is only meant to be used by a unit tests to verify the
     * cleanup of activity providers.
     * 
     * @return the size of the internal activity providers collection
     */
    public int getActivityProviderCount() {
        return activityProviders.size();
    }
}
