package de.fu_berlin.inf.dpp.concurrent.watchdog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.activities.SPath;
import de.fu_berlin.inf.dpp.activities.business.ChecksumActivity;
import de.fu_berlin.inf.dpp.activities.business.IActivity;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.concurrent.management.DocumentChecksum;
import de.fu_berlin.inf.dpp.editor.EditorManager;
import de.fu_berlin.inf.dpp.net.ConnectionState;
import de.fu_berlin.inf.dpp.net.SarosNet;
import de.fu_berlin.inf.dpp.project.AbstractActivityProvider;
import de.fu_berlin.inf.dpp.project.AbstractSarosSessionListener;
import de.fu_berlin.inf.dpp.project.ISarosSession;
import de.fu_berlin.inf.dpp.project.ISarosSessionManager;
import de.fu_berlin.inf.dpp.synchronize.Blockable;
import de.fu_berlin.inf.dpp.ui.util.SWTUtils;

/**
 * This class is an eclipse job run on the host side ONLY.
 * 
 * The job computes checksums for all files currently managed by Jupiter (the
 * ConcurrentDocumentManager) and sends them to all guests.
 * 
 * These will call their ConcurrentDocumentManager.check(...) method, to verify
 * that their version is correct.
 * 
 * Once started with schedule() the job is scheduled to rerun every INTERVAL ms.
 * 
 * @author chjacob
 * 
 *         TODO Make ConsistencyWatchDog configurable => Timeout, Whether run or
 *         not, etc.
 * 
 */
@Component(module = "consistency")
public class ConsistencyWatchdogServer extends Job {

    private static Logger log = Logger
        .getLogger(ConsistencyWatchdogServer.class);

    protected static final long INTERVAL = 10000;

    // this map holds for all open editors of all participants the checksums
    protected final HashMap<SPath, DocumentChecksum> docsChecksums = new HashMap<SPath, DocumentChecksum>();

    @Inject
    protected SarosNet sarosNet;

    @Inject
    protected EditorManager editorManager;

    protected ISarosSessionManager sessionManager;

    protected ISarosSession sarosSession;

    private boolean locked = false;

    protected final AbstractActivityProvider activityProvider = new AbstractActivityProvider() {
        @Override
        public void exec(IActivity activity) {
            /* Needed to be able to dispatch activities */
        }
    };

    private final Blockable executionState = new Blockable() {

        @Override
        public void block() {

            SWTUtils.runSafeSWTSync(log, new Runnable() {
                @Override
                public void run() {
                    /*
                     * set in SWT thread to ensure that all outstanding
                     * activities are fired when we return
                     */
                    locked = true;
                }
            });
        }

        @Override
        public void unblock() {
            locked = false;
        }

    };

