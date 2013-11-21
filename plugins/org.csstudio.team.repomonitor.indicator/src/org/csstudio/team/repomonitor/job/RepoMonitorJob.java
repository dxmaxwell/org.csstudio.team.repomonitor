package org.csstudio.team.repomonitor.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.csstudio.team.repomonitor.RepoMonitorPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Repository;

public class RepoMonitorJob extends Job {

	private boolean shouldSchedule = true;
	
	public RepoMonitorJob() {
		super("Repository Monitor Job");
	}
	
	
	@Override
	protected IStatus run(IProgressMonitor monitor) {
		Map<Repository,ArrayList<IProject>> repositories = new HashMap<>();	
		for(IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			RepositoryMapping mapping = RepositoryMapping.getMapping(project);
			if(mapping != null) {
				Repository repository = mapping.getRepository();
				if(!repositories.containsKey(repository)) {
					repositories.put(repository, new ArrayList<IProject>());
				} 
				repositories.get(repository).add(project);
			}
		}
		
		List<RepoFetchJob> repoFetchJobs = new ArrayList<>();
		for(Map.Entry<Repository,ArrayList<IProject>> entry : repositories.entrySet()) {
			RepoFetchJob job = new RepoFetchJob(entry.getKey());
			job.setRule(new MultiRule(entry.getValue().toArray(new IProject[entry.getValue().size()])));
			job.setProgressGroup(monitor, 1);
			repoFetchJobs.add(job);
			job.schedule();
		}
		
		monitor.beginTask("Repository Monitor", repositories.size());
		
		if(monitor.isCanceled()) {
			schedule(RepoMonitorPlugin.getDefault().getMonitorDelay());
			return Status.CANCEL_STATUS;
		}
		
		int remoteBranchAhead = 0;
		int trackingBranchAhead = 0;
		boolean error = false;
		
		for(RepoFetchJob job : repoFetchJobs) {
			try {
				job.join();
			} catch (InterruptedException e) {
				error = true;
			}
			
			if(job.getResult().isOK()) {
				remoteBranchAhead += job.getRemoteBranchAhead();
				trackingBranchAhead += job.getTrackingBranchAhead();
			} else {
				error = true;
			}
			
			monitor.worked(1);
		}
		
		if(error) {
			RepoMonitorPlugin.getDefault().setError();
		} else {
			RepoMonitorPlugin.getDefault().setStatus(trackingBranchAhead, remoteBranchAhead);
		}
		
		
		schedule(RepoMonitorPlugin.getDefault().getMonitorDelay());
		
		return Status.OK_STATUS;
	}

	@Override
	public boolean shouldSchedule() {
		return shouldSchedule;
	}

	public void setShouldSchedule(boolean shouldSchedule) {
		this.shouldSchedule = shouldSchedule;
	}
}
