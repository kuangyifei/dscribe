package com.ideanest.dscribe.mixt.test;

import static org.junit.Assert.*;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

import org.apache.log4j.*;
import org.apache.tools.ant.DirectoryScanner;
import org.custommonkey.xmlunit.*;
import org.exist.fluent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs.Parameters;

@DatabaseTestCase.ConfigFile("test/conf.xml") @RunWith(ParameterizedShowingArgs.class)
public class SystemTest extends DatabaseTestCase {
	
	private static final File TEST_SPEC_DIR = new File("test/systest-specs");
	
	public static void main(String args[]) throws Exception {
		if (!args[1].startsWith("test/perf")) configureLogging();
		SystemTest test = new SystemTest(args[1], new File(args[1]));
		test.startupDatabase();
		try {
			int numRuns = Integer.parseInt(args[0], 10);
			for (int i = numRuns; i > 0; i--) {
				test = new SystemTest(args[1], new File(args[1]));
				test.dumpAndResetStatsAfterEachCycle = true;
				try {
					test.setUp();
					test.run();
				} finally {
					if (numRuns == 1 && args[1].startsWith("test/perf")) dumpDatabase();
				}
				db.getFolder("/workspace").delete();
				db.getFolder("/rulespace").delete();
				db.getFolder(Transformer.recordsRootPath()).delete();
			}
		} finally {
			try {
				SystemTest.shutdownDatabase();
			} catch (Exception e) {
				System.err.println("exception trying to shut down:");
				e.printStackTrace();
			}
		}
	}

	private static void dumpDatabase() {
		try {
			File debugDest = new File("test/debug-dump");
			deleteDir(debugDest);
			db.getFolder("/").export(debugDest);
			LOG.debug("dumped final database into '" + debugDest + "'");
		} catch (IOException e) {
			LOG.error("failed to dump database for debugging", e);
		}
	}

	private static void deleteDir(File dir) {
		if (dir.isDirectory()) for (File child : dir.listFiles()) deleteDir(child);
		dir.delete();
	}
	
	@Parameters public static Collection<Object[]> findSpecFiles() {
		List<Object[]> list = new ArrayList<Object[]>();
		for (File file : TEST_SPEC_DIR.listFiles()) {
			if (!file.isHidden()) list.add(new Object[] {stripExtension(file.getName()), file});
		}
		return list;
	}
	
	private static String stripExtension(String filename) {
		int k = filename.lastIndexOf('.');
		return k > 0 ? filename.substring(0, k) : filename;
	}
	
	private static Level previousLoggerLevel;
	private static Priority previousAppenderThreshold;
	@BeforeClass public static void configureLogging() {
		ConsoleAppender appender = (ConsoleAppender) Logger.getRootLogger().getAllAppenders().nextElement();
		previousAppenderThreshold = appender.getThreshold();
		appender.setThreshold(Level.TRACE);
		Logger logger = Logger.getLogger("com.ideanest.dscribe.mixt");
		previousLoggerLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
	}
	@AfterClass public static void unconfigureLogging() {
		((ConsoleAppender) Logger.getRootLogger().getAllAppenders().nextElement()).setThreshold(previousAppenderThreshold);
		Logger.getLogger("com.ideanest.dscribe.mixt").setLevel(previousLoggerLevel);
	}
	
	private static final Logger LOG = Logger.getLogger(SystemTest.class);
	
	private static final Pattern STAGE_RE = Pattern.compile("#### run (\\d+)");
	private static final Pattern INSTRUCTION_RE = Pattern.compile("## (load|reload|modify|check) (file|directory|rules|mods|stats)(.*)");
	private static final Pattern STATS_RE = Pattern.compile("(\\w+)\\s*=\\s*(\\d+)");
	
	private Folder workspace, rulespace;
	private Transformer transformer;
	private int run = 0;
	private File specFile;
	private Engine.Stats stats;
	private boolean dumpAndResetStatsAfterEachCycle;
	
	public SystemTest(String name, File specFile) {
		this.specFile = specFile;
	}
	
	@Before public void setUp() {
		workspace = db.createFolder("/workspace");
		rulespace = db.createFolder("/rulespace");
		rulespace.namespaceBindings().put("", Engine.MIXT_NS);
		transformer = new Transformer(workspace, rulespace);
	}
	
