package org.csstudio.team.repomonitor.job;

import java.io.IOException;

import org.csstudio.team.repomonitor.RepoMonitorPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.ReflogEntry;
import org.eclipse.jgit.storage.file.ReflogReader;

public class RepoFetchJob extends Job {

	private static final String REMOTE_BRANCH_PREFIX = "refs/remotes/";
	
	private static final int MAX_LOG_SEARCH_DEPTH = 1000;
	
	
	private Repository repository;
	
	private int remoteBranchAhead;
	
	private int trackingBranchAhead;
	
	public RepoFetchJob(Repository repository) {
		super("Fetch Repository");
		this.repository = repository;
	}

	public int getRemoteBranchAhead() {
		return remoteBranchAhead;
	}

	public int getTrackingBranchAhead() {
		return trackingBranchAhead;
	}
	
	protected String getRemoteBranch() {
		// Consider checking git config for remote
		// associated with the current branch.
		String current;
		try {
			current = "/" + repository.getBranch();
		} catch(IOException e) {
			return null;
		}
		for(String branch : repository.getAllRefs().keySet()) {
			if(branch.startsWith(REMOTE_BRANCH_PREFIX) && branch.endsWith(current)) {
				return branch;
			}
		}
		return null;
	}
	
	protected String getTrackingBranch() {
		try {
			return repository.getFullBranch();
		} catch (IOException e) {
			return null;
		}
	}
	
	@Override
	protected IStatus run(IProgressMonitor monitor) {
		
		monitor.beginTask("Fetch Repository", 2);
		
		FetchCommand cmd = Git.wrap(repository).fetch();
		// Enabling a 'dry-run' is broken;
		// an actual fetch is executed instead.
		// cmd.setDryRun(true);
		try {
			cmd.call();	
		} catch(GitAPIException e) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error while executing 'fetch' commannd", e);
		} catch(JGitInternalException e) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Exception while executing 'fetch' commannd", e);
		}
		
		monitor.worked(1);
		
		String remoteBranch = getRemoteBranch();
		if(remoteBranch == null) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error while getting remote branch name");
		}
		
		String trackingBranch = getTrackingBranch();
		if(trackingBranch == null) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error while getting tracking branch name");
		}
		
		ReflogReader remoteBranchRefLog;
		try {
			remoteBranchRefLog = repository.getReflogReader(remoteBranch);
		} catch (IOException e) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error while reading remote branch reflog.", e);
		}
		
		if(remoteBranchRefLog == null) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "No reflog found for branch: " + remoteBranch);
		}
		
		ReflogReader trackingBranchRefLog;
		try {
			trackingBranchRefLog = repository.getReflogReader(trackingBranch);
		} catch(IOException e) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error while reading tracking branch reflog.", e);
		}
		
		if(trackingBranchRefLog == null) {
			return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "No reflog found for branch: " + trackingBranch);
		}
		
		ReflogEntry remoteBranchEntry;
		ReflogEntry trackingBranchEntry;
		for(int tidx = 0; tidx < MAX_LOG_SEARCH_DEPTH; tidx++) {
			try {
				trackingBranchEntry  = trackingBranchRefLog.getReverseEntry(tidx);
			} catch (IOException e) {
				return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error while reading tracking branch reflog entry: " + tidx, e);
			}
			
			if(trackingBranchEntry == null) {
				break;
			}
			
			for(int ridx = 0; ridx < MAX_LOG_SEARCH_DEPTH; ridx++) {
				try {
					remoteBranchEntry = remoteBranchRefLog.getReverseEntry(ridx);
				} catch(IOException e) {
					return new Status(Status.ERROR, RepoMonitorPlugin.PLUGIN_ID, "Error while reading remote branch reflog entry: " + ridx, e); 
				}
				
				if(remoteBranchEntry == null) {
					break;
				}
				
				if(remoteBranchEntry.getNewId().equals(trackingBranchEntry.getNewId())) {
					remoteBranchAhead = ridx;
					trackingBranchAhead = tidx;
					return Status.OK_STATUS;
				}
			}
		}
		
		monitor.worked(1);
		
		return new Status(Status.WARNING, RepoMonitorPlugin.PLUGIN_ID, "Remote branch and tracking branch have no common ancestor.");
	}
}
