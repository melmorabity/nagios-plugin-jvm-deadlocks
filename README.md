# nagios-plugin-systemd-service

Nagios plugin to check Java deadlocks.

## Authors

Mohamed El Morabity <melmorabity -(at)- fedoraproject.org>

## Usage

    check_jvm_deadlocks -f <file> | -p <integer> | -s <service>

* `-f`, `--pid-file <file>`: path to a PID file
* `-p`, `--pid <integer>`: PID number of the Java process to monitor
* `-s`, `--systemd-unit <service>`: systemd unit service (Linux only)

## Notes

This plugin is written in Java and relies on methods provided by the Attach API. As a result, a JDK is required to run this plugin.
