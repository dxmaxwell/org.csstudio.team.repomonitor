package org.csstudio.team.repomonitor;

import java.util.Map;

import org.csstudio.startup.module.ServicesStartupExtPoint;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.widgets.Display;

public class RepoMonitorStartup implements ServicesStartupExtPoint {

	
	@Override
	public Object startServices(Display display, IApplicationContext context, Map<String, Object> parameters) throws Exception {
		RepoMonitorPlugin.getDefault().startMonitor();
		return null;
	}
}