    public ConsistencyWatchdogServer(ISarosSessionManager sessionManager) {
        super("ConsistencyWatchdog");

        this.sessionManager = sessionManager;

        this.sessionManager
            .addSarosSessionListener(new AbstractSarosSessionListener() {
                @Override
                public void sessionStarted(ISarosSession newSarosSession) {

                    newSarosSession.addActivityProvider(activityProvider);
                    if (newSarosSession.isHost()) {
                        sarosSession = newSarosSession;
                        sarosSession.getStopManager().addBlockable(
                            executionState);

                        log.debug("Starting consistency watchdog");
                        if (!isSystem())
                            setSystem(true);
                        setPriority(Job.SHORT);
                        schedule(10000);
                    }
                }

                @Override
                public void sessionEnded(ISarosSession oldSarosSession) {

                    oldSarosSession.removeActivityProvider(activityProvider);

                    if (sarosSession != null) {
                        sarosSession.getStopManager().removeBlockable(
                            executionState);
                        // Cancel Job
                        cancel();
                        sarosSession = null;

                        // Unregister from all documents
                        for (DocumentChecksum document : docsChecksums.values()) {
                            document.dispose();
                        }
                        docsChecksums.clear();
                    }
                }
            });
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {

        try {
            return runUnsafe(monitor);
        } catch (RuntimeException e) {
            log.error("Internal Error: ", e);
            schedule(10000);
            return Status.OK_STATUS;
        }
    }

    protected IStatus runUnsafe(IProgressMonitor monitor) {
        if (sessionManager.getSarosSession() == null)
            return Status.OK_STATUS;

        assert sarosSession.isHost() : "This job is intended to be run on host side!";

        // If connection is closed, checking does not make sense...
        if (sarosNet.getConnectionState() != ConnectionState.CONNECTED) {
            // Reschedule the next run in 30 seconds
            schedule(30000);
            return Status.OK_STATUS;
        }

        Set<SPath> missingDocuments = new HashSet<SPath>(docsChecksums.keySet());

        Set<SPath> localEditors = editorManager.getLocallyOpenEditors();
        Set<SPath> remoteEditors = editorManager.getRemoteOpenEditors();
        Set<SPath> allEditors = new HashSet<SPath>();
        allEditors.addAll(localEditors);
        allEditors.addAll(remoteEditors);

        // Update Checksums for all open documents
        for (SPath docPath : allEditors) {

            if (monitor.isCanceled())
                return Status.CANCEL_STATUS;

            updateChecksum(missingDocuments, localEditors, remoteEditors,
                docPath);
        }

        // Unregister all documents that are no longer there
        for (SPath missing : missingDocuments) {
            docsChecksums.remove(missing).dispose();
        }

        if (monitor.isCanceled())
            return Status.CANCEL_STATUS;

        // Reschedule the next run in INTERVAL ms
        schedule(INTERVAL);
        return Status.OK_STATUS;
    }

    protected void updateChecksum(final Set<SPath> missingDocuments,
        final Set<SPath> localEditors, final Set<SPath> remoteEditors,
        final SPath docPath) {

        SWTUtils.runSafeSWTSync(log, new Runnable() {
            @Override
            public void run() {

                if (locked)
                    return;

                IFile file = docPath.getFile();

                IDocument doc;
                IDocumentProvider provider = null;
                FileEditorInput input = null;
                if (!file.exists()) {
                    doc = null;
                } else {
                    input = new FileEditorInput(file);
                    provider = EditorManager.getDocumentProvider(input);
                    try {
                        provider.connect(input);
                        doc = provider.getDocument(input);
                    } catch (CoreException e) {
                        log.warn("Could not check checksum of file "
                            + docPath.toString());
                        provider = null;
                        doc = null;
                    }
                }

                try {
                    // Null means that the document does not exist locally
                    if (doc == null) {
                        if (localEditors.contains(docPath)) {
                            log.error("EditorManager is in an inconsistent state. "
                                + "It is reporting a locally open editor but no"
                                + " document could be found on disk: "
                                + docPath);
                        }
                        if (!remoteEditors.contains(docPath)) {
                            /*
                             * Since remote users do not report this document as
                             * open, they are right (and our EditorPool might be
                             * confused)
                             */
                            return;
                        }
                    }

                    // Update listener management
                    missingDocuments.remove(docPath);

                    DocumentChecksum checksum = docsChecksums.get(docPath);
                    if (checksum == null) {
                        checksum = new DocumentChecksum(docPath);
                        docsChecksums.put(docPath, checksum);
                    }

                    /*
                     * Potentially bind to null doc, which will set the Checksum
                     * to represent a missing file (existsFile() == false)
                     */
                    checksum.bind(doc);
                    checksum.update();

                    // Send a checksum to everybody
                    ChecksumActivity checksumActivity = new ChecksumActivity(
                        sarosSession.getLocalUser(), checksum.getPath(),
                        checksum.getHash(), checksum.getLength(), null);

                    activityProvider.fireActivity(checksumActivity);

                } finally {
                    if (provider != null) {
                        provider.disconnect(input);
                    }
                }

            }
        });
    }
}
