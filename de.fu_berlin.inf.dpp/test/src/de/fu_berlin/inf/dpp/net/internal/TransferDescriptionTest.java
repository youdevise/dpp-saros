package de.fu_berlin.inf.dpp.net.internal;

import junit.framework.TestCase;
import de.fu_berlin.inf.dpp.net.JID;

public class TransferDescriptionTest extends TestCase {

    private TransferDescription td;
    private JID recipient;
    private JID sender;
    private String invitationID;
    private String sessionID;

    @Override
    protected void setUp() throws Exception {
        recipient = new JID("receiver@foo");
        sender = new JID("sender@bar");
        invitationID = "invitation";
        sessionID = "session";
        td = TransferDescription.createFileListTransferDescription(recipient,
            sender, sessionID, invitationID);
    }

    public void testByteArray() throws ClassNotFoundException {
        byte[] data = td.toByteArray();
        TransferDescription td2 = TransferDescription.fromByteArray(data);
        assertEquals(td.sessionID, td2.sessionID);
        assertEquals(td.invitationID, td2.invitationID);
        assertEquals(td.sender, td2.sender);
        assertEquals(td.recipient, td2.recipient);
    }
}