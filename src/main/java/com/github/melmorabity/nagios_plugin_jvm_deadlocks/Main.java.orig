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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Option.Builder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.SystemUtils;

/**
 * Main execution class.
 */
public class Main {
	/**
	 * Program name.
	 */
	private static final String PROGRAM_NAME = "check_jvm_deadlocks";

	/**
	 * Nagios status codes.
	 */
	private static enum NAGIOS_STATES {
		OK, // return code: 0
		WARNING, // return code: 1
		CRITICAL, // return code: 2
		UNKNOWN; // return code: 3
	}

	/**
	 * Available CLI options for the program.
	 */
	private static final Options CLI_OPTIONS = defineCliOptions();

	/**
	 * Short option identifier to specify a JVM process to the program by its PID
	 * number.
	 */
	private static final String PID_NUMBER_SHORT_CLI_OPTION = "p";

	/**
	 * Short option identifier to specify a JVM process to the program from a PID
	 * file.
	 */
	private static final String PID_FILE_SHORT_CLI_OPTION = "f";

	/**
	 * Short option identifier to specify a JVM process to the program from a PID
	 * file.
	 */
	private static final String SYSTEMD_UNIT_SHORT_CLI_OPTION = "s";

	/**
	 * Reads a PID from a PID file. A PID file is supposed to only contain an
	 * integer, corresponding to a (running) process ID.
	 * 
	 * @param file
	 *            the PID file to parse.
	 * @return the PID stored in the file.
	 * @throws NagiosPluginJVMDeadlocksException
	 *             if PID file doesn't exist, is not readable or doesn't content a
	 *             unique PID (as an integer number).
	 */
	private static int getPIDFromFile(String file) throws NagiosPluginJVMDeadlocksException {
		final Pattern emptyLineRegex = Pattern.compile("^\\s*$");
		final Pattern pidLineRegex = Pattern.compile("^\\s*(\\d+)\\s*$");

		int pid = -1;

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				/* Skip empty lines */
				if (emptyLineRegex.matcher(line).matches()) {
					continue;
				}

				/* Only one integer number is expected */
				Matcher m = pidLineRegex.matcher(line);
				if (pid < 0 && m.matches()) {
					pid = Integer.parseInt(m.group(1));
				} else {
					throw new NagiosPluginJVMDeadlocksException("Invalid content for PID file " + file);
				}
			}

