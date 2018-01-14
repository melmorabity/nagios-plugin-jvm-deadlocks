/*
 *  Copyright 2018 Mohamed El Morabity
 *
 *  This program is free software: you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package com.github.melmorabity.nagios_plugin_jvm_deadlocks;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Connects to a JMX agent attached to a local JVM process (or starts one if not
 * available) to detect deadlocks.
 */
public class NagiosPluginJVMDeadlocks {
	/**
	 * Java property to get the JMX connector address.
	 */
	private static final String LOCAL_CONNECTOR_ADDRESS_PROPERTY = "com.sun.management.jmxremote.localConnectorAddress";

	/**
	 * JMX connector address for the monitored Java process.
	 */
	private String connectorAddress;

	/**
	 * Attaches to a JVM instance defined by its PID and retrieve its JMX agent
	 * address, by starting one if none is running.
	 * 
	 * @param pid
	 *            the PID for the Java process to monitor.
	 * @throws NagiosPluginJVMDeadlocksException
	 *             if:
	 *             <ul>
	 *             <li>the PID doesn't correspond to a JVM instance</li>
	 *             <li>the current user doesn't own the JVM process</li>
	 *             <li>Running a new JMX agent on the JVM process fails</li>
	 *             </ul>
	 */
	public NagiosPluginJVMDeadlocks(int pid) throws NagiosPluginJVMDeadlocksException {
		VirtualMachine jvm = null;
		try {
			/* Check all running JVMs and retrieve the one matching the PID */
			for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
				if (vmd.id().equals(Integer.toString(pid))) {
					jvm = VirtualMachine.attach(vmd);
					break;
				}
			}
			if (jvm == null) {
				throw new NagiosPluginJVMDeadlocksException("Unable to check process with PID " + pid);
			}

			String connectorAddress = jvm.getAgentProperties().getProperty(LOCAL_CONNECTOR_ADDRESS_PROPERTY);

			/* If no JMX agent is available for the JVM instance, start a JMX agent */
			if (connectorAddress == null) {
				/* Use the management agent bundled in JDK */
				String agent = jvm.getSystemProperties().getProperty("java.home") + File.separator + "lib"
						+ File.separator + "management-agent.jar";
				jvm.loadAgent(agent);
				connectorAddress = jvm.getAgentProperties().getProperty(LOCAL_CONNECTOR_ADDRESS_PROPERTY);
			}
			jvm.detach();

			this.connectorAddress = connectorAddress;
		} catch (AttachNotSupportedException | IOException | AgentLoadException | AgentInitializationException e) {
			throw new NagiosPluginJVMDeadlocksException(e);
		}
	}

	/**
	 * Gets all threads in deadlock state for the monitored process.
	 * 
	 * @return an array containing thread numbers if the process is in deadlock
	 *         state, null otherwise.
	 * @throws NagiosPluginJVMDeadlocksException
	 *             if connecting to the JMX agent attached to the JVM process fails.
	 */
	public long[] findDeadlockedThreads() throws NagiosPluginJVMDeadlocksException {
		try {
			JMXServiceURL jmxUrl = new JMXServiceURL(connectorAddress);
			JMXConnector connector = JMXConnectorFactory.connect(jmxUrl);
			MBeanServerConnection mbean = connector.getMBeanServerConnection();
			ThreadMXBean thread_mxbean = ManagementFactory.getPlatformMXBean(mbean, ThreadMXBean.class);
			return thread_mxbean.findDeadlockedThreads();
		} catch (IOException e) {
			throw new NagiosPluginJVMDeadlocksException(e);
		}
	}
}
