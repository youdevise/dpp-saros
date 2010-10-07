package de.fu_berlin.inf.dpp.stf.test.invitation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.rmi.AccessException;
import java.rmi.RemoteException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import de.fu_berlin.inf.dpp.stf.sarosswtbot.BotConfiguration;
import de.fu_berlin.inf.dpp.stf.sarosswtbot.Musician;
import de.fu_berlin.inf.dpp.stf.test.InitMusician;

public class TestParallelInvitationWithTerminationByHost {

    private static final String CLS = BotConfiguration.CLASSNAME;
    private static final String PKG = BotConfiguration.PACKAGENAME;
    private static final String PROJECT = BotConfiguration.PROJECTNAME;

    protected static Musician alice;
    protected static Musician bob;
    protected static Musician carl;

    protected static ArrayList<String> invitees = new ArrayList<String>();

    @BeforeClass
    public static void initMusicians() throws AccessException, RemoteException {
        alice = InitMusician.newAlice();
        bob = InitMusician.newBob();
        carl = InitMusician.newCarl();
        alice.bot.newJavaProjectWithClass(PROJECT, PKG, CLS);
        invitees.add(bob.getPlainJid());
        invitees.add(carl.getPlainJid());
    }

    /**
     * make sure, all opened xmppConnects, popup windows and editor should be
     * closed. make sure, all existed projects should be deleted. if you need
     * some more after class condition for your tests, please add it.
     * 
     * @throws RemoteException
     */
    @AfterClass
    public static void resetSaros() throws RemoteException {
        bob.bot.resetSaros();
        carl.bot.resetSaros();
        alice.bot.resetSaros();
    }

    /**
     * make sure,all opened popup windows and editor should be closed. if you
     * need some more after condition for your tests, please add it.
     * 
     * @throws RemoteException
     */
    @After
    public void cleanUp() throws RemoteException {
        bob.bot.resetWorkbench();
        carl.bot.resetWorkbench();
        alice.bot.resetWorkbench();
    }

    /**
     * 1. Alice invites Bob and Carl simultaneously. 2. Carl accepts the
     * invitation but does not choose a target project. 3. Alice opens the
     * Progress View and cancels Bob's invitation before Bob accepts. 4. Alice
     * opens the Progress View and cancels Carl's invitation before Carl
     * accepts. Result: 1. Bob is notified of Alice's canceling the invitation.
     * 2. Carl is notified of Alice's canceling the invitation. 3. Carl and Bob
     * are not in session
     * 
     * @throws RemoteException
     */
    @Test
    public void testInvitationWithTerminationByHost() throws RemoteException {
        alice.bot.shareProject(PROJECT, invitees);
        carl.bot.confirmSessionInvitationWindowStep1(alice.getPlainJid());

        alice.bot.cancelInvitation();
        bob.bot.waitUntilShellActive("Invitation Cancelled");

        assertTrue(bob.bot.isShellActive("Invitation Cancelled"));

        bob.bot.ackErrorDialog();
        alice.bot.removeProgress();

        alice.bot.cancelInvitation();
        carl.bot.waitUntilShellActive("Invitation Cancelled");

        assertTrue(carl.bot.isShellActive("Invitation Cancelled"));

        carl.bot.ackErrorDialog();
        alice.bot.removeProgress();

        assertFalse(bob.bot.isInSession());
        assertFalse(carl.bot.isInSession());

    }
}