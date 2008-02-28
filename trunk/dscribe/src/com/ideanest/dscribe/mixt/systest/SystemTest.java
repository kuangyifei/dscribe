package com.ideanest.dscribe.mixt.systest;

import static org.junit.Assert.*;

import java.io.*;
import java.text.ParseException;
import java.util.*;
import java.util.regex.*;

import org.apache.log4j.*;
import org.custommonkey.xmlunit.*;
import org.exist.fluent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.systest.ParameterizedShowingArgs.Parameters;

@DatabaseTestCase.ConfigFile("test/conf.xml") @RunWith(ParameterizedShowingArgs.class)
public class SystemTest extends DatabaseTestCase {
	
	private static final File TEST_SPEC_DIR = new File("test/systest-specs");

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
		appender.setThreshold(Level.DEBUG);
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
	private static final Pattern INSTRUCTION_RE = Pattern.compile("## (load|modify|check) (file|rules|mods|stats)(.*)");
	private static final Pattern STATS_RE = Pattern.compile("(\\w+)\\s*=\\s*(\\d+)");
	
	private Folder workspace, rulespace;
	private Transformer transformer;
	private int run = 0;
	private File specFile;
	private Engine.Stats stats;
	
	public SystemTest(String name, File specFile) {
		this.specFile = specFile;
	}
	
	@Before public void setUp() {
		workspace = db.createFolder("/workspace");
		rulespace = db.createFolder("/rulespace");
		rulespace.namespaceBindings().put("", Transformer.RULES_NS);
		transformer = new Transformer(workspace, rulespace);
	}
	
	@Test public void run() throws IOException, ParseException, RuleBaseException, TransformException, InterruptedException, SAXException, IllegalArgumentException, SecurityException, IllegalAccessException, NoSuchFieldException {
		LOG.debug("starting system test " + specFile);
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
				LOG.debug("system test " + specFile + " starting run " + run);
				stats = transformer.executeOnce();
				LOG.debug("system test " + specFile + " finished run " + run);
				line = reader.readLine();
			} else if (line.startsWith("##")) {
				Matcher matcher = INSTRUCTION_RE.matcher(line);
				if (!matcher.matches()) throw new IOException("bad ## line: " + line);
				String instruction = matcher.group(1) + " " + matcher.group(2);
				if (instruction.equals("load file")) {
					line = doLoadFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("load rules")) {
						line = doLoadRules(reader);
				} else if (instruction.equals("load mods")) {
					line = doLoadMods(reader);
				} else if (instruction.equals("modify file")) {
					line = doModifyFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("modify rules")) {
					line = doModifyRules(reader);
				} else if (instruction.equals("check file")) {
					line = doCheckFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("check mods")) {
					line = doCheckMods(reader);
				} else if (instruction.equals("check stats")) {
					line = doCheckStats(reader);
				} else {
					throw new IOException("bad ## instruction: " + line);
				}
			}
		}
		LOG.debug("finished system test " + specFile);
	}

	private String doLoadRules(BufferedReader reader) throws IOException, ParseException {
		StringBuilder buf = new StringBuilder();
		String line = readUntilNextInstruction(reader, buf);
		Reader bufReader = new StringReader(buf.toString());
		try {
			rulespace.documents().load(Name.overwrite("rules"), CompactFormTranslator.compactToXml(bufReader));
		} finally {
			bufReader.close();
		}
		return line;
	}

	private String doModifyRules(BufferedReader reader) throws IOException, ParseException {
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
	
	private String doModifyFile(BufferedReader reader, String line, String path) throws IOException {
		if (path.isEmpty()) throw new IOException("no file path: " + line);
		StringBuilder buf = new StringBuilder();
		line = readUntilNextInstruction(reader, buf);
		workspace.documents().get(path).query().all(buf.toString());
		return line;
	}
	
	private String doLoadMods(BufferedReader reader) throws IOException {
		StringBuilder buf = new StringBuilder();
		buf.append("<mods xmlns='" + Transformer.MOD_NS + "' stage='-1'>\n");
		String line = readUntilNextInstruction(reader, buf);
		buf.append("</mods>");
		db.createFolder(Transformer.recordsRootPath() + workspace.path())
				.documents().load(Name.overwrite("mods"), Source.xml(buf.toString()));
		return line;
	}

	private String doCheckFile(BufferedReader reader, String line, String path) throws IOException {
		if (path.isEmpty()) throw new IOException("no file path: " + line);
		StringBuilder buf = new StringBuilder();
		line = readUntilNextInstruction(reader, buf);
		XMLDocument actual = workspace.documents().get(path).xml();
		XMLDocument expected = workspace.documents().load(Name.generate(), Source.xml(buf.toString()));
		try {
			assertTrue(
					"Document " + path + " after run " + run + "\n--- Expected:\n" + expected.contentsAsString() + "\n\n--- Actual:\n" + actual.contentsAsString() + "\n",
					workspace.query().single("deep-equal($_1, $_2)", actual.root(), expected.root()).booleanValue());
		} finally {
			expected.delete();
		}
		return line;
	}

	private String doCheckMods(BufferedReader reader) throws IOException, SAXException {
		StringBuilder buf = new StringBuilder();
		buf.append("<mods xmlns='" + Transformer.MOD_NS + "' stage='-1'>\n");
		String line = readUntilNextInstruction(reader, buf);
		buf.append("</mods>");
		String expected = buf.toString();
		String actual = db.getDocument(Transformer.recordsRootPath() + workspace.path() + "/mods").contentsAsString();
		XMLUnit.setNormalizeWhitespace(true);
		XMLUnit.setIgnoreAttributeOrder(true);
		Diff diff = new Diff(expected, actual);
		diff.overrideDifferenceListener(new DifferenceListener() {
			public int differenceFound(Difference difference) {
				if (difference.getId() == DifferenceEngine.ATTR_VALUE_ID && "*".equals(difference.getControlNodeDetail().getValue())) {
					return DifferenceListener.RETURN_IGNORE_DIFFERENCE_NODES_IDENTICAL;
				} else if (difference.getId() == DifferenceEngine.TEXT_VALUE_ID && "*".equals(difference.getControlNodeDetail().getValue())) {
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
				if (control.hasAttribute("xml:id")) {
					return test.hasAttribute("xml:id") && control.getAttribute("xml:id").equals(test.getAttribute("xml:id"));
				} else if (control.hasAttribute("stage")) {
					return test.hasAttribute("stage") && control.getAttribute("stage").equals(test.getAttribute("stage"));
				} else {
					return true;
				}
			}
		});
		if (!diff.similar()) {
			fail("Mods differ after run " + run + "\n--- Expected:\n" + expected + "\n\n--- Actual: \n" + actual + "\n");
		}
		return line;
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