			return pid;
		} catch (NumberFormatException | IOException e) {
			throw new NagiosPluginJVMDeadlocksException(e);
		}
	}

	/**
	 * Gets the PID of a systemd unit service.
	 * 
	 * @param unit
	 *            the name for the service.
	 * @return the PID stored in the file.
	 * @throws NagiosPluginJVMDeadlocksException
	 *             <ul>
	 *             <li>if retrieving the service status fails</li>
	 *             <li>if the service doesn't exist or is not running</li>
	 *             </ul>
	 */
	private static int getPIDFromSystemdUnit(String unit) throws NagiosPluginJVMDeadlocksException {
		/* Build systemctl command to retrieve the PID for the specified service */
		org.apache.commons.exec.CommandLine commandLine = new org.apache.commons.exec.CommandLine("systemctl");
		commandLine.addArgument("show");
		commandLine.addArgument("-p");
		commandLine.addArgument("MainPID");
		commandLine.addArgument(unit);

		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);

		ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
		PumpStreamHandler streamHandler = new PumpStreamHandler(stdoutStream);

		executor.setStreamHandler(streamHandler);
		try {
			executor.execute(commandLine);
		} catch (IOException e) {
			throw new NagiosPluginJVMDeadlocksException(e);
		}

		/* Extract PID from stdout */
		final Pattern mainPIDRegex = Pattern.compile("^MainPID=(\\d+)\\s*$");

		String output = stdoutStream.toString();
		Matcher m = mainPIDRegex.matcher(output);
		if (!m.matches()) {
			throw new NagiosPluginJVMDeadlocksException("Unable to retrieve status for systemd unit " + unit);
		}
		int pid = Integer.parseInt(m.group(1));
		if (pid == 0) {
			throw new NagiosPluginJVMDeadlocksException(
					"Systemd unit " + unit + " service doesn't exist or is not running");
		}
		return pid;
	}

	/**
	 * Define CLI options for the program.
	 * 
	 * @return an <code>org.apache.commons.cli.Option</code> object defining
	 *         available options for the program.
	 */
	private static Options defineCliOptions() {
		Options options = new Options();

		OptionGroup pidOptions = new OptionGroup();
		pidOptions.setRequired(true);

		Builder pidNumberOption = Option.builder(PID_NUMBER_SHORT_CLI_OPTION);
		pidNumberOption.longOpt("pid");
		pidNumberOption.desc("PID number of the Java process to monitor");
		pidNumberOption.argName("INTEGER");
		pidNumberOption.hasArg();
		pidNumberOption.type(Number.class);
		pidOptions.addOption(pidNumberOption.build());

		Builder pidFileOption = Option.builder(PID_FILE_SHORT_CLI_OPTION);
		pidFileOption.longOpt("pid-file");
		pidFileOption.desc("Path to a PID file");
		pidFileOption.argName("FILE");
		pidFileOption.hasArg();
		pidOptions.addOption(pidFileOption.build());

		/* Linux-specific options */
		if (SystemUtils.IS_OS_LINUX) {
			Builder systemdUnitOption = Option.builder(SYSTEMD_UNIT_SHORT_CLI_OPTION);
			systemdUnitOption.longOpt("systemd-unit");
			systemdUnitOption.desc("systemd unit service");
			systemdUnitOption.argName("SERVICE");
			systemdUnitOption.hasArg();
			pidOptions.addOption(systemdUnitOption.build());
		}

		options.addOptionGroup(pidOptions);

		return options;
	}

	/**
	 * Displays program help on standard error.
	 */
	private final static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(new PrintWriter(System.err, true), HelpFormatter.DEFAULT_WIDTH, PROGRAM_NAME, null,
				CLI_OPTIONS, HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, null, true);
	}

	/**
	 * Computes the PID to monitor from specified command-line arguments.
	 * 
	 * @param args
	 *            Command-line arguments.
	 * @return the PID
	 * @throws NagiosPluginJVMDeadlocksException
	 *             if PID computing fails.
	 * @throws ParseException
	 *             if command-line arguments are invalid.
	 */
	private static int getPIDFromCliOptions(String... args) throws NagiosPluginJVMDeadlocksException, ParseException {
		CommandLine command = new DefaultParser().parse(CLI_OPTIONS, args);
		int pid = 0;

		if (command.hasOption(PID_NUMBER_SHORT_CLI_OPTION)) {
			pid = ((Number) command.getParsedOptionValue(PID_NUMBER_SHORT_CLI_OPTION)).intValue();
		} else if (command.hasOption(PID_FILE_SHORT_CLI_OPTION)) {
			String file = command.getOptionValue(PID_FILE_SHORT_CLI_OPTION);
			pid = getPIDFromFile(file);
		} else if (command.hasOption(SYSTEMD_UNIT_SHORT_CLI_OPTION)) {
			String unit = command.getOptionValue(SYSTEMD_UNIT_SHORT_CLI_OPTION);
			pid = getPIDFromSystemdUnit(unit);
		}

		return pid;
	}

	/**
	 * Main method.
	 * 
	 * @param args
	 *            Command line arguments.
	 */
	public static void main(String[] args) {
		try {
			/* Parse command-line arguments */
			int pid = getPIDFromCliOptions(args);

			ToolsLibrary.loadToolsLibrary();

			/* Check specified JVM process */
			NagiosPluginJVMDeadlocks nagios = new NagiosPluginJVMDeadlocks(pid);
			long[] deadlockedThreads = nagios.findDeadlockedThreads();

			if (deadlockedThreads != null && deadlockedThreads.length > 0) {
				System.out.printf("%s: Deadlock detected on process %s (%s deadlocked threads)%n",
						NAGIOS_STATES.CRITICAL.toString(), pid, deadlockedThreads.length);
				System.exit(NAGIOS_STATES.CRITICAL.ordinal());
			}

			System.out.printf("%s: no deadlock detected for process %s%n", NAGIOS_STATES.OK.toString(), pid);
			System.exit(NAGIOS_STATES.OK.ordinal());
		} catch (ParseException e) {
			printHelp();
			System.exit(NAGIOS_STATES.UNKNOWN.ordinal());
		} catch (NagiosPluginJVMDeadlocksException e) {
			String message = e.getMessage();
			Throwable cause = e.getCause();
			if (cause != null) {
				message = cause.getMessage();
			}
			System.err.printf("%s: %s%n", NAGIOS_STATES.UNKNOWN.toString(), message);
			System.exit(NAGIOS_STATES.UNKNOWN.ordinal());
		}
	}
}
