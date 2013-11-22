package org.csstudio.team.repomonitor.indicator;



import org.csstudio.team.repomonitor.IRepoMonitorListener;
import org.csstudio.team.repomonitor.RepoMonitorPlugin;
import org.csstudio.team.repomonitor.RepoMonitorPlugin.RepoStatus;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.resource.ImageRegistry;


public class RepoToolbarIndicator extends ContributionItem {
	
	public static final String CONTRIBUTION_ID = "org.csstudio.team.repomonitor.toolbar.indicator";
	
	// Could not find a static variable that contained this ID. //
	public static final String GIT_REPOSITORIES_VIEW_ID = "org.eclipse.egit.ui.RepositoriesView";
	
	public static final String DEFAULT_IMAGE = RepoMonitorPlugin.REPO_ERROR_ICON;
	
	private IndicateStatusJob indicateStatusJob = new IndicateStatusJob();
	
	private ToolItem indicator = null;
	
	private Menu indicatorMenu = null;
	
	
	public RepoToolbarIndicator() {
		super(CONTRIBUTION_ID);
	}

	public void setImage(Image image) {
		if(indicator != null && !indicator.isDisposed()) {
			indicator.setImage(image);
		}
	}

	public void setToolTipText(String toolTipText) {
		if(indicator != null && !indicator.isDisposed()) {
			indicator.setToolTipText(toolTipText);
		}
	}
	
	@Override
	public void fill(ToolBar parent, int index) {
		indicator = new ToolItem(parent, SWT.DROP_DOWN);
		indicator.setImage(RepoMonitorPlugin.getDefault().getImageRegistry().get(DEFAULT_IMAGE));
		indicator.addSelectionListener(new IndicatorSelectionListener());
		
		indicatorMenu = new Menu(parent.getShell(), SWT.NONE);
		
		MenuItem m;

		m = new MenuItem(indicatorMenu, SWT.NONE);
		m.setText("Refresh");
		m.addSelectionListener(new RefreshRepoStatusSelectionListener());
		
		m = new MenuItem(indicatorMenu, SWT.SEPARATOR);
		
		m = new MenuItem(indicatorMenu, SWT.NONE);
		m.setText("Projects");
		m.addSelectionListener(new ProjectExplorerViewSelectionListener());
				
		m = new MenuItem(indicatorMenu, SWT.NONE);
		m.setText("Repositories");
		m.addSelectionListener(new GitRepositoryViewSelectionListener());

		RepoMonitorPlugin.getDefault().addMonitorListener(new RepoMonitorListener());
	}		
	
	protected class IndicatorSelectionListener extends SelectionAdapter {
		
		@Override
		public void widgetSelected(SelectionEvent event) {
			if(event.detail == SWT.ARROW) {
				Rectangle bounds = indicator.getBounds();
				Point point = indicator.getParent().toDisplay(bounds.x, bounds.y);
				indicatorMenu.setLocation(point.x, point.y + bounds.height);
				indicatorMenu.setVisible(true);				
			} else {
				RepoMonitorPlugin.getDefault().updateMonitor();
			}
		}
	}
	
	protected class RefreshRepoStatusSelectionListener extends SelectionAdapter {
		
		@Override
		public void widgetSelected(SelectionEvent event) {
			RepoMonitorPlugin.getDefault().updateMonitor();
		}
	}
	
	protected class ProjectExplorerViewSelectionListener extends SelectionAdapter {

		@Override
		public void widgetSelected(SelectionEvent event) {
			try {
				 PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(IPageLayout.ID_PROJECT_EXPLORER);
			} catch(PartInitException e) {
				RepoMonitorPlugin.getDefault().getLog().log(new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error showing Project Explorer view", e));
			}
		}
	}
	
	protected class GitRepositoryViewSelectionListener extends SelectionAdapter {

		@Override
		public void widgetSelected(SelectionEvent event) {
			try {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(GIT_REPOSITORIES_VIEW_ID);
			} catch(PartInitException e) {
				RepoMonitorPlugin.getDefault().getLog().log(new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error showing Git Repositories view", e));
			}
		}
	}
	
	protected class RepoMonitorListener implements IRepoMonitorListener {

		@Override
		public void status(RepoStatus status, int commitsAhead, int commitsBehind) {
			if(indicateStatusJob != null) {
				indicateStatusJob.setStatus(status);
				indicateStatusJob.setCommitsAhead(commitsAhead);
				indicateStatusJob.setCommitsBehind(commitsBehind);
				indicateStatusJob.schedule();
			}
		}
	}
	
	protected class IndicateStatusJob extends UIJob {

		private int commitsAhead;
		
		private int commitsBehind;
		
		private RepoStatus status;
		
		public IndicateStatusJob() {
			super("Update Repo Monitor Toolbar Indicator Job");
		}

		public void setCommitsAhead(int commitsAhead) {
			this.commitsAhead = commitsAhead;
		}

		public void setCommitsBehind(int commitsBehind) {
			this.commitsBehind = commitsBehind;
		}

		public void setStatus(RepoStatus status) {
			this.status = status;
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {

			ImageRegistry imageRegistry = RepoMonitorPlugin.getDefault().getImageRegistry();
			
			switch(status) {
			case SYNC:
				setImage(imageRegistry.get(RepoMonitorPlugin.REPO_SYNC_ICON));
				setToolTipText("Repository: Synchonized");
				break;
			
			case AHEAD:
				setImage(imageRegistry.get(RepoMonitorPlugin.REPO_AHEAD_ICON));
				setToolTipText("Repository: " + commitsAhead + " Ahead");
				break;
				
			case BEHIND:
				setImage(imageRegistry.get(RepoMonitorPlugin.REPO_BEHIND_ICON));
				setToolTipText("Repository: " + commitsBehind + " Behind");
				break;
				
			case DIVERGE:
				setImage(imageRegistry.get(RepoMonitorPlugin.REPO_DIVERGE_ICON));
				setToolTipText("Repository: " + commitsAhead + " Ahead, " + commitsBehind + " Behind");
				break;
				
			case ERROR:			
				setImage(imageRegistry.get(RepoMonitorPlugin.REPO_ERROR_ICON));
				setToolTipText("Repository Error: See Log for Information");
				break;
				
			case BUSY:
				setImage(imageRegistry.get(RepoMonitorPlugin.REPO_BUSY_ICON));
				setToolTipText("Repository: Refreshing");
			}
			
			return Status.OK_STATUS;
		}	
	}
}
