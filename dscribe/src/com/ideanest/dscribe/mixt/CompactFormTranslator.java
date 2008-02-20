package com.ideanest.dscribe.mixt;

import static org.junit.Assert.fail;

import java.io.*;
import java.text.ParseException;
import java.util.*;
import java.util.regex.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.Namespace;

public class CompactFormTranslator {
	
	private static final Pattern INDENT_PATTERN = Pattern.compile("^(\\s*)(.*)$");
	private static final Pattern TO_PATTERN = Pattern.compile("^to (.*) \\[(.+)\\]$");

	private CompactFormTranslator() {}
	
	public static Source.XML compactToXml(Reader compactFormTextReader) throws IOException, ParseException {
		StringBuilder buf = new StringBuilder();
		buf.append("<rules xmlns:rules='" + Transformer.RULES_NS + "'");
		BufferedReader in = new BufferedReader(compactFormTextReader);
		Deque<String> indents = new LinkedList<String>();
		Deque<String> tags = new LinkedList<String>();
		indents.addFirst("");
		String line, textIndent = null;
		boolean inTextRun = false, needIndentedText = false, inPreamble = true;
		for(int lineNumber = 1; (line = in.readLine()) != null; lineNumber++) {
			Matcher lineMatcher = INDENT_PATTERN.matcher(line);
			if (!lineMatcher.matches()) throw new ParseException("indent pattern failed to match line " + lineNumber + ":\n" + line, 0);
			String indent = lineMatcher.group(1);
			String content = lineMatcher.group(2).trim();
			if (content.length() == 0) continue;
			if (indent.equals(indents.peekFirst())) {
				if (!inPreamble) buf.append("</rules:" + tags.removeFirst() + ">");
				if (needIndentedText) throw new ParseException("expected indented text block after line " + (lineNumber-1) + " but got:\n" + line, 0);
				inTextRun = false;
			} else {
				if (indent.startsWith(indents.peekFirst())) {
					if (inTextRun) {
						if (needIndentedText) {
							textIndent = indent;
						} else {
							if (!indent.startsWith(textIndent)) throw new ParseException("indented text block has inconsistent indent on line " + lineNumber + ":\n" + line, 0);
						}
					} else {
						// indent in one level
						indents.addFirst(indent);
					}
				} else {
					if (needIndentedText) throw new ParseException("expected indented text block after line " + (lineNumber-1) + " but got:\n" + line, 0);
					inTextRun = false;
					try {
						do {
							indents.removeFirst();
							buf.append("</rules:" + tags.removeFirst() + ">");
						} while (!indent.equals(indents.peekFirst()));
					} catch (NoSuchElementException e) {
						throw new ParseException("indent at line " + lineNumber + " is shorter than on the previous line, and doesn't match any previous indent", 0);
					}
					buf.append("</rules:" + tags.removeFirst() + ">");
				}
			}
			if (inTextRun) {
				buf.append(indent.substring(textIndent.length())).append(content).append("\n");
				needIndentedText = false;
			} else {
				int k = content.indexOf(':');
				String[] specParts = (k == -1 ? content : content.substring(0, k)).split("\\s+");
				String keyword = specParts[0];
				if (keyword.equals("namespace")) {
					if (!inPreamble) throw new ParseException("namespace directive only allowed in preamble, but seen on line " + lineNumber + ":\n" + line, indent.length());
					specParts = content.split("\\s+");
					if (specParts.length != 3) throw new ParseException("namespace directive takes prefix and namespace URI as arguments, which weren't found on line " + lineNumber + ":\n" + line, indent.length());
					if (specParts[1].equals("rules")) throw new ParseException("the 'rules' namespace prefix is reserved by the compact form translator on line " + lineNumber + ":\n" + line, indent.length());
					buf.append(" xmlns:" + specParts[1] + "='" + specParts[2] + "'");
				} else {
					if (inPreamble) buf.append(">");
					inPreamble = false;
					if (keyword.equals("to")) {
						if (indents.size() != 1) throw new ParseException("rule definitions must be at outermost level, but found a nested one on line " + lineNumber + ":\n" + line, indent.length());
						Matcher toMatcher = TO_PATTERN.matcher(content);
						if (!toMatcher.matches()) throw new ParseException("rule definition syntax doesn't match 'to <rule name> [<id>]' on line " + lineNumber + ":\n" + line, indent.length());
						buf.append("<rules:rule xml:id='" + toMatcher.group(2) + "' name='" + toMatcher.group(1) + "'>");
						tags.addFirst("rule");
					} else {
						if (indents.size() == 1) throw new ParseException("rule blocks must be nested in a rule, but found one at outermost level on line " + lineNumber + ":\n" + line, indent.length());
						buf.append("<rules:" + keyword);
						tags.addFirst(keyword);
						if (specParts.length % 2 != 1) throw new ParseException("unpaired attribute on line " + lineNumber + ":\n" + line, indent.length());
						for (int i=1; i<specParts.length; i+=2) {
							buf.append(" " + specParts[i] + "='" + specParts[i+1] + "'");
						}
						buf.append(">");
						if (k != -1) {
							String text = k+1 >= content.length() ? "" : content.substring(k+1).trim();
							if (text.length() == 0) {
								inTextRun = true;
								needIndentedText = true;
							} else {
								buf.append(text);
							}
						}
					}
				}
			}
		}
		if (inPreamble) buf.append(">");
		while (tags.size() > 0) buf.append("</rules:" + tags.removeFirst() + ">");
		buf.append("</rules>");
		return Source.xml(buf.toString());
	}
	
	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml")
	public static class _Test extends DatabaseTestCase {
		private final StringBuilder compactText = new StringBuilder(), xml = new StringBuilder();
		private StringBuilder captureTarget;
		
