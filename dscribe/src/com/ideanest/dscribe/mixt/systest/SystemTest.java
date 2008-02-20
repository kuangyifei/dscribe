package com.ideanest.dscribe.mixt.systest;

import static org.junit.Assert.assertTrue;

import java.io.*;
import java.text.ParseException;
import java.util.regex.*;

import org.exist.fluent.*;
import org.junit.*;

import com.ideanest.dscribe.mixt.*;

@DatabaseTestCase.ConfigFile("test/conf.xml")
public class SystemTest extends DatabaseTestCase {
	
	private static final Pattern STAGE_RE = Pattern.compile("#### stage (\\d+)");
	private static final Pattern INSTRUCTION_RE = Pattern.compile("## (set|check) (file|rules)(.*)");
	
	private Folder workspace, rulespace;
	private Transformer transformer;
	
	@Before public void setUp() {
		workspace = db.createFolder("/workspace");
		rulespace = db.createFolder("/rulespace");
		transformer = new Transformer(workspace, rulespace);
	}
	
	@Test public void run() throws IOException, ParseException, RuleBaseException, TransformException, InterruptedException {
		BufferedReader reader = new BufferedReader(new FileReader("test/systest-specs/simple.txt"));
		int stage = -1;
		String line = reader.readLine();
		while (line != null) {
			line = line.trim();
			if (line.isEmpty()) {line = reader.readLine(); continue;}
			if (line.startsWith("####")) {
				Matcher matcher = STAGE_RE.matcher(line);
				if (!matcher.matches()) throw new IOException("bad #### line: " + line);
				int nextStage = Integer.parseInt(matcher.group(1));
				if (nextStage != ++stage) throw new IOException("non-consecutive stage: " + line);
				if (stage != 0) transformer.executeOnce();
				line = reader.readLine();
			} else if (line.startsWith("##")) {
				Matcher matcher = INSTRUCTION_RE.matcher(line);
				if (!matcher.matches()) throw new IOException("bad ## line: " + line);
				if ("set".equals(matcher.group(1))) {
					if ("file".equals(matcher.group(2))) {
						line = doSetFile(reader, line, matcher.group(3).trim());
					} else if ("rules".equals(matcher.group(2))) {
						line = doSetRules(reader);
					} else {
						throw new IOException("bad set argument: " + line);
					}
				} else if ("check".equals(matcher.group(1))) {
					if ("file".equals(matcher.group(2))) {
						line = doCheckFile(reader, line, matcher.group(3).trim());
					} else if ("mods".equals(matcher.group(2))) {
						// TODO: implement modStore checker
					} else {
						throw new IOException("bad check argument: " + line);
					}
				}
			}
		}
	}

	private String doSetRules(BufferedReader reader) throws IOException, ParseException {
		String line;
		StringBuilder buf = new StringBuilder();
		line = readUntilNextInstruction(reader, buf);
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

	private String readUntilNextInstruction(BufferedReader reader, StringBuilder buf) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("##")) break;
			buf.append(line).append('\n');
		}
		return line;
	}
}
