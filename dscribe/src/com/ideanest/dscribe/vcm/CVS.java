package com.ideanest.dscribe.vcm;

import java.io.*;
import java.math.BigDecimal;
import java.text.*;
import java.util.*;
import java.util.regex.Pattern;

import javax.xml.datatype.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.netbeans.lib.cvsclient.*;
import org.netbeans.lib.cvsclient.admin.*;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.history.HistoryCommand;
import org.netbeans.lib.cvsclient.command.log.*;
import org.netbeans.lib.cvsclient.connection.*;
import org.netbeans.lib.cvsclient.event.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;

/**
 * Manages a connection to a CVS repository.
 *
 * TODO: figure if/how to support module redirection and aliasing; problems with history, rlog, etc.
 * TODO: figure if/how to support branches
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class CVS extends TaskBase {

	private static final Logger LOG = Logger.getLogger(CVS.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"notes", Namespace.NOTES,
			"vcm", Namespace.VCM,
			"cvs", Namespace.VCM_CVS
	);
	private static final Duration ONE_MINUTE = DataUtils.datatypeFactory().newDuration("PT1M");
	
	private QuietPeriod quietPeriod;
	private Node workFiles;
	private CVSRoot root;
	private String module;
	private Date prevUpdate;
	private GlobalOptions globalOptions;
	private boolean blockBetweenUpdates, scanPrehistory;
	private File blockFile, targetDir;
	private final AdminHandler adminHandler = new StandardAdminHandler();
	
	private final DateFormat commandDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
	{commandDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+0"));}
	
	@Override
	protected void init(Node taskDef) throws Exception {
		initParams(taskDef);
		initDerivedParams();
		initQuietPeriod(taskDef);	
		initGlobalOptions();		
	}

	private void initParams(Node taskDef) {
		root = CVSRoot.parse(taskDef.query().single("@root").value());
		module = taskDef.query().single("@module").value();
		blockBetweenUpdates = taskDef.query().flag("@block-between-updates", false);
		scanPrehistory = taskDef.query().flag("@scan-prehistory", false);
		targetDir = cycle().resolveOptionalFile(taskDef.query().optional("@dst").value());
		targetDir.mkdirs();
	}
	
	private void initDerivedParams() {
		String repositoryID = root.getHostName() + "/" + root.getRepository() + "#" + module;
		prevUpdate = cycle().prevspace(NAMESPACE_MAPPINGS).query().optional("/cvs:record[@repid=$_1]/@update-at", repositoryID).instantValue();
		workFiles = cycle().workspace(NAMESPACE_MAPPINGS).documents()
		.build(Name.adjust("cvs-record"))
			.elem("cvs:record").attr("repid", repositoryID).end("cvs:record")
		.commit().root();
		blockFile = new File(cycle().tempdir(), "cvs-blocker_" + cycle().name() + "_" + module + "_delete-me");
	}
	
	private void initQuietPeriod(Node taskDef) throws ParseException {
		Date scanStart = prevUpdate;
		if (scanStart == null && scanPrehistory) scanStart = new Date(0);	// arbitrary date far in the past, before any conceivable events
		quietPeriod = new QuietPeriod(cycle(), taskDef, scanStart);
		quietPeriod.setPrecision(ONE_MINUTE);
		// if first run, assume there are changes before that we're not going to scan
		if (scanStart == null) quietPeriod.addChangePointBeforeScanStart();
		
		for (Item interestPoint : taskDef.query().all("interest-point/@at")) {
			try {
				quietPeriod.addInterestPoint(interestPoint.instantValue());
			} catch (DatabaseException e) {
				LOG.error("invalid CVS interest point " + interestPoint, e);
			}
		}
	}
	
	private void initGlobalOptions() {
		globalOptions = new GlobalOptions();
		globalOptions.setCVSRoot(root.toString());
		globalOptions.setCompressionLevel(3);
		globalOptions.setNoHistoryLogging(true);
		globalOptions.setModeratelyQuiet(true);
		globalOptions.setIgnoreCvsrc(true);
	}

	@Phase
	public void check() throws CommandAbortedException, CommandException, AuthenticationException, IOException {
		if (checkBlocker()) return;	
		doHistory();		
		quietPeriod.recordProposal(workFiles.append());
		if (LOG.isDebugEnabled()) LOG.debug("checkpoint proposal: \n" + workFiles.query().single("//vcm:checkpoint-proposal"));
	}

	private void doHistory() throws CommandException, CommandAbortedException, AuthenticationException, IOException {
		HistoryCommand cmd = new HistoryCommand();
		cmd.setForAllUsers(true);
		cmd.setReportEventType("TMAR");	// tags and commits
		cmd.setReportOnRepository(new String[]{module});
		if (prevUpdate != null || !scanPrehistory) {
			cmd.setSinceDate(commandDateFormat.format(quietPeriod.scanStart()));
		}
		executeCommand(cmd, targetDir, new HistoryListener());
	}

	private boolean checkBlocker() {
		if (blockBetweenUpdates) {
			if (blockFile.exists()) {
				LOG.debug("abandoning cvs check due to presence of blocking file");
				cycle().abandon();
				return true;
			}
			try {
				blockFile.createNewFile();
			} catch (IOException e) {
				LOG.warn("unable to create CVS blocking file", e);
			}
		}
		return false;
	}

	@Phase
	public void update() throws CommandAbortedException, CommandException, AuthenticationException, IOException {
		Date checkpoint = calculateAdjustedCheckpoint();
		doCheckout(checkpoint);
		boolean gotModifications = false;
		if (prevUpdate != null) try {
			doRlog(checkpoint);
			matchLocalFilenames();
			matchHistoryActions();
			translateCommits();
			inferCommits();
			if (LOG.isDebugEnabled()) LOG.debug("extracted modifications:\n" + workFiles.query().all("//vcm:modification").nodes());
			gotModifications = true;
		} catch (Exception e) {
			LOG.warn("unable to infer CVS modifications, performing a clean run", e);
		}
		if (!gotModifications) {
			cycle().workspace(NAMESPACE_MAPPINGS).query().single("/notes:notes").node()
				.update().attr("cleanrun", "true").commit();
		}
		workFiles.update().attr("update-at", checkpoint).commit();
	}
	
	private void doRlog(Date checkpoint) throws CommandException, CommandAbortedException, AuthenticationException, IOException {
		RlogCommand logCmd = new RlogCommand();
		// date filter seems to be exclusive on upper bound, but due to precision roundoff
		// done by the QuietPeriod to the checkpoint, it'll actually include everything it should
		String dateFilter = commandDateFormat.format(prevUpdate) + "<" + commandDateFormat.format(checkpoint);
		logCmd.setDateFilter(dateFilter);
		logCmd.setModule(module);
		logCmd.setDefaultBranch(true);
		logCmd.setNoTags(true);
		logCmd.setSuppressHeader(true);	// suppress headers with no revisions selected
		executeCommand(logCmd, targetDir, new LogListener());
		// TODO: is there any way to do a sanity check on the results, since we're about to rely on them for incremental builds?
	}

	private void doCheckout(Date checkpoint) throws CommandException, CommandAbortedException, AuthenticationException, IOException {
		// TODO: verify that our target directory isn't being used by some other CVS module
		CheckoutCommand cmd = new CheckoutCommand(true, module);
		cmd.setCheckoutByDate(commandDateFormat.format(checkpoint));
		cmd.setCheckoutDirectory(targetDir.getName());
		cmd.setPruneDirectories(true);
		executeCommand(cmd, targetDir.getParentFile(), new CheckoutListener());
		// note that we can't use which files were checked out for inference later since we might
		// be running after a previous aborted checkout and hence not every file would need to be re-written
		// TODO: if update fails, set a flag to force a wipe next time
	}

	private Date calculateAdjustedCheckpoint() {
		// TODO: adjust checkpoint to CVS server clock skew
		return cycle().workspace(NAMESPACE_MAPPINGS).query().single("/notes:notes/vcm:checkpoint/@at").instantValue();
	}
	
	private static int count(String s, char c) {
		int count = 0;
		for (int i=s.length()-1; i>=0; i--) if (s.charAt(i) == c) count++;
		return count;
	}
	
	private void matchLocalFilenames() throws CommandAbortedException, CommandException, AuthenticationException, IOException {
		boolean stripCommaV = workFiles.query().single("every $f in //cvs:loginfo/@rcsfile satisfies ends-with($f, ',v')").booleanValue();
		if (stripCommaV) LOG.debug("stripping ',v' from all rcs filenames");
		
		for (Node logInfo : workFiles.query().all("//cvs:loginfo").nodes()) {
			String rcsFull = logInfo.query().single("@rcsfile").value();
			
			// trim repository and ,v extension
			String localName = rcsFull.substring(root.getRepository().length()+1, rcsFull.length() - (stripCommaV ? 2 : 0));
			
			// trim module path prefix to get local relative path
			int k = 0;
			for (int i=count(module, '/'); i>=0; i--) k = localName.indexOf('/', k);
			localName = localName.substring(k+1);

			// TODO: do some sanity-checking here, maybe against adminHandler?
			// but remember, we'll get removed files in the log, so there's probably no trace left of them by this point
			logInfo.update().attr("localfile", localName).commit();
			LOG.debug("matched " + rcsFull + " to " + localName);
		}
	}
	
	private void matchHistoryActions() throws IOException {
		for (Node logInfo : workFiles.query().all("//cvs:loginfo").nodes()) {
			
			String localPath = logInfo.query().single("@localfile").value();
			int k = localPath.lastIndexOf('/');
			String filename = k == -1 ? localPath : localPath.substring(k+1);
			String rcsDirectory = module + (k == -1 ? "" : "/" + localPath.substring(0, k));
			String pattern = "^" + filename + " +" + rcsDirectory + "$";
			LOG.debug("matching history for " + filename + " in " + rcsDirectory);
			
			for (Node revision : logInfo.query().all("cvs:revision").nodes()) {
				// zero out detailed fields from revision date to enable match to history date
				XMLGregorianCalendar revDate = revision.query().single("@date").dateTimeValue();
				revDate.setSecond(0);
				revDate.setFractionalSecond(BigDecimal.ZERO);
				
				char code = workFiles.query()
					.single("//cvs:history[" +
							"@author=$_1/@author and " +
							"@rev=$_1/@name and " +
							"xs:dateTime(@date) eq xs:dateTime($_3) and " +
							"matches(., $_2)]" +
							"/@code", revision, pattern, revDate)
					.value().charAt(0);
				String action;
				switch(code) {
					case 'A': action = "add"; break;
					case 'R': action = "delete"; break;
					case 'M': action = "edit"; break;
					default: throw new RuntimeException("unexpected history code '" + code + "'");
				}
				revision.update().attr("action", action).commit();
				LOG.debug("  revision " + revision.query().single("@name").value() + " action " + action);
			}
		}
	}

	private void translateCommits() {
		for (String commitId : workFiles.query().unordered("distinct-values(//cvs:revision/@commit-id)").values()) {
			ItemList revs = workFiles.query().unordered("//cvs:revision[@commit-id=$_1]", commitId);
			try {
				Node modMem = workFiles.query().let("revs", revs).let("commitid", commitId).single(
						"let \n" +
						"	$author := exactly-one(distinct-values($revs/@author)), \n" +
						"	$message := exactly-one(distinct-values($revs/text())), \n" +
						"	$date := max(xs:dateTime($revs/@date)) \n" +
						"return \n" +
						"	element vcm:modification { \n" +
						"		attribute demarcation {'user'}, \n" +
						"		attribute type {'cvs'}, \n" +
						"		attribute cvs:commit-id {$commitid}, \n" +
						"		element vcm:user {text{$author}}, \n" +
						"		element vcm:description {text{$message}}, \n" +
						"		element vcm:date {text{$date}}, \n" +
						"		for $rev in $revs return \n" +
						"			element vcm:file { \n" +
						"				attribute action {exactly-one($rev/@action)}, \n" +
						"				element vcm:filename {text{exactly-one($rev/parent::*/@localfile)}}, \n" +
						"				element vcm:revision {text{exactly-one($rev/@name)}} \n" +
						"			} \n" +
						"	}"
				).node();
				workFiles.append().node(modMem).commit();
			} catch (DatabaseException e) {
				LOG.warn("different values across components of a server-tracked commit (commitid=" + commitId + "); will synthesize commit instead", e);
				revs.query().unordered("@commit-id").deleteAllNodes();
				continue;
			}
		}
	}
	
	private void inferCommits() {
		for (Node rev : workFiles.query().unordered(
				"for $rev in //cvs:revision " +
				"where not(exists($rev/@commit-id)) " +
				"order by xs:dateTime($rev/@date) " +
				"return $rev").nodes()) {
			rev.query().let("rev", rev);
			Node mod = workFiles.query().optional(
					"//vcm:modification[" +
					"	vcm:user eq $_1/@author and" +
					"	vcm:description eq $_1/text() and" +
					"	not(exists(vcm:file[vcm:filename eq $_1/parent::*/@localfile])) and" +
					"	xs:dateTime($_1/@date) - xs:dateTime(vcm:date) lt xdt:dayTimeDuration($_2)]",
					rev, quietPeriod.duration()
			).node();
			if (mod.extant()) {
				mod.query().single("vcm:date").node().replace().node(rev.query().single("element vcm:date {text{$rev/@date}}").node()).commit();
			} else {
				Node modMem = rev.query().single(
						"element vcm:modification {" +
						"	attribute demarcation {'synthetic'}," +
						"	attribute type {'cvs'}," +
						"	element vcm:user {text{$rev/@author}}," +
						"	element vcm:description {text{$rev/text()}}," +
						"	element vcm:date {text{$rev/@date}}" +
						"}"
				).node();
				mod = workFiles.append().node(modMem).commit();
			}
			Node fileMem = rev.query().single(
					"element vcm:file {" +
					"	attribute action {$rev/@action}," +
					"	element vcm:filename {text{$rev/parent::*/@localfile}}," +
					"	element vcm:revision {text{$rev/@name}}" +
					"}"
			).node();
			mod.append().node(fileMem).commit();
		}
	}
	
	private void executeCommand(Command cmd, File localDir, CVSListener listener) throws CommandException, CommandAbortedException, AuthenticationException, IOException {
		if (LOG.isDebugEnabled()) org.netbeans.lib.cvsclient.util.Logger.setLogging(new File(cycle().tempdir(), module + "_cvs-log").getAbsolutePath());
		LOG.debug("command: " + cmd.getCVSCommand());
		
		Client cvsClient = new Client(ConnectionFactory.getConnection(root), adminHandler);
		cvsClient.setLocalPath(localDir.getAbsolutePath());	// get parent path so that project name can be used as module's checkout name
		cvsClient.ensureConnection();
		
		try {
			if (listener != null) cvsClient.getEventManager().addCVSListener(listener); 
			cvsClient.executeCommand(cmd, globalOptions);
			if (listener != null && listener instanceof MessageListener) ((MessageListener) listener).checkForErrors();
		} finally {
			if (listener != null) cvsClient.getEventManager().removeCVSListener(listener);
			cvsClient.getConnection().close();
			org.netbeans.lib.cvsclient.util.Logger.setLogging(null);
		}
	}
	
	private abstract class MessageListener extends CVSAdapter {
		private final StringBuffer taggedLine = new StringBuffer();
		private final StringBuilder errorMessage = new StringBuilder();
		private final Pattern ignorePattern;

		public MessageListener(String ignoreRegex) {
			ignorePattern = Pattern.compile(ignoreRegex);
		}
		
		/**
		 * Throw an exception with any errors that might have been accumulated
		 * during the interaction with the server.
		 * @throws CommandException if an error was noticed
		 */
		public void checkForErrors() throws CommandException {
			if (errorMessage.length() > 0) throw new CommandException(errorMessage.toString(), errorMessage.toString());
		}
		
		@Override
		public void messageSent(MessageEvent ev) {
			String message = ev.getMessage();
			if (message.length() == 0) return;
			
			if (ev.isTagged()) message = MessageEvent.parseTaggedMessage(taggedLine, message);
			if (message == null || message.length() == 0) return;
			
			LOG.debug(message);
			if (ignorePattern.matcher(message).matches()) return;
			
			if (ev.isError()) {
				if (errorMessage.length() > 0) errorMessage.append('\n');
				errorMessage.append(message);
				return;
			}
			
			parse(message);
		}
		
		protected abstract void parse(String message);
	}
	
	
	private class HistoryListener extends MessageListener {
		private final DateFormat cvsHistoryDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm Z");
		private final Pattern modulePattern = Pattern.compile("[^/]* " + module + "(/[^=]*| +)==.*");
		
		public HistoryListener() {
			super("No records selected\\.");
		}
		
		@Override
		protected void parse(String message) {
			if (!modulePattern.matcher(message).matches()) {
				LOG.debug("ignoring history line above due to module name mismatch");
				return;
			}
			char code = message.charAt(0);
			ParsePosition pp = new ParsePosition(2);
			Date changeDate = cvsHistoryDateFormat.parse(message, pp);
			if (changeDate == null) {
				LOG.warn("failed to parse CVS history date at index " + pp.getErrorIndex() + ": " + message);
				return;
			}
			switch(code) {
				case 'M': case 'R': case 'A':		// commit
					quietPeriod.addChangePoint(changeDate);
					try {
						StringTokenizer tokenizer = new StringTokenizer(message.substring(pp.getIndex()));
						String author = tokenizer.nextToken();
						String revision = tokenizer.nextToken();
						String fileAndDirectory = tokenizer.nextToken("==").trim();
						workFiles.append().elem("cvs:history")
							.attr("code", code)
							.attr("date", changeDate)
							.attr("author", author)
							.attr("rev", revision)
							.text(fileAndDirectory)
						.end("cvs:history").commit();
					} catch (NoSuchElementException e) {
						LOG.warn("failed to parse history entry: " + message);
						// TODO: flag modifications list as inaccurate?
					}
					break;
				case 'T':		// tag
					quietPeriod.addInterestPoint(changeDate);	// TODO: record tag name
					break;
				default:
					LOG.warn("unexpected CVS history code '" + code + "'");
			}
		}
	}
	
	private class CheckoutListener extends MessageListener {
		public CheckoutListener() {
			super("cvs server: .* is no longer in the repository");
		}
		@Override
		protected void parse(String message) {
			// nothing to do
		}
	}

	@SuppressWarnings("unchecked")
	private class LogListener extends CVSAdapter {
		@Override
		public void fileInfoGenerated(FileInfoEvent ev) {
			ElementBuilder<?> b = workFiles.append();
			LogInformation logInfo = (LogInformation) ev.getInfoContainer();
			b.elem("cvs:loginfo").attr("rcsfile", logInfo.getRepositoryFilename());
			for (LogInformation.Revision rev : (List<LogInformation.Revision>) logInfo.getRevisionList()) {
				b.elem("cvs:revision")
				.attr("name", rev.getNumber())
				.attr("author", rev.getAuthor())
				.attr("date", rev.getDate())
				.attrIf(rev.getCommitID() != null, "commit-id", rev.getCommitID())
				.text(rev.getMessage().trim().replaceAll("\\r\\n", "\n").replaceAll("\\r", ""))
				.end("cvs:revision");
			}
			b.end("cvs:loginfo").commit();
		}
	}
}
