package org.csstudio.team.repomonitor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import org.csstudio.team.repomonitor.job.RepoMonitorJob;


/**
 * The activator class controls the plug-in life cycle
 */
public class RepoMonitorPlugin extends AbstractUIPlugin {

	// The shared instance
	private static RepoMonitorPlugin plugin;
	
	// The plug-in ID
	public static final String PLUGIN_ID = "org.csstudio.team.repomonitor.indicator"; //$NON-NLS-1$

	public static final String REPO_ERROR_ICON = "icons/repo-error.gif";
	
	public static final String REPO_SYNC_ICON = "icons/repo-sync.gif";
	
	public static final String REPO_AHEAD_ICON = "icons/repo-ahead.gif";
	
	public static final String REPO_BEHIND_ICON = "icons/repo-behind.gif";
	
	public static final String REPO_DIVERGE_ICON = "icons/repo-diverge.gif";
	
	public static final String REPO_BUSY_ICON = "icons/repo-busy.gif";
	
	public static final long DEFAULT_MONITOR_DELAY = 360000; // 1 hour
	
	public static final long START_MONITOR_DELAY = 30000;    // 30 seconds
	
	public static enum RepoStatus { ERROR, BUSY, SYNC, AHEAD, BEHIND, DIVERGE }
	
	
	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static RepoMonitorPlugin getDefault() {
		return plugin;
	}

	
	private int commitsAhead = 0;
	
	private int commitsBehind = 0;

	private RepoStatus status = RepoStatus.ERROR;
	
	private long monitorDelay = DEFAULT_MONITOR_DELAY;
	
	private RepoMonitorJob monitorJob = new RepoMonitorJob();  
	
	private Set<IRepoMonitorListener> listeners = Collections.synchronizedSet(new HashSet<IRepoMonitorListener>()); 
	
	/**
	 * Activate the plugin.
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;		
		getImageRegistry().put(REPO_ERROR_ICON, ImageDescriptor.createFromURL(
				FileLocator.find(context.getBundle(), new Path(REPO_ERROR_ICON), null)));
		getImageRegistry().put(REPO_SYNC_ICON, ImageDescriptor.createFromURL(
				FileLocator.find(context.getBundle(), new Path(REPO_SYNC_ICON), null)));
		getImageRegistry().put(REPO_AHEAD_ICON, ImageDescriptor.createFromURL(
				FileLocator.find(context.getBundle(), new Path(REPO_AHEAD_ICON), null)));
		getImageRegistry().put(REPO_BEHIND_ICON, ImageDescriptor.createFromURL(
				FileLocator.find(context.getBundle(), new Path(REPO_BEHIND_ICON), null)));
		getImageRegistry().put(REPO_DIVERGE_ICON, ImageDescriptor.createFromURL(
				FileLocator.find(context.getBundle(), new Path(REPO_DIVERGE_ICON), null)));	
		getImageRegistry().put(REPO_BUSY_ICON, ImageDescriptor.createFromURL(
				FileLocator.find(context.getBundle(), new Path(REPO_BUSY_ICON), null)));	
	}

	/**
	 * Deactivate the plugin.
	 */
	public void stop(BundleContext context) throws Exception {
		getImageRegistry().dispose();
		plugin = null;
		super.stop(context);
	}
	
	/**
	 * Starts the repository monitor job.
	 * 
	 * A short delay is used on startup to allow the workbench to fully initialize before
	 * any Git commands are executed.  If Git commands are executed too soon after
	 * workbench startup, then the command may fail because the stored password
	 * can not be accessed yet. 
	 */
	public void startMonitor() {
		if(monitorJob.getState() == Job.NONE) {
			monitorJob.setShouldSchedule(true);
			monitorJob.schedule(START_MONITOR_DELAY);
		}
	}
	
	/**
	 * Stop the repository monitor job.
	 */
	public void stopMonitor() {
		if(monitorJob.getState() != Job.NONE) {
			monitorJob.setShouldSchedule(false);
			monitorJob.cancel();
		}
	}
	
	/**
	 * Run the repository monitor job to update status immediately.
	 */
	public void updateMonitor() {
		if(monitorJob.getState() == Job.SLEEPING) {
			monitorJob.wakeUp();
		} else if(monitorJob.getState() == Job.NONE) { 
			monitorJob.schedule();
		}
	}
	
	public int getCommitsAhead() {
		return commitsAhead;
	}

	public int getCommitsBehind() {
		return commitsBehind;
	}
	
	public long getMonitorDelay() {
		return monitorDelay;
	}

	public void setMonitorDelay(long monitorDelay) {
		this.monitorDelay = monitorDelay;
	}

	public RepoStatus getStatus() {
		return status;
	}
	
	/**
	 * Set the status of the repository based on the number of commits ahead and behind.
	 * 
	 * 
	 * @param commitsAhead Number of commits local branch is ahead
	 * @param commitsBehind Number of commits local branch is behind
	 */
	public void setStatus(int commitsAhead, int commitsBehind) {
		this.commitsAhead = commitsAhead;
		this.commitsBehind = commitsBehind;
		if(commitsAhead > 0 && commitsBehind > 0) {
			status = RepoStatus.DIVERGE;
		} else if(commitsAhead > 0) {
			status = RepoStatus.AHEAD;
		} else if(commitsBehind > 0) {
			status = RepoStatus.BEHIND;
		} else {
			status = RepoStatus.SYNC;
		}
		fireMonitorListeners();
	}
	
	/**
	 * Set the status of the repository to error.
	 * 
	 * Information about the error can be obtained from the error log.
	 * 
	 */
	public void setError() {
		status = RepoStatus.ERROR;
		fireMonitorListeners();
	}
	
	/**
	 * Set the status of the repository to busy.
	 * 
	 * This can be used to indicate to the user that monitoring is active.
	 */
	public void setBusy() {
		status = RepoStatus.BUSY;
		fireMonitorListeners();
	}
	
	
	public void addMonitorListener(IRepoMonitorListener listener) {
		if(listeners.add(listener)) {
			fireMonitorListener(listener);
		}
	}
	
	public void removeMonitorListener(IRepoMonitorListener listener) {
		listeners.remove(listener);
	}
	
	protected void fireMonitorListeners() {
		for(IRepoMonitorListener listener : listeners) {
			fireMonitorListener(listener);
		}
	}
	
	protected void fireMonitorListener(IRepoMonitorListener listener) {
		try {
			listener.status(status, commitsAhead, commitsBehind);
		} catch(Exception e) {
			getLog().log(new Status(Status.WARNING, PLUGIN_ID, "Exception while executing repository monitor listener: " + listener.getClass().getSimpleName(), e));
		}
	}
}