		private void captureInput() {
			captureTarget = compactText;
		}
		
		private void captureOutput() {
			captureTarget = xml;
		}
		
		private void _(String line) {
			captureTarget.append(line).append("\n");
		}
		
		private void translateAndCheck() throws IOException, ParseException {
			Node target = db.getFolder("/").documents().load(Name.create("target"), Source.xml(xml.toString())).root();
			Node result = db.getFolder("/").documents().load(Name.create("translationResult"), compactToXml(new StringReader(compactText.toString()))).root();
			if (!db.query().single("deep-equal($_1, $_2)", target, result).booleanValue()) {
				fail("translation doesn't match\n\nExpected:\n" + target + "\n\nActual:\n" + result + "\n");
			}
		}
		
		private void translateBadInput() throws ParseException, IOException {
			ElementBuilder<XMLDocument> builder = db.getFolder("/").documents().build(Name.create("translationResult"));
			compactToXml(new StringReader(compactText.toString()));
			builder.commit();
		}
		
		@Test public void empty() throws IOException, ParseException {
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'/>");
			translateAndCheck();
		}
		
		@Test public void namespaceDeclarations() throws IOException, ParseException {
			captureInput();
			_("namespace java " + Namespace.JAVA);
			_("namespace uml " + Namespace.UML);
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "' xmlns:java='" + Namespace.JAVA + "' xmlns:uml='" + Namespace.UML + "'/>");
			translateAndCheck();
		}
		
		@Test public void oneRuleWithoutBlocks() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'/>");
			_("</rules>");
			translateAndCheck();
		}
		
		@Test public void twoRulesWithoutBlocks1() throws IOException, ParseException {
			captureInput();
			_("to do something [r1]");
			_("to do other [r2]");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something'/>");
			_("	<rule xml:id='r2' name='do other'/>");
			_("</rules>");
			translateAndCheck();
		}
		
		@Test public void twoRulesWithoutBlocks2() throws IOException, ParseException {
			captureInput();
			_("to do something [r1]");
			_("");
			_("to do other [r2]");
			_("");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something'/>");
			_("	<rule xml:id='r2' name='do other'/>");
			_("</rules>");
			translateAndCheck();
		}
		
		@Test public void oneRuleWithEmptyBlocks() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	for");
			_("	insert");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<for/><insert/>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}

		@Test public void blockWithAttributes() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	for some $x and $y");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<for some='$x' and='$y'/>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}

		@Test public void blockWithInlineText() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	for: foo bar bar : is blah");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<for>foo bar bar : is blah</for>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}

		@Test public void blockWithInlineElements() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	insert: <bar/>");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<insert><bar/></insert>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}

		@Test public void blockWithAttributesAndInlineText() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	for any $x: foo bar bar : is blah");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<for any='$x'>foo bar bar : is blah</for>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}

		@Test public void blockWithInlineTextBetweenOtherBlocks() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	with this $x");
			_("	for: foo bar bar : is blah");
			_("	with that $y");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<with this='$x'/>");
			_("		<for>foo bar bar : is blah</for>");
			_("		<with that='$y'/>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}

		@Test public void blockWithIndentedText() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	for:");
			_("		something like: this and");
			_("			this more indented stuff too");
			_("		then back to normal");
			_("	with that $y");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<for>something like: this and");
			_("	this more indented stuff too");
			_("then back to normal");
			_("</for>");
			_("		<with that='$y'/>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}
		
		@Test public void nestedBlocks() throws IOException, ParseException {
			captureInput();
			_("to do something or other [r1]");
			_("	for");
			_("		with that $y");
			_("		with this $x");
			_("	insert");
			captureOutput();
			_("<rules xmlns='"+ Transformer.RULES_NS + "'>");
			_("	<rule xml:id='r1' name='do something or other'>");
			_("		<for>");
			_("			<with that='$y'/>");
			_("			<with this='$x'/>");
			_("		</for>");
			_("		<insert/>");
			_("	</rule>");
			_("</rules>");
			translateAndCheck();
		}
		@Test(expected = ParseException.class)
		public void namespaceAfterPreamble() throws ParseException, IOException {
			captureInput();
			_("to do stuff [r1]");
			_("namespace java http://foo");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badNamespace1() throws ParseException, IOException {
			captureInput();
			_("namespace java ");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badNamespace2() throws ParseException, IOException {
			captureInput();
			_("namespace java foo bar");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badIndent1() throws ParseException, IOException {
			captureInput();
			_("to do stuff [r1]");
			_("	for");
			_("  bar");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badIndent2() throws ParseException, IOException {
			captureInput();
			_("to do stuff [r1]");
			_("	for:");
			_("	bar");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badIndent3() throws ParseException, IOException {
			captureInput();
			_("to do stuff [r1]");
			_("	for:");
			_("		line 1");
			_("	  line 2");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badIndent4() throws ParseException, IOException {
			captureInput();
			_("to do stuff [r1]");
			_("for");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void ruleNotAtTopLevel() throws ParseException, IOException {
			captureInput();
			_("to do stuff [r1]");
			_("	to do nested [r2]");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badRuleDeclaration() throws ParseException, IOException {
			captureInput();
			_("to do stuff");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void unpairedAttribute() throws ParseException, IOException {
			captureInput();
			_("to do stuff [r1]");
			_("	for any $x empty");
			translateBadInput();
		}

	}
}
