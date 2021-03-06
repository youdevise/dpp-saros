/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universität Berlin - Fachbereich Mathematik und Informatik - 2010
 * (c) Stephan Lau - 2010
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
package de.fu_berlin.inf.dpp.ui.actions;

import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.picocontainer.Disposable;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.SarosPluginContext;
import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.exceptions.SarosCancellationException;
import de.fu_berlin.inf.dpp.observables.VideoSessionObservable;
import de.fu_berlin.inf.dpp.ui.ImageManager;
import de.fu_berlin.inf.dpp.ui.Messages;
import de.fu_berlin.inf.dpp.ui.util.DialogUtils;
import de.fu_berlin.inf.dpp.ui.util.SWTUtils;
import de.fu_berlin.inf.dpp.ui.util.selection.SelectionUtils;
import de.fu_berlin.inf.dpp.ui.util.selection.retriever.SelectionRetrieverFactory;
import de.fu_berlin.inf.dpp.util.ValueChangeListener;
import de.fu_berlin.inf.dpp.videosharing.VideoSharing;
import de.fu_berlin.inf.dpp.videosharing.VideoSharing.VideoSharingSession;

/**
 * @author s-lau
 * @author bkahlert
 */

@Component(module = "action")
public class VideoSharingAction extends Action implements Disposable {

    private static final Logger log = Logger
        .getLogger(VideoSharingAction.class);

    protected static final String ACTION_ID = VideoSharingAction.class
        .getName();
    public static final String TOOLTIP_START_SESSION = Messages.VideoSharingAction_start_session_tooltip;
    public static final String TOOLTIP_STOP_SESSION = Messages.VideoSharingAction_stop_session_tooltip;

    protected ValueChangeListener<VideoSharingSession> changeListener = new ValueChangeListener<VideoSharingSession>() {
        @Override
        public void setValue(VideoSharingSession newValue) {
            updateEnablement();
        }
    };

    protected ISelectionListener selectionListener = new ISelectionListener() {
        @Override
        public void selectionChanged(IWorkbenchPart part, ISelection selection) {
            updateEnablement();
        }
    };

    @Inject
    protected VideoSharing videoSharing;
    @Inject
    protected VideoSessionObservable sessionObservable;

    public VideoSharingAction() {
        super(Messages.VideoSharingAction_title);
        SarosPluginContext.initComponent(this);
        setId(ACTION_ID);

        sessionObservable.add(changeListener);
        SelectionUtils.getSelectionService().addSelectionListener(
            selectionListener);
        updateEnablement();
    }

    protected void updateEnablement() {
        try {

            List<User> participants = SelectionRetrieverFactory
                .getSelectionRetriever(User.class).getSelection();
            setEnabled(participants.size() == 1
                && !participants.get(0).isLocal());
        } catch (NullPointerException e) {
            this.setEnabled(false);
        } catch (Exception e) {
            if (!PlatformUI.getWorkbench().isClosing())
                log.error("Unexcepted error while updating enablement", e); //$NON-NLS-1$
        }

        if (sessionObservable.getValue() != null) {
            setImageDescriptor(ImageManager
                .getImageDescriptor("icons/elcl16/stopvideo.png")); //$NON-NLS-1$
            setToolTipText(TOOLTIP_STOP_SESSION);
            return;
        }
        setImageDescriptor(ImageManager
            .getImageDescriptor("icons/elcl16/startvideo.png")); //$NON-NLS-1$
        setToolTipText(TOOLTIP_START_SESSION);

        setEnabled(videoSharing.ready());
    }

    @Override
    public void run() {
        SWTUtils.runSafeSWTAsync(log, new Runnable() {
            @Override
            public void run() {
                List<User> participants = SelectionRetrieverFactory
                    .getSelectionRetriever(User.class).getSelection();
                if (participants.size() == 1) {
                    VideoSharingSession videoSharingSession = sessionObservable
                        .getValue();
                    if (videoSharing.ready()) {
                        try {
                            setEnabled(false);
                            videoSharing.startSharing(participants.get(0));
                        } catch (final SarosCancellationException e) {
                            DialogUtils
                                .popUpFailureMessage(
                                    Messages.VideoSharingAction_error_could_not_establish_screensharing_text,
                                    e.getMessage(), false);
                            log.error("Could not establish screensharing: ", e); //$NON-NLS-1$
                        }
                    } else {
                        switch (videoSharingSession.getMode()) {
                        case LOCAL:
                        case HOST: // $FALL-THROUGH$
                            break;
                        case CLIENT:
                            videoSharingSession.requestStop();
                            break;
                        }
                        videoSharingSession.dispose();
                    }
                    updateEnablement();
                } else {
                    log.warn("More than one participant selected."); //$NON-NLS-1$
                }
            }
        });
    }

    @Override
    public void dispose() {
        SelectionUtils.getSelectionService().removeSelectionListener(
            selectionListener);
        sessionObservable.remove(changeListener);
    }
}
