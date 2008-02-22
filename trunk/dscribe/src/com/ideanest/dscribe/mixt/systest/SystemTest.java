package com.ideanest.dscribe.mixt.systest;

import static org.junit.Assert.*;

import java.io.*;
import java.text.ParseException;
import java.util.*;
import java.util.regex.*;

import org.custommonkey.xmlunit.*;
import org.exist.fluent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.w3c.dom.*;
import org.w3c.dom.Node;
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
	
	private static final Pattern STAGE_RE = Pattern.compile("#### cycle (\\d+)");
	private static final Pattern INSTRUCTION_RE = Pattern.compile("## (set|check) (file|rules|mods)(.*)");
	
	private Folder workspace, rulespace;
	private Transformer transformer;
	private int cycle = -1;
	private File specFile;
	
	public SystemTest(String name, File specFile) {
		this.specFile = specFile;
	}
	
	@Before public void setUp() {
		workspace = db.createFolder("/workspace");
		rulespace = db.createFolder("/rulespace");
		transformer = new Transformer(workspace, rulespace);
	}
	
	@Test public void run() throws IOException, ParseException, RuleBaseException, TransformException, InterruptedException, SAXException {
		BufferedReader reader = new BufferedReader(new FileReader(specFile));
		String line = reader.readLine();
		while (line != null) {
			line = line.trim();
			if (line.isEmpty()) {line = reader.readLine(); continue;}
			if (line.startsWith("####")) {
				Matcher matcher = STAGE_RE.matcher(line);
				if (!matcher.matches()) throw new IOException("bad #### line: " + line);
				int nextStage = Integer.parseInt(matcher.group(1));
				if (nextStage != ++cycle) throw new IOException("non-consecutive cycle: " + line);
				if (cycle != 0) transformer.executeOnce();
				line = reader.readLine();
			} else if (line.startsWith("##")) {
				Matcher matcher = INSTRUCTION_RE.matcher(line);
				if (!matcher.matches()) throw new IOException("bad ## line: " + line);
				String instruction = matcher.group(1) + " " + matcher.group(2);
				if (instruction.equals("set file")) {
					line = doSetFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("set rules")) {
						line = doSetRules(reader);
				} else if (instruction.equals("set mods")) {
					line = doSetMods(reader);
				} else if (instruction.equals("check file")) {
					line = doCheckFile(reader, line, matcher.group(3).trim());
				} else if (instruction.equals("check mods")) {
					line = doCheckMods(reader);
				} else {
					throw new IOException("bad ## instruction: " + line);
				}
			}
		}
	}

	private String doSetRules(BufferedReader reader) throws IOException, ParseException {
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

	private String doSetFile(BufferedReader reader, String line, String path) throws IOException {
		if (path.isEmpty()) throw new IOException("no file path: " + line);
		StringBuilder buf = new StringBuilder();
		line = readUntilNextInstruction(reader, buf);
		workspace.documents().load(Name.overwrite(path), Source.xml(buf.toString()));
		return line;
	}
	
	private String doSetMods(BufferedReader reader) throws IOException {
		StringBuilder buf = new StringBuilder();
		buf.append("<mods xmlns='" + Transformer.MOD_NS + "'>\n");
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
					"Document " + path + "\n--- Expected:\n" + expected.contentsAsString() + "\n\n--- Actual:\n" + actual.contentsAsString() + "\n",
					workspace.query().single("deep-equal($_1, $_2)", actual.root(), expected.root()).booleanValue());
		} finally {
			expected.delete();
		}
		return line;
	}

	private String doCheckMods(BufferedReader reader) throws IOException, SAXException {
		StringBuilder buf = new StringBuilder();
		buf.append("<mods xmlns='" + Transformer.MOD_NS + "'>\n");
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
			public void skippedComparison(Node control, Node test) {
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
			fail("Mods differ after cycle " + cycle + "\n--- Expected:\n" + expected + "\n\n--- Actual: \n" + actual + "\n");
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
