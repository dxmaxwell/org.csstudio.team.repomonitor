package org.csstudio.team.repomonitor;

import org.csstudio.team.repomonitor.RepoMonitorPlugin.RepoStatus;

public interface IRepoMonitorListener {

	public void status(RepoStatus status, int commitsAhead, int commitsBehind);
}
