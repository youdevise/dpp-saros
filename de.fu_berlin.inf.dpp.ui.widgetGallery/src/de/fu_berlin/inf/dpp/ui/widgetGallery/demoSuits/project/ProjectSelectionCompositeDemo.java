package de.fu_berlin.inf.dpp.ui.widgetGallery.demoSuits.project;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;

import de.fu_berlin.inf.dpp.ui.util.selection.SelectionUtils;
import de.fu_berlin.inf.dpp.ui.util.selection.retriever.SelectionRetrieverFactory;
import de.fu_berlin.inf.dpp.ui.widgetGallery.annotations.Demo;
import de.fu_berlin.inf.dpp.ui.widgetGallery.demoSuits.AbstractDemo;
import de.fu_berlin.inf.dpp.ui.widgets.viewer.project.ProjectSelectionComposite;

@Demo("This demo shows a Composite that reflects the currently slected projects in the workbench.")
public class ProjectSelectionCompositeDemo extends AbstractDemo {
    protected ProjectSelectionComposite projectSelectionComposite;

    protected ISelectionListener selectionListener = new ISelectionListener() {
        @Override
        public void selectionChanged(IWorkbenchPart part, ISelection selection) {
            projectSelectionComposite
                .setSelectedProjects(SelectionRetrieverFactory
                    .getSelectionRetriever(IProject.class)
                    .getOverallSelection());
        }
    };

    @Override
    public void createDemo(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        projectSelectionComposite = new ProjectSelectionComposite(parent,
            SWT.BORDER, true);
        projectSelectionComposite.setLayoutData(new GridData(SWT.FILL,
            SWT.FILL, true, true));
        SelectionUtils.getSelectionService().addSelectionListener(
            selectionListener);
    }

    @Override
    public void dispose() {
        SelectionUtils.getSelectionService().removeSelectionListener(
            selectionListener);
        super.dispose();
    }
}
