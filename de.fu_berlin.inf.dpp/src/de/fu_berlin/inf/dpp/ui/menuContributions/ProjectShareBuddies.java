package de.fu_berlin.inf.dpp.ui.menuContributions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.picocontainer.annotations.Inject;

import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.SarosPluginContext;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.SarosNet;
import de.fu_berlin.inf.dpp.net.discoverymanager.DiscoveryManager;
import de.fu_berlin.inf.dpp.net.discoverymanager.DiscoveryManager.CacheMissException;
import de.fu_berlin.inf.dpp.net.util.RosterUtils;
import de.fu_berlin.inf.dpp.project.ISarosSessionManager;
import de.fu_berlin.inf.dpp.ui.Messages;
import de.fu_berlin.inf.dpp.ui.model.roster.RosterEntryElement;
import de.fu_berlin.inf.dpp.ui.util.CollaborationUtils;
import de.fu_berlin.inf.dpp.ui.util.selection.retriever.SelectionRetrieverFactory;

/**
 * This class fills a {@link Menu} with {@link MenuItem}s.<br/>
 * Each {@link MenuItem} represents a Saros enabled contact.<br/>
 * A click leads to a shared project invitation.
 */
public class ProjectShareBuddies extends ContributionItem {

    @Inject
    protected SarosNet sarosNet;

    @Inject
    protected ISarosSessionManager sarosSessionManager;

    @Inject
    protected DiscoveryManager discoveryManager;

    public ProjectShareBuddies() {
        this(null);
    }

    public ProjectShareBuddies(String id) {
        super(id);
        SarosPluginContext.initComponent(this);
    }

    @Override
    public void fill(Menu menu, int index) {
        if (!sarosNet.isConnected())
            return;

        final List<IResource> selectedResources = SelectionRetrieverFactory
            .getSelectionRetriever(IResource.class).getSelection();

        int numSarosSupportedBuddies = 0;

        for (final RosterEntry rosterEntry : this.getSortedRosterEntries()) {
            boolean sarosSupport;
            try {
                sarosSupport = discoveryManager.isSupportedNonBlock(new JID(
                    rosterEntry.getUser()), Saros.NAMESPACE);
            } catch (CacheMissException e) {
                sarosSupport = true;
            }

            if (sarosSupport) {
                createBuddyMenuItem(menu, numSarosSupportedBuddies++,
                    rosterEntry, selectedResources);
            }
        }

        if (numSarosSupportedBuddies == 0) {
            createNoBuddiesMenuItem(menu, numSarosSupportedBuddies);
        }
    }

    /**
     * Returns a sorted array of {@link Roster}'s buddies.
     * 
     * @return
     */
    protected RosterEntry[] getSortedRosterEntries() {
        RosterEntry[] rosterEntries = sarosNet.getRoster().getEntries()
            .toArray(new RosterEntry[0]);
        Arrays.sort(rosterEntries, new Comparator<RosterEntry>() {
            @Override
            public int compare(RosterEntry o1, RosterEntry o2) {
                String name1 = RosterUtils.getDisplayableName(o1);
                String name2 = RosterUtils.getDisplayableName(o2);
                return name1.compareToIgnoreCase(name2);
            }
        });
        return rosterEntries;
    }

    /**
     * Creates a menu entry which shares projects with the given
     * {@link RosterEntry}.
     * 
     * @param parentMenu
     * @param index
     * @param rosterEntry
     * @param resources
     * @return
     */
    protected MenuItem createBuddyMenuItem(Menu parentMenu, int index,
        final RosterEntry rosterEntry, final List<IResource> resources) {

        /*
         * The model knows how to display roster entries best.
         */
        RosterEntryElement rosterEntryElement = new RosterEntryElement(
            sarosNet.getRoster(), new JID(rosterEntry.getUser()));

        MenuItem menuItem = new MenuItem(parentMenu, SWT.NONE, index);
        menuItem.setText(rosterEntryElement.getStyledText().toString());
        menuItem.setImage(rosterEntryElement.getImage());

        menuItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                List<JID> buddies = new ArrayList<JID>();
                buddies.add(new JID(rosterEntry));
                CollaborationUtils.shareResourcesWith(sarosSessionManager,
                    resources, buddies);
            }
        });

        return menuItem;
    }

    /**
     * Creates a menu entry which indicates that no Saros enabled buddies are
     * online.
     * 
     * @param parentMenu
     * @param index
     * @return
     */
    protected MenuItem createNoBuddiesMenuItem(Menu parentMenu, int index) {
        MenuItem menuItem = new MenuItem(parentMenu, SWT.NONE, index);
        menuItem
            .setText(Messages.ProjectShareBuddies_no_buddies_online_with_saros);
        menuItem.setEnabled(false);
        return menuItem;
    }

}