	@Test public void run() throws IOException, ParseException, RuleBaseException, TransformException, InterruptedException, SAXException, IllegalArgumentException, SecurityException, IllegalAccessException, NoSuchFieldException {
		LOG.info("starting system test " + specFile);
		BufferedReader reader = new BufferedReader(new FileReader(specFile));
		String line = reader.readLine();
		while (line != null) {
			line = line.trim();
			if (line.isEmpty()) {line = reader.readLine(); continue;}
			if (line.startsWith("####")) {
				Matcher matcher = STAGE_RE.matcher(line);
				if (!matcher.matches()) throw new IOException("bad #### line: " + line);
				int nextStage = Integer.parseInt(matcher.group(1));
				if (nextStage != ++run) throw new IOException("non-consecutive cycle: " + line);
				LOG.info("system test " + specFile + " starting run " + run);
				long startTime = System.currentTimeMillis();
				stats = transformer.executeOnce();
				double runTime = (System.currentTimeMillis() - startTime) / 1000.0;
				LOG.info("system test " + specFile + " finished run " + run + " in " + new DecimalFormat("0.000").format(runTime) + "s");
				if (dumpAndResetStatsAfterEachCycle) {
					System.out.println(QueryService.statistics().toStringTop(20));
					QueryService.statistics().reset();
				}
				line = reader.readLine();
			} else if (line.startsWith("##")) {
				Matcher matcher = INSTRUCTION_RE.matcher(line);
				if (!matcher.matches()) throw new IOException("bad ## line: " + line);
				String instruction = matcher.group(1) + " " + matcher.group(2);
				if (instruction.equals("load file")) {
					line = doLoadFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("load directory")) {
					line = doLoadDirectory(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("load rules")) {
					line = doLoadRules(reader, matcher.group(3).trim());
				} else if (instruction.equals("load mods")) {
					line = doLoadMods(reader);
				} else if (instruction.equals("modify file")) {
					line = doModifyFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("modify rules")) {
					line = doReloadRules(reader);
				} else if (instruction.equals("check file")) {
					line = doCheckFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("check directory")) {
					line = doCheckDirectory(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("check mods")) {
					line = doCheckMods(reader);
				} else if (instruction.equals("check stats")) {
					line = doCheckStats(reader);
				} else {
					throw new IOException("bad ## instruction: " + line);
				}
			} else {
				throw new IOException("unexpected non-instruction line: " + line);
			}
		}
		LOG.info("finished system test " + specFile);
	}

	private String doLoadRules(BufferedReader reader, String path) throws IOException, ParseException {
		String line;
		Reader mxcReader;
		if (path.isEmpty()) {
			StringBuilder buf = new StringBuilder();
			line = readUntilNextInstruction(reader, buf);
			mxcReader = new StringReader(buf.toString());
		} else {
			line = reader.readLine();
			mxcReader = new FileReader(new File(path));
		}
		try {
			rulespace.documents().load(Name.adjust("rules"), CompactFormTranslator.compactToXml(mxcReader));
		} finally {
			mxcReader.close();
		}
		return line;
	}

	private String doReloadRules(BufferedReader reader) throws IOException, ParseException {
		StringBuilder buf = new StringBuilder();
		String line = readUntilNextInstruction(reader, buf);
		Reader bufReader = new StringReader(buf.toString());
		try {
			XMLDocument newRules = rulespace.documents().load(Name.adjust("rules"), CompactFormTranslator.compactToXml(bufReader));
			for (Node rule : rulespace.query().unordered("/id($_1//rule/@xml:id)", newRules).nodes()) {
				if (!rule.document().equals(newRules)) rule.delete();
			}
		} finally {
			bufReader.close();
		}
		return line;
	}

	private String doLoadFile(BufferedReader reader, String line, String path) throws IOException {
		if (path.isEmpty()) throw new IOException("no file path: " + line);
		StringBuilder buf = new StringBuilder();
		line = readUntilNextInstruction(reader, buf);
		workspace.documents().load(Name.overwrite(path), Source.xml(buf.toString()));
		return line;
	}
	
	private String doLoadDirectory(BufferedReader reader, String line, String path) throws IOException {
		if (path.isEmpty()) throw new IOException("no directory path: " + line);
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(path);
		scanner.addDefaultExcludes();
		scanner.scan();
		for (String relativeName : scanner.getIncludedFiles()) {
			workspace.documents().load(Name.overwrite(relativeName.replace('\\', '/')), Source.xml(new File(path, relativeName)));
		}
		return reader.readLine();
	}
	
	private String doModifyFile(BufferedReader reader, String line, String path) throws IOException {
		if (path.isEmpty()) throw new IOException("no file path: " + line);
		StringBuilder buf = new StringBuilder();
		line = readUntilNextInstruction(reader, buf);
		workspace.documents().get(path).query().run(buf.toString());
		return line;
	}
	
	private String doLoadMods(BufferedReader reader) throws IOException {
		StringBuilder buf = new StringBuilder();
		buf.append("<modstore xmlns='" + Engine.MOD_NS + "'>\n");
		String line = readUntilNextInstruction(reader, buf);
		buf.append("</modstore>");
		db.createFolder(Transformer.recordsRootPath() + workspace.path())
				.documents().load(Name.overwrite("mods"), Source.xml(buf.toString()));
		return line;
	}

	private String doCheckFile(BufferedReader reader, String line, String path) throws IOException, SAXException {
		if (path.isEmpty()) throw new IOException("no file path: " + line);
		StringBuilder buf = new StringBuilder();
		line = readUntilNextInstruction(reader, buf);
		assertSourceXmlMatches(path, Source.xml(buf.toString()));
		return line;
	}
	
	private String doCheckDirectory(BufferedReader reader, String line, String path) throws IOException, SAXException {
		if (path.isEmpty()) throw new IOException("no file path: " + line);
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(path);
		scanner.addDefaultExcludes();
		scanner.scan();
		for (String relativeName : scanner.getIncludedFiles()) {
			assertSourceXmlMatches(relativeName.replace('\\', '/'), Source.xml(new File(path, relativeName)));
		}
		return reader.readLine();
	}
	
	private void assertSourceXmlMatches(String path, Source.XML expectedSource) throws SAXException, IOException {
		XMLDocument actual;
		try {
			actual = workspace.documents().get(path).xml();
		} catch (DatabaseException e) {
			fail(e.getMessage() + "\nDocuments in workspace:\n" + listWorkspaceDocuments());
			return;
		}
		XMLDocument expected = workspace.documents().load(Name.generate(), expectedSource);
		try {
			assertXMLMatches(path, expected.contentsAsString(), actual.contentsAsString());
		} finally {
			expected.delete();
		}
	}
	
	private String listWorkspaceDocuments() {
		StringBuilder buf = new StringBuilder();
		LinkedList<Folder> stack = new LinkedList<Folder>();
		stack.add(workspace);
		while(!stack.isEmpty()) {
			Folder folder = stack.removeFirst();
			for (Document d : folder.documents()) buf.append("  " + workspace.relativePath(d.path()) + "\n");
			for (Folder f : folder.children()) stack.add(f);
		}
		return buf.toString();
	}

	private String doCheckMods(BufferedReader reader) throws IOException, SAXException {
		StringBuilder buf = new StringBuilder();
		buf.append("<modstore xmlns='" + Engine.MOD_NS + "'>\n");
		String line = readUntilNextInstruction(reader, buf);
		buf.append("</modstore>");
		String expected = buf.toString();
		String actual = db.getDocument(Transformer.recordsRootPath() + workspace.path() + "/mods").contentsAsString();
		assertXMLMatches("mods", expected, actual);
		return line;
	}

	private void assertXMLMatches(String what, String expected, String actual) throws SAXException, IOException {
		XMLUnit.setNormalizeWhitespace(true);
		XMLUnit.setIgnoreAttributeOrder(true);
		Diff diff = new Diff(expected, actual);
		diff.overrideDifferenceListener(new DifferenceListener() {
			public int differenceFound(Difference difference) {
				if (difference.getId() == DifferenceEngine.ATTR_VALUE_ID && "_".equals(difference.getControlNodeDetail().getValue())) {
					return DifferenceListener.RETURN_IGNORE_DIFFERENCE_NODES_IDENTICAL;
				} else if (difference.getId() == DifferenceEngine.TEXT_VALUE_ID && "_".equals(difference.getControlNodeDetail().getValue())) {
					return DifferenceListener.RETURN_IGNORE_DIFFERENCE_NODES_IDENTICAL;
				} else {
					return DifferenceListener.RETURN_ACCEPT_DIFFERENCE;
				}
			}
			public void skippedComparison(org.w3c.dom.Node control, org.w3c.dom.Node test) {
			}
		});
		diff.overrideElementQualifier(new ElementQualifier() {
			public boolean qualifyForComparison(Element control, Element test) {
				if (control.hasAttribute("xml:id") && !control.getAttribute("xml:id").equals("_")) {
					return test.hasAttribute("xml:id") && control.getAttribute("xml:id").equals(test.getAttribute("xml:id"));
				} else if (control.hasAttribute("stage") && !control.getAttribute("stage").equals("_")) {
					return test.hasAttribute("stage") && control.getAttribute("stage").equals(test.getAttribute("stage"));
				} else {
					return true;
				}
			}
		});
		if (!diff.similar()) {
			fail(what + " mismatch after run " + run + "\n--- Expected:\n" + expected + "\n\n--- Actual: \n" + actual + "\n");
		}
	}
	
	private String doCheckStats(BufferedReader reader) throws IOException, IllegalArgumentException, SecurityException, IllegalAccessException, NoSuchFieldException {
		if (stats == null) throw new RuntimeException("can't check stats before first engine run");
		String line;
		while ((line = reader.readLine()) != null && !line.startsWith("##")) {
			line = line.trim();
			if (line.isEmpty()) continue;
			Matcher matcher = STATS_RE.matcher(line);
			if (!matcher.matches()) throw new IOException("bad check stats line: " + line);
			long expectedValue = Long.parseLong(matcher.group(2));
			long actualValue = ((Counter) Engine.Stats.class.getField(matcher.group(1)).get(stats)).value();
			assertEquals("stat " + matcher.group(1) + " after run " + run, expectedValue, actualValue);
		}
		return line;
	}

	private String readUntilNextInstruction(BufferedReader reader, StringBuilder buf) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("##")) break;
			buf.append(line).append('\n');
		}
		return line;
	}
}
