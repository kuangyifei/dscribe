package com.ideanest.dscribe;

import java.io.*;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.exist.fluent.*;
import org.jargp.*;
import org.quartz.SchedulerException;

import com.ideanest.dscribe.job.Job;

/**
 * The main entry point for Reef.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class Main implements Runnable {
	
	private static final Logger LOG = Logger.getLogger(Main.class);
	static final String MAIN_INSTANCE_KEY = "dScribe main instance";
	private static final String CONFIG_UPDATE_JOB_NAME = "update-config";

	private String workingDirName = "";
	private boolean help;
	private ArgumentProcessor argumentProcessor;
	
	private File workingDir, tempDir;
	private Schedule schedule;
	private Configuration bootstrap;
	
	private Main() {/*singleton*/}
	
	public void run() {
		checkCommandLine();
		initDirectories();
		initLogging();
		// warning:  logging not configured before this point! use at own risk
		initDatabase();
		listenToConsole();
		initScheduler();
		bootstrapConfig();
	}

	private void checkCommandLine() {
		if (argumentProcessor.getArgs().hasNext()) {
			System.out.println("Invalid extra arguments on command line");
			help = true;
		}
		if (help) {
			System.out.println("Valid options are:");
			argumentProcessor.listParameters(80, System.out);
			System.exit(0);
		}
	}
	
	private void initDirectories() {
		workingDir = new File(workingDirName).getAbsoluteFile();
		tempDir = new File(workingDir, "temp");
		tempDir.mkdir();
	}
	
	private void initLogging() {
		DOMConfigurator.configureAndWatch(new File(workingDir, "sysconfig/log4j.xml").getAbsolutePath());
	}

	private void initDatabase() {
		// create a data directory for the database, otherwise if not present the db won't start up
		// the directory name is hardcoded and should match the one in the db config file referenced below
		try {
			new File(workingDir, "data").mkdir();
			Database.startup(new File(workingDir, "sysconfig/dbconf.xml"));
		} catch (DatabaseException e) {
			LOG.fatal("Failed to initialize database", e);
			System.exit(1);
		}
	}

	private synchronized void initScheduler() {
		try {
			schedule = new Schedule(this);
		} catch (SchedulerException e) {
			LOG.fatal("Failed to initialize scheduler", e);
			System.exit(2);
		}
	}
	
	private void listenToConsole() {
		new Thread(new Runnable() {
			public void run() {
				Reader console = new InputStreamReader(System.in);
				try {
					while(true) {
						switch (console.read()) {
							case '\n':
							case '\r':
								break;
							case 'h':
								System.out.println("h - help");
								System.out.println("q - quit");
								break;
							case 'q':
							case -1:
								throw new IOException();
							default:
								System.out.println("unkown command");
						}
					}
				} catch (IOException e) {
				}
				synchronized (this) { if (schedule != null) schedule.shutdown(); }
				Database.shutdown();
				System.exit(0);
			}
		}).start();
	}

	private void bootstrapConfig() {
		bootstrap = createBootstrapConfig();
		try {
			Job configUpdateJob = new Job(CONFIG_UPDATE_JOB_NAME, bootstrap);
			configUpdateJob.createCycle(null, true).execute();
			QueryService qs = configUpdateJob.latestSpace().query().namespace("", Namespace.NOTES);
			String status = qs.optional("//notes/@status").value();
			if (status == null) throw new IllegalStateException("running bootstrap config update did not result in a status code");
			if (status.equals("completed")) return;
			// TODO: if status is postponed, cancel scheduled cycle, wait until given time and try again
			throw new IllegalStateException("bootstrap config status '" + status + "'");
		} catch (Exception e) {
			LOG.fatal("Failed to bootstrap config", e);
			System.exit(3);
		}
	}
	
	private Configuration createBootstrapConfig() {
		LOG.info("Creating bootstrap configuration");
		Folder bootstrapFolder = Database.login("admin", null).createFolder("/dscribe/system/bootstrap");
		bootstrapFolder.clear();
		// load basic config files into the bootstrap folder to create a minimal config
		File[] configFiles = new File(workingDir, "config").listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".xml");
			}
		});
		for (File file : configFiles) {
			LOG.debug("Loading bootstrap config file '" + file + "'");
			bootstrapFolder.documents().load(Name.keepCreate(), Source.xml(file));
		}
		return new Configuration(this, bootstrapFolder);
	}
	
	void resetSchedule() {
		initScheduler();
		bootstrapConfig();
	}
	
	Configuration currentConfig() {
		Folder configFolder = new Job(CONFIG_UPDATE_JOB_NAME, bootstrap).stableSpace();
		assert !configFolder.isEmpty();
		return new Configuration(this, configFolder);
	}
	
	File workdir() {
		return workingDir;
	}
	
	File tempdir() {
		return tempDir;
	}
	
	Schedule schedule() {
		return schedule;
	}


	// TODO: rebuild arg parser to take definition from annotations in JDK 1.5
	private static final ParameterDef[] PARAMS = {
			new StringDef('c', "workingDirName", "working directory, defaults to current directory"),
			new BoolDef('h', "help", "print usage information")
	};
	
	public static void main(String[] args) {
		ArgumentProcessor.runWithArgs(args, PARAMS, "argumentProcessor", new Main());
	}

}
