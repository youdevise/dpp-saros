package de.fu_berlin.inf.dpp.concurrent.management;

import java.util.HashMap;

import org.eclipse.core.runtime.IPath;

import de.fu_berlin.inf.dpp.activities.TextEditActivity;
import de.fu_berlin.inf.dpp.concurrent.jupiter.JupiterActivity;
import de.fu_berlin.inf.dpp.concurrent.jupiter.Operation;
import de.fu_berlin.inf.dpp.concurrent.jupiter.TransformationException;
import de.fu_berlin.inf.dpp.concurrent.jupiter.internal.Jupiter;
import de.fu_berlin.inf.dpp.project.ISharedProject;

/**
 * A JupiterClient manages Jupiter client docs for a single user with several
 * paths
 */
public class JupiterClient {

    protected ISharedProject sharedProject;

    public JupiterClient(ISharedProject project) {
        this.sharedProject = project;
    }

    /**
     * Jupiter instances for each local editor.
     * 
     * @host and @client
     */
    protected final HashMap<IPath, Jupiter> clientDocs = new HashMap<IPath, Jupiter>();

    /**
     * @host and @client
     */
    protected synchronized Jupiter get(IPath path) {

        Jupiter clientDoc = this.clientDocs.get(path);
        if (clientDoc == null) {
            clientDoc = new Jupiter(true);
            this.clientDocs.put(path, clientDoc);
        }
        return clientDoc;
    }

    public synchronized Operation receive(JupiterActivity jupiterActivity)
        throws TransformationException {
        return get(jupiterActivity.getEditorPath()).receiveJupiterActivity(
            jupiterActivity);
    }

    public synchronized void reset(IPath path) {
        this.clientDocs.remove(path);
    }

    public synchronized void reset() {
        this.clientDocs.clear();
    }

    public synchronized JupiterActivity generate(TextEditActivity textEdit) {

        return get(textEdit.getEditor()).generateJupiterActivity(
            textEdit.toOperation(), sharedProject.getLocalUser().getJID(),
            textEdit.getEditor());
    }
}