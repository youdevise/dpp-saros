package de.fu_berlin.inf.dpp.project.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.picocontainer.Startable;

import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.activities.business.AbstractActivityReceiver;
import de.fu_berlin.inf.dpp.activities.business.ChangeColorActivity;
import de.fu_berlin.inf.dpp.activities.business.IActivity;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.editor.EditorManager;
import de.fu_berlin.inf.dpp.editor.colorstorage.ColorIDSet;
import de.fu_berlin.inf.dpp.editor.colorstorage.ColorIDSetStorage;
import de.fu_berlin.inf.dpp.editor.colorstorage.UserColorID;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.project.AbstractActivityProvider;
import de.fu_berlin.inf.dpp.project.AbstractSharedProjectListener;
import de.fu_berlin.inf.dpp.project.ISarosSession;
import de.fu_berlin.inf.dpp.project.ISharedProjectListener;

/**
 * This manager is responsible for handling color changes and managing the
 * currently available colors.
 * 
 * @author Stefan Rossbach
 */
@Component(module = "core")
public class ChangeColorManager extends AbstractActivityProvider implements
    Startable {

    private static final Logger log = Logger
        .getLogger(ChangeColorManager.class);

    private final ISarosSession sarosSession;
    private final EditorManager editorManager;
    private final ColorIDSetStorage colorIDSetStorage;

    private final Map<User, Integer> favoriteUserColors = new LinkedHashMap<User, Integer>();

    /*
     * As it is possible that the same color id is taken during invitation
     * multiple times we must use a counter for each id.
     */
    private final int[] availableColors;

    private final AbstractActivityReceiver receiver = new AbstractActivityReceiver() {

        @Override
        public void receive(ChangeColorActivity activity) {
            handleChangeColorActivity(activity);
        }
    };

    private final ISharedProjectListener sessionListener = new AbstractSharedProjectListener() {
        @Override
        public void userJoined(User user) {

            synchronized (ChangeColorManager.this) {
                favoriteUserColors.put(user, user.getFavoriteColorID());
            }

            if (!sarosSession.isHost()) {
                // just remove the color, the host will send the correction
                removeColorIdFromPool(user.getColorID());
                return;
            }

            reassignSessionColorIDs(sarosSession.getUsers(), user, true);
        }

        @Override
        public void userLeft(User user) {

            synchronized (ChangeColorManager.this) {
                favoriteUserColors.remove(user);
            }

            if (!sarosSession.isHost()) {
                addColorIdToPool(user.getColorID());
                return;
            }

            reassignSessionColorIDs(sarosSession.getUsers(), user, false);
        }
    };

    public ChangeColorManager(ISarosSession sarosSession,
        EditorManager editorManager, ColorIDSetStorage colorIDSetStorage) {
        this.sarosSession = sarosSession;
        this.editorManager = editorManager;
        this.colorIDSetStorage = colorIDSetStorage;

        assert SarosSession.MAX_USERCOLORS >= 0;

        availableColors = new int[SarosSession.MAX_USERCOLORS];
    }

    @Override
    public void exec(IActivity activity) {
        activity.dispatch(receiver);
    }

    @Override
    public synchronized void start() {

        /*
         * If the host does not know its color ID, just assign him one
         */
        if (sarosSession.isHost()) {
            int colorID = sarosSession.getLocalUser().getColorID();

            favoriteUserColors.put(sarosSession.getLocalUser(), sarosSession
                .getLocalUser().getFavoriteColorID());

            if (!isValidColorID(colorID)) {
                colorID = getNextAvailableColorID();
                sarosSession.getLocalUser().setColorID(colorID);
            } else
                removeColorIdFromPool(colorID);
        } else {
            /*
             * Just remove the (maybe not valid) color ids, the host will
             * correct it later
             */
            for (User user : sarosSession.getUsers()) {
                favoriteUserColors.put(user, user.getFavoriteColorID());
                removeColorIdFromPool(user.getColorID());
            }

        }

        sarosSession.addActivityProvider(this);
        sarosSession.addListener(sessionListener);
    }

    @Override
    public synchronized void stop() {
        sarosSession.removeActivityProvider(this);
        sarosSession.removeListener(sessionListener);
    }

    /**
     * Returns a snapshot of the currently available (not in use) color IDs.
     * 
     * @return
     */
    public synchronized Set<Integer> getAvailableColors() {

        Set<Integer> available = new HashSet<Integer>();
        for (int i = 0; i < availableColors.length; i++) {
            if (availableColors[i] == 0)
                available.add(i);
        }

        return available;
    }

    /**
     * Changes the color id for the current local user. The change is done
     * asynchronously and may not be available immediately.
     * 
     * @param colorID
     *            the new color ID for the current session
     */
    public void changeColorID(int colorID) {
        fireActivity(new ChangeColorActivity(sarosSession.getLocalUser(),
            sarosSession.getHost(), sarosSession.getLocalUser(), colorID));
    }

    private void handleChangeColorActivity(ChangeColorActivity activity) {

        boolean fireChanges = false;
        User source = activity.getSource();
        User affected = activity.getAffected();
        int colorID = activity.getColorID();

        synchronized (this) {

            /*
             * FIXME the leave message packet is send from a client to all other
             * session users. This does not work well as this component uses a
             * host - client architecture !!!
             */
            if (affected == null) {
                log.warn("received color id change for a user that is no longer part of the session");
                return;
            }

            log.debug("received color id change fo user : " + affected + " ["
                + activity.getColorID() + "]");

            // host send us an update for a user
            if (source.isHost() && !sarosSession.isHost()) {

                addColorIdToPool(affected.getColorID());
                removeColorIdFromPool(colorID);

                // this fails if a new copy is returned !
                affected.setColorID(colorID);
            } else {

                assert sarosSession.isHost() : "only the session host can assign a color id";

                if (!isColorIDAvailable(colorID))
                    colorID = getNextAvailableColorID();
                else
                    removeColorIdFromPool(colorID);

                addColorIdToPool(affected.getColorID());

                affected.setColorID(colorID);
                fireChanges = true;
            }
        }

        if (fireChanges)
            broadcastColorIDChange(affected, affected.getColorID());

        // FIXME this can fail on client side, see fixme above
        /*
         * FIXME rework the code in SarosSession to ensure that getParticipants
         * always return a non dirty state during activity execution
         */

        updateColorSet(sarosSession.getUsers());

        editorManager.colorChanged();
        editorManager.refreshAnnotations();
    }

    /*
     * original algorithm by: fzieris and pschlott, modified and integrated by
     * srossbach
     * 
     * it ensures one invariant: favorite colors are optimally distributed
     * 
     * it assumes following invariants: if a colorIdSet exists, it contains no
     * color collisions (except when a colorIdSet is created for the first time,
     * then all colors are UserColorId.UNKNOWN)
     */
    private void reassignSessionColorIDs(List<User> currentUsers, User user,
        boolean joined) {
        assert sarosSession.isHost() : "only the session host can assign a color id";

        log.debug("reassigning color IDs for the current session users");

        synchronized (this) {

            /*
             * release all colors, see join / left, the host must handle the
             * join / left of a user here !
             */
            currentUsers.remove(user);

            // we need to release all current colors
            for (User currentUser : currentUsers)
                addColorIdToPool(currentUser.getColorID());

            /*
             * if a user just joined, he must be in the current users list. if
             * he left, his color became available in the pool
             */
            if (joined)
                currentUsers.add(user);
            else
                addColorIdToPool(user.getColorID());

            Map<User, Integer> assignedColors = assumeNoFavoriteColorCollisions(currentUsers);

            assert assignedColors.size() == currentUsers.size();

            ColorIDSet colorIDSet = colorIDSetStorage
                .getColorIDSet(asJIDCollection(currentUsers));

            resolveColorConflicts: do {

                // no conflict = OK
                if (isOptimalColorAssignment(assignedColors)) {
                    log.debug("color conflict resolve result = NO CONFLICT");
                    break resolveColorConflicts;
                }

                Map<User, Integer> lastKnownFavoriteColors = getLastKnownFavoriteColors(
                    assignedColors, colorIDSet);

                /*
                 * we already solved the problem in a former session = use the
                 * resolved colors again ... see above assignedColors is
                 * initialized with the current favorite colors
                 */
                if (lastKnownFavoriteColors.equals(assignedColors)) {

                    applyStoredColors(assignedColors, colorIDSet);

                    /*
                     * if there are users with favorite color
                     * UserColorID.UNKNOWN, then the optimal assignment cannot
                     * be known. So if there was already a valid assignment for
                     * these users we choose that, so that the users experience
                     * the reuse of colors.
                     */
                    if (favoriteUserColors.containsValue(UserColorID.UNKNOWN)
                        && isValidColorAssignment(assignedColors)) {
                        log.debug("color conflict resolve result = FAVORITE COLORS UNKNOWN, USING PREVIOUS COLOR ASSIGNMENT");
                        break resolveColorConflicts;
                    }

                    /*
                     * if color assignment is optimal, assignment is resolved.
                     */
                    if (isOptimalColorAssignment(assignedColors)) {
                        log.debug("color conflict resolve result = ALREADY SOLVED");
                        break resolveColorConflicts;
                    } else {
                        // the colorIdSet was not optimal, reassign colors
                        assignedColors = assumeNoFavoriteColorCollisions(currentUsers);
                    }
                }

                // resolve the problem
                autoAssignColors(assignedColors);

                /*
                 * if favorite colors weren't initialized, there cannot be an
                 * optimal assignment
                 */
                if (!favoriteUserColors.containsValue(UserColorID.UNKNOWN))
                    assert isOptimalColorAssignment(assignedColors);

                /* release all colors again as they will be removed again */
                for (int colorID : assignedColors.values())
                    addColorIdToPool(colorID);

                log.debug("color conflict resolve result = RESOLVED");

            } while (false); // this loop acts as GOTO replacement

            log.debug("new color assignment: " + assignedColors);

            updateColorAndUserPools(assignedColors);

            updateColorSet(currentUsers);
        }

        for (User currentUser : currentUsers)
            broadcastColorIDChange(currentUser, currentUser.getColorID());

        editorManager.colorChanged();
        editorManager.refreshAnnotations();

    }

    private void updateColorAndUserPools(Map<User, Integer> assignedColors) {
        for (Map.Entry<User, Integer> entry : assignedColors.entrySet()) {
            // make sure a used color is no longer available
            Integer colorId = entry.getValue();
            removeColorIdFromPool(colorId);

            // make sure user uses the colorId we calculated
            User user = entry.getKey();
            user.setColorID(colorId);
        }
    }

    /**
     * Proofs the if the invariant for optimal color assignment holds true. Here
     * we take this invariant to be:
     * <ul>
     * <li>Each favorite color is used in the final assignment</li>
     * <li>The assignment is valid (i.e each color is unique, and none of the
     * colors is UserColorId.UNKNOWN</li>
     * </ul>
     * 
     * @param assignedColors
     * @return
     */
    private boolean isOptimalColorAssignment(Map<User, Integer> assignedColors) {
        return assignedColors.values().containsAll(
            new HashSet<Integer>(favoriteUserColors.values()))
            && isValidColorAssignment(assignedColors);
    }

    private boolean isValidColorAssignment(Map<User, Integer> assignedColors) {
        return isUnique(assignedColors.values())
            && !assignedColors.containsValue(UserColorID.UNKNOWN);
    }

    private void applyStoredColors(Map<User, Integer> assignedColors,
        ColorIDSet colorIDSet) {

        for (Map.Entry<User, Integer> e : assignedColors.entrySet()) {
            e.setValue(colorIDSet.getColor(e.getKey().getJID()));
        }

    }

    /**
     * assigns the next available color for the users that couldn't get
     * <ul>
     * <li>their favorite color</li>
     * <li>a color from a previous session</li>
     * </ul>
     * 
     * @param assignedColors
     */
    private void autoAssignColors(Map<User, Integer> assignedColors) {
        List<User> usersToAutoAssignColors = new ArrayList<User>();

        for (Map.Entry<User, Integer> entry : assignedColors.entrySet()) {

            if (!isColorIDAvailable(entry.getValue())) {
                usersToAutoAssignColors.add(entry.getKey());
                continue;
            }
            removeColorIdFromPool(entry.getValue());
        }

        for (User currentUser : usersToAutoAssignColors) {
            int colorID = getNextAvailableColorID();
            assignedColors.put(currentUser, colorID);
        }
    }

    private Map<User, Integer> getLastKnownFavoriteColors(
        Map<User, Integer> assignedColors, ColorIDSet colorIDSet) {
        Map<User, Integer> lastKnownFavoriteColors = new LinkedHashMap<User, Integer>();

        for (User currentUser : assignedColors.keySet()) {
            lastKnownFavoriteColors.put(currentUser,
                colorIDSet.getFavoriteColor(currentUser.getJID()));
        }
        return lastKnownFavoriteColors;
    }

    /**
     * creates a color assignment, which assumes the favorite colors of the
     * users in the session don't collide
     * 
     * @param currentUsers
     * @return
     */
    private synchronized Map<User, Integer> assumeNoFavoriteColorCollisions(
        List<User> currentUsers) {

        // assume everyone gets his favorite color
        Map<User, Integer> assignedColors = new LinkedHashMap<User, Integer>();

        /*
         * iterate over favoriteUserColors to ensure no favorite color
         * "stealing" if a new user joins a.k.a assign colors in join order
         */
        for (Map.Entry<User, Integer> entry : favoriteUserColors.entrySet()) {
            if (currentUsers.contains(entry.getKey())) {

                /*
                 * make sure to use the right user object reference (although
                 * they should be the same)
                 */
                assignedColors.put(
                    currentUsers.get(currentUsers.indexOf(entry.getKey())),
                    entry.getValue());
            }
        }

        return assignedColors;
    }

    private void broadcastColorIDChange(User affected, int colorID) {

        assert sarosSession.isHost() : "only the session host can broadcast color id changes";

        List<User> currentRemoteSessionUsers = sarosSession.getRemoteUsers();

        for (User user : currentRemoteSessionUsers)
            fireActivity(new ChangeColorActivity(sarosSession.getLocalUser(),
                user, affected, colorID));
    }

    private boolean isValidColorID(int colorID) {
        if (colorID < 0)
            assert !UserColorID.isValid(colorID) : "negative color id must not be valid";

        return colorID >= 0 && colorID < SarosSession.MAX_USERCOLORS;
    }

    /**
     * Removes and returns the next available color ID from the color pool.
     */
    private synchronized int getNextAvailableColorID() {

        for (int i = 0; i < availableColors.length; i++) {
            if (availableColors[i] == 0) {
                removeColorIdFromPool(i);
                return i;
            }
        }

        return SarosSession.MAX_USERCOLORS;
    }

    private synchronized boolean isColorIDAvailable(int colorID) {
        if (!isValidColorID(colorID))
            return false;

        return availableColors[colorID] == 0;

    }

    /**
     * Adds the color ID to the color pool. Does nothing if the color ID is
     * invalid.
     */
    private synchronized void addColorIdToPool(int colorID) {
        if (!isValidColorID(colorID))
            return;

        availableColors[colorID]--;

        if (availableColors[colorID] < 0) {
            log.warn("color id: " + colorID
                + " was added although it was never removed");
            availableColors[colorID] = 0;
        }

        log.trace("color id: " + colorID + " is currently used "
            + availableColors[colorID] + " times");
    }

    /**
     * Removes the color ID from the color pool. Does nothing if the color ID is
     * invalid.
     */
    private synchronized void removeColorIdFromPool(int colorID) {
        if (!isValidColorID(colorID))
            return;

        availableColors[colorID]++;

        log.trace("color id: " + colorID + " is currently used "
            + availableColors[colorID] + " times");
    }

    private synchronized void updateColorSet(Collection<User> users) {

        ColorIDSet colorIDSet = colorIDSetStorage
            .getColorIDSet(asJIDCollection(users));

        log.debug("updating color id set: "
            + Arrays.toString(colorIDSet.getParticipants().toArray()));

        /*
         * reset colors to unknown otherwise we may get an illegal state
         * exception
         */
        for (User user : users)
            colorIDSetStorage.updateColor(colorIDSet, user.getJID(),
                UserColorID.UNKNOWN, UserColorID.UNKNOWN);
        for (User user : users) {
            if (!isValidColorID(user.getColorID()))
                continue;

            /*
             * the host still sends color updates so it is ok to abort here and
             * leaving the color set in a dirty state
             */
            if (!colorIDSet.isAvailable(user.getColorID())) {
                assert !sarosSession.isHost() : "invalid color state on host side";
                break;
            }

            log.trace("updating color id set: user '" + user + "' id '"
                + user.getColorID() + "' fav id '"
                + favoriteUserColors.get(user) + "'");

            colorIDSetStorage.updateColor(colorIDSet, user.getJID(),
                user.getColorID(), favoriteUserColors.get(user));
        }
    }

    private Collection<JID> asJIDCollection(Collection<User> users) {
        List<JID> result = new ArrayList<JID>(users.size());

        for (User user : users)
            result.add(user.getJID());

        return result;
    }

    private boolean isUnique(Collection<Integer> collection) {
        Set<Integer> set = new HashSet<Integer>(collection.size());

        for (Integer i : collection) {
            if (set.contains(i))
                return false;

            set.add(i);
        }

        return true;
    }
}
