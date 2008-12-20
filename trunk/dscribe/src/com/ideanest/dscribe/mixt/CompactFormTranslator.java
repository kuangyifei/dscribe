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
	private static final Pattern RULE_PATTERN = Pattern.compile("^rule (.*?)( \\[(.+)\\])?$");
	private static final Pattern FUNCTION_PATTERN = Pattern.compile("^function (.*?)\\s*\\(\\s*(\\$\\w+\\s*(\\,\\s*\\$\\w+\\s*)*)?\\)\\s*:");

	private CompactFormTranslator() {}
	
	public static Source.XML compactToXml(Reader compactFormTextReader) throws IOException, ParseException {
		StringBuilder buf = new StringBuilder();
		buf.append("<mixt:rules xmlns:mixt='" + Engine.MIXT_NS + "'");
		BufferedReader in = new BufferedReader(compactFormTextReader);
		Deque<String> indents = new LinkedList<String>();
		Deque<String> tags = new LinkedList<String>();
		indents.addFirst("");
		String line, textIndent = null;
		boolean inTextRun = false, needIndentedText = false, inPreamble = true, atRule = false;
		for(int lineNumber = 1; (line = in.readLine()) != null; lineNumber++) {
			if (line.trim().length() == 0) continue;	// skip empty lines, don't care about indentation
			Matcher lineMatcher = INDENT_PATTERN.matcher(line);
			if (!lineMatcher.matches()) throw new ParseException("indent pattern failed to match line " + lineNumber + ":\n" + line, 0);
			String indent = lineMatcher.group(1);
			String content = lineMatcher.group(2).trim();
			if (indent.equals(indents.peekFirst())) {
				if (!inPreamble) buf.append("</mixt:" + tags.removeFirst() + ">");
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
							buf.append("</mixt:" + tags.removeFirst() + ">");
						} while (!indent.equals(indents.peekFirst()));
					} catch (NoSuchElementException e) {
						throw new ParseException("indent at line " + lineNumber + " is shorter than on the previous line, and doesn't match any previous indent", 0);
					}
					buf.append("</mixt:" + tags.removeFirst() + ">");
				}
			}
			if (inTextRun) {
				buf.append(indent.substring(textIndent.length())).append(escapeXMLChars(content)).append("\n");
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
					boolean parseText = false;
					if (keyword.equals("function")) {
						if (indents.size() != 1) throw new ParseException("function definitions must be at outermost level, but found a nested one on line " + lineNumber + ":\n" + line, indent.length());
						Matcher functionMatcher = FUNCTION_PATTERN.matcher(content);
						if (!functionMatcher.lookingAt()) throw new ParseException("function definition syntax doesn't match 'function <function name>(<arg1>, <arg2>, ...' on line " + lineNumber + ":\n" + line, indent.length());
						k = functionMatcher.end() - 1;
						buf.append("<mixt:function");
						buf.append(" name='").append(functionMatcher.group(1)).append("'");
						if (functionMatcher.group(2) != null) buf.append(" args='").append(functionMatcher.group(2)).append("'");
						buf.append(">");
						tags.addFirst("function");
						parseText = true;
					} else if (keyword.equals("rule")) {
						if (indents.size() != 1) throw new ParseException("rule definitions must be at outermost level, but found a nested one on line " + lineNumber + ":\n" + line, indent.length());
						if (k != -1) throw new ParseException("rule definitions must not have text content on line " + lineNumber + ":\n" + line, indent.length());
						Matcher ruleMatcher = RULE_PATTERN.matcher(content);
						if (!ruleMatcher.matches()) throw new ParseException("rule definition syntax doesn't match 'to <rule name> [<id>]' on line " + lineNumber + ":\n" + line, indent.length());
						buf.append("<mixt:rule ");
						if (ruleMatcher.group(2) != null) buf.append("xml:id='" + ruleMatcher.group(3) + "' ");
						buf.append("name='" + ruleMatcher.group(1) + "'>");
						tags.addFirst("rule");
						atRule = true;
					} else if (keyword.equals("alias")) {
						if (indents.size() != 2) throw new ParseException("alias declarations must be nested immediately in a rule, but found one at level " + indents.size() + " on line " + lineNumber + ":\n" + line, indent.length());
						if (!atRule) throw new ParseException("alias declarations must immediately follow a rule declaration, but found a misplaced one on line " + lineNumber + ":\n" + line, indent.length());
						assert content.startsWith("alias");
						if (content.length() >= 7) {
							buf.append("<mixt:alias name='").append(content.substring(6)).append("'>");
							tags.addFirst("alias");
						}
					} else {
						if (indents.size() == 1) throw new ParseException("rule blocks must be nested in a rule, but found one at outermost level on line " + lineNumber + ":\n" + line, indent.length());
						atRule = false;
						buf.append("<mixt:" + keyword);
						tags.addFirst(keyword);
						if (specParts.length % 2 != 1) throw new ParseException("unpaired attribute on line " + lineNumber + ":\n" + line, indent.length());
						for (int i=1; i<specParts.length; i+=2) {
							buf.append(" " + specParts[i] + "='" + escapeXMLChars(specParts[i+1]) + "'");
						}
						buf.append(">");
						parseText = true;
					}
					if (parseText && k != -1) {
						String text = k+1 >= content.length() ? "" : content.substring(k+1).trim();
						if (text.length() == 0) {
							inTextRun = true;
							needIndentedText = true;
						} else {
							buf.append(escapeXMLChars(text));
						}
					}
				}
			}
		}
		if (inPreamble) buf.append(">");
		while (tags.size() > 0) buf.append("</mixt:" + tags.removeFirst() + ">");
		buf.append("</mixt:rules>");
		return Source.xml(buf.toString());
	}
	
	private static String escapeXMLChars(String raw) {
		return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;");
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
			Node result = db.getFolder("/").documents().load(
					Name.create("translationResult"),
					compactToXml(new StringReader(compactText.toString()))).root();
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
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'/>");
			translateAndCheck();
		}
		
		@Test public void namespaceDeclarations() throws IOException, ParseException {
			captureInput();
			_("namespace java " + Namespace.JAVA);
			_("namespace uml " + Namespace.UML);
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "' xmlns:java='" + Namespace.JAVA + "' xmlns:uml='" + Namespace.UML + "'/>");
			translateAndCheck();
		}
		
		@Test public void namespaceDeclarationsAndRule() throws IOException, ParseException {
			captureInput();
			_("namespace java " + Namespace.JAVA);
			_("namespace uml " + Namespace.UML);
			_("rule do stuff [r1]");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "' xmlns:java='" + Namespace.JAVA + "' xmlns:uml='" + Namespace.UML + "'>");
			_("	<mixt:rule xml:id='r1' name='do stuff'/>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void functionDeclaration() throws IOException, ParseException {
			captureInput();
			_("function foo($a1, $a2): x");
			captureOutput();
			_("<mixt:rules xmlns:mixt='" + Engine.MIXT_NS + "'>");
			_("	<mixt:function name='foo' args='$a1, $a2'>x</mixt:function>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void functionDeclarationQName() throws IOException, ParseException {
			captureInput();
			_("function bar:foo($a1, $a2): x");
			captureOutput();
			_("<mixt:rules xmlns:mixt='" + Engine.MIXT_NS + "'>");
			_("	<mixt:function name='bar:foo' args='$a1, $a2'>x</mixt:function>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void functionDeclarationNoArgs() throws IOException, ParseException {
			captureInput();
			_("function foo(): x");
			captureOutput();
			_("<mixt:rules xmlns:mixt='" + Engine.MIXT_NS + "'>");
			_("	<mixt:function name='foo'>x</mixt:function>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void functionDeclarationMultiLine() throws IOException, ParseException {
			captureInput();
			_("function foo ( $a1 )  :");
			_("	/bar/baz");
			_("		[@blah]");
			captureOutput();
			_("<mixt:rules xmlns:mixt='" + Engine.MIXT_NS + "'>");
			_("	<mixt:function name='foo' args='$a1 '>/bar/baz");
			_("	[@blah]");
			_("</mixt:function>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void oneRuleWithoutBlocks() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'/>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void oneRuleWithoutId() throws IOException, ParseException {
			captureInput();
			_("rule do something or other");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule name='do something or other'/>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void twoRulesWithoutBlocks1() throws IOException, ParseException {
			captureInput();
			_("rule do something [r1]");
			_("rule do other [r2]");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something'/>");
			_("	<mixt:rule xml:id='r2' name='do other'/>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void twoRulesWithoutBlocks2() throws IOException, ParseException {
			captureInput();
			_("rule do something [r1]");
			_("");
			_("rule do other [r2]");
			_("");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something'/>");
			_("	<mixt:rule xml:id='r2' name='do other'/>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void oneRuleWithEmptyBlocks() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	for");
			_("	insert");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:for/><mixt:insert/>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}

		@Test public void blockWithAttributes() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	for some $x and $y");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:for some='$x' and='$y'/>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}

		@Test public void blockWithInlineText() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	for: foo bar bar : is < blah");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:for>foo bar bar : is &lt; blah</mixt:for>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}

		@Test public void blockWithInlineElements() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	insert: <bar/>");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:insert>&lt;bar/&gt;</mixt:insert>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}

		@Test public void blockWithAttributesAndInlineText() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	for any $x: foo bar bar : is blah");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:for any='$x'>foo bar bar : is blah</mixt:for>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}

		@Test public void blockWithInlineTextBetweenOtherBlocks() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	with this $x");
			_("	for: foo bar bar : is blah");
			_("	with that $y");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:with this='$x'/>");
			_("		<mixt:for>foo bar bar : is blah</mixt:for>");
			_("		<mixt:with that='$y'/>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}

		@Test public void blockWithIndentedText() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	for:");
			_("		something like: this and");
			_("			this more indented stuff too");
			_("		then back to normal");
			_("	with that $y");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:for>something like: this and");
			_("	this more indented stuff too");
			_("then back to normal");
			_("</mixt:for>");
			_("		<mixt:with that='$y'/>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void nestedBlocks() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	for");
			_("		with that $y");
			_("		with this $x");
			_("	insert");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:for>");
			_("			<mixt:with that='$y'/>");
			_("			<mixt:with this='$x'/>");
			_("		</mixt:for>");
			_("		<mixt:insert/>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void ruleWithAlias() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	alias do that other thing");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:alias name='do that other thing'/>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test public void ruleWithTwoAliasesAndBlocks() throws IOException, ParseException {
			captureInput();
			_("rule do something or other [r1]");
			_("	alias do that other thing");
			_("	alias and that too");
			_("	for");
			_("	insert");
			captureOutput();
			_("<mixt:rules xmlns:mixt='"+ Engine.MIXT_NS + "'>");
			_("	<mixt:rule xml:id='r1' name='do something or other'>");
			_("		<mixt:alias name='do that other thing'/>");
			_("		<mixt:alias name='and that too'/>");
			_("		<mixt:for/><mixt:insert/>");
			_("	</mixt:rule>");
			_("</mixt:rules>");
			translateAndCheck();
		}
		
		@Test(expected = ParseException.class)
		public void namespaceAfterPreamble() throws ParseException, IOException {
			captureInput();
			_("rule do stuff [r1]");
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
			_("rule do stuff [r1]");
			_("	for");
			_("  bar");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badIndent2() throws ParseException, IOException {
			captureInput();
			_("rule do stuff [r1]");
			_("	for:");
			_("	bar");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badIndent3() throws ParseException, IOException {
			captureInput();
			_("rule do stuff [r1]");
			_("	for:");
			_("		line 1");
			_("	  line 2");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badIndent4() throws ParseException, IOException {
			captureInput();
			_("rule do stuff [r1]");
			_("for");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void ruleNotAtTopLevel() throws ParseException, IOException {
			captureInput();
			_("rule do stuff [r1]");
			_("	to do nested [r2]");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void badRuleDeclaration() throws ParseException, IOException {
			captureInput();
			_("rule do stuff: xxx");
			translateBadInput();
		}

		@Test(expected = ParseException.class)
		public void unpairedAttribute() throws ParseException, IOException {
			captureInput();
			_("rule do stuff [r1]");
			_("	for any $x empty");
			translateBadInput();
		}
		
		@Test(expected = ParseException.class)
		public void aliasOutermost() throws ParseException, IOException {
			captureInput();
			_("alias foo bar");
			translateBadInput();
		}
		
		@Test(expected = ParseException.class)
		public void aliasNestedWithoutRule() throws ParseException, IOException {
			captureInput();
			_("	alias foo bar");
			translateBadInput();
		}
		
		@Test(expected = ParseException.class)
		public void aliasAfterBlocks() throws ParseException, IOException {
			captureInput();
			_("rule do stuff [r1]");
			_("	for any $x empty");
			_("	alias foo bar");
			translateBadInput();
		}
		
		@Test(expected = ParseException.class)
		public void functionNested() throws ParseException, IOException {
			captureInput();
			_("  function foo($arg): x");
			translateBadInput();
		}
		
		@Test(expected = ParseException.class)
		public void functionWithoutText() throws ParseException, IOException {
			captureInput();
			_("  function foo($arg)");
			translateBadInput();
		}
		
		@Test(expected = ParseException.class)
		public void functionMalformed() throws ParseException, IOException {
			captureInput();
			_("  function foo($arg,): x");
			translateBadInput();
		}
		
	}
}
