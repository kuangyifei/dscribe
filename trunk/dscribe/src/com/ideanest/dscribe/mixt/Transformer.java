package com.ideanest.dscribe.mixt;

import static org.junit.Assert.*;

import java.io.*;
import java.text.ParseException;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.junit.*;

public class Transformer {
	public static final String RULES_NS = "http://ideanest.com/dscribe/ns/rules";
	public static final String MOD_NS = "http://ideanest.com/dscribe/ns/mod";
	public static final String RECORD_NS = "http://ideanest.com/dscribe/ns/record";
	
	private static final Logger LOG = Logger.getLogger(Transformer.class);

	private static String recordsRootPath = "/mixt-records";
	
	public static String recordsRootPath() {return recordsRootPath;}
	public static void setRecordsRootPath(String path) {recordsRootPath = path;}

	private final Folder workspace, rulespace;
	private Folder recordspace;
	
	public Transformer(Folder workspace, Folder ruleRepository) {
		Folder recordsRoot = workspace.database().createFolder(recordsRootPath);
		if (workspace.contains(recordsRoot)) throw new IllegalArgumentException("workspace must not contain records root");
		this.workspace = workspace.cloneWithoutNamespaceBindings();
		this.rulespace = ruleRepository.cloneWithoutNamespaceBindings();
		this.rulespace.namespaceBindings().put("", RULES_NS);
		createRecordspace();
	}
	
	// Constructor for testing only.
	private Transformer(Folder workspace, Folder rulespace, Folder recordspace) {
		this.workspace = workspace;
		this.rulespace = rulespace;
		this.recordspace = recordspace;
	}
	
	private void createRecordspace() {
		recordspace = workspace.database().createFolder(recordsRootPath + workspace.path());
		recordspace.namespaceBindings().put("", RECORD_NS);
		recordspace.namespaceBindings().put("record", RECORD_NS);
		recordspace.namespaceBindings().put("mod", MOD_NS);
	}
	
	public Engine.Stats executeOnce() throws RuleBaseException, TransformException, InterruptedException {
		Collection<XMLDocument> modifiedDocs;
		Date lastRunDate = recordspace.query().optional("/last-run/@date").instantValue();
		if (lastRunDate == null || !Engine.isSameVersionAs(recordspace)) {
			// incremental processing corrupted, wipe and run global transform
			wipeRecords();
			modifiedDocs = null;
		} else {
			modifiedDocs = findXmlDocsModifiedSince(lastRunDate);
		}
		
		final Engine engine = new Engine(rulespace, recordspace.cloneWithoutNamespaceBindings(), workspace, initModStore());
		engine.autoGenerateIdsWithPrefix("mixt");
		lastRunDate = engine.executeTransform(modifiedDocs);
		
		recordRun(lastRunDate);
		return engine.stats();
	}
	
	private Collection<XMLDocument> findXmlDocsModifiedSince(Date lastRunDate) {
		Collection<XMLDocument> modifiedDocs = new ArrayList<XMLDocument>();
		for (Document doc : workspace.query().unordered("/*").nodes().documents()) {
			if (lastRunDate.compareTo(doc.metadata().lastModificationDate()) <= 0) modifiedDocs.add(doc.xml());
		}
		return modifiedDocs;
	}
	
	private void wipeRecords() {
		LOG.info("must run global transformation, wiping records");
		recordspace.query().unordered("$_1/id($_2//mod:affected/@refid)", workspace, recordspace).deleteAllNodes();
		recordspace.delete();
		createRecordspace();
	}
	
	public void loadCompactRules(File ruleDefinitionsFile) throws IOException, ParseException {
		Reader fileReader = new FileReader(ruleDefinitionsFile);
		try {
			rulespace.documents().load(Name.overwrite(ruleDefinitionsFile.getName()), CompactFormTranslator.compactToXml(fileReader));
		} finally {
			fileReader.close();
		}
	}
	
	private Node initModStore() {
		Node modStore = recordspace.query().optional("/mod:modstore").node();
		if (!modStore.extant()) {
			modStore = recordspace.documents().load(Name.overwrite("mods"), Source.xml("<modstore xmlns='" + MOD_NS + "'/>")).root();
		}
		modStore.namespaceBindings().sever();
		modStore.namespaceBindings().clear();
		modStore.namespaceBindings().put("", MOD_NS);
		return modStore;
	}
	
	private void recordRun(Date lastRunDate) {
		ElementBuilder<XMLDocument> builder = recordspace.documents().build(Name.overwrite("last-run"));
		builder.elem("last-run").attr("date", lastRunDate);
		Engine.recordVersions(builder);
		builder.end("last-run").commit();

		for (Document doc : recordspace.query().unordered("//rule").nodes().documents()) {
			doc.delete();
		}
		for (Document doc : rulespace.query().unordered("//rule").nodes().documents()) {
			doc.copy(recordspace, Name.keepOverwrite())
					.query().namespace("record", RECORD_NS).unordered("//record:* union //@record:*").deleteAllNodes();
		}
	}
	

	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml")
	public static class _Test extends DatabaseTestCase {
		private Folder workspace, rulespace, recordspace;
		private Transformer transformer;
		
		@Before public void setupTransformer() {
			workspace = db.createFolder("/workspace");
			rulespace = db.createFolder("/rulespace");
			rulespace.namespaceBindings().put("", RULES_NS);
			recordspace = db.createFolder(Transformer.recordsRootPath() + workspace.path());
			recordspace.namespaceBindings().put("", RECORD_NS);
			recordspace.namespaceBindings().put("record", RECORD_NS);
			recordspace.namespaceBindings().put("mod", MOD_NS);
			transformer = new Transformer(workspace, rulespace, recordspace);
		}
		
		@Test public void create() {
			transformer = new Transformer(workspace, rulespace);
			assertEquals(workspace, transformer.workspace);
			assertEquals(rulespace, transformer.rulespace);
			assertEquals(rulespace.namespaceBindings(), transformer.rulespace.namespaceBindings());
			assertEquals(recordspace, transformer.recordspace);
			assertEquals(recordspace.namespaceBindings(), transformer.recordspace.namespaceBindings());
		}
		
		@Test(expected = IllegalArgumentException.class)
		public void createWithBadWorkspace() {
			new Transformer(db.getFolder("/"), rulespace);
		}
		
		@Test public void loadCompactRules() throws IOException, ParseException {
			File compactRules = File.createTempFile("mixt", "mxc");
			compactRules.deleteOnExit();
			FileWriter out = new FileWriter(compactRules);
			out.append(
					"rule do something or other [r1]\n" +
					"	for any $x: foo bar bar : is blah\n");
			out.close();
			transformer.loadCompactRules(compactRules);
			XMLDocument doc = rulespace.documents().get(compactRules.getName()).xml();
			assertTrue(doc.query().exists("//rule[@xml:id='r1']"));
		}
		
		@Test public void createRecordspace() {
			transformer.recordspace = null;
			transformer.createRecordspace();
			assertEquals(recordspace, transformer.recordspace);
			assertEquals(recordspace.namespaceBindings(), transformer.recordspace.namespaceBindings());
		}
		
		@Test public void initModStoreFresh() {
			Node modsRoot = transformer.initModStore();
			assertEquals(MOD_NS, modsRoot.namespaceBindings().get(""));
			assertTrue(modsRoot.query().optional("self::modstore").extant());
			assertEquals(recordspace.query().optional("/mod:modstore").node(), modsRoot);
		}

		@Test public void initModStoreExisting() {
			recordspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + MOD_NS + "'><mod xml:id='_foo.'/></modstore>"));
			Node modsRoot = transformer.initModStore();
			assertEquals(MOD_NS, modsRoot.namespaceBindings().get(""));
			assertTrue(modsRoot.query().optional("self::modstore").extant());
			assertEquals(recordspace.query().optional("/mod:modstore").node(), modsRoot);
			assertTrue(modsRoot.query().optional("mod").extant());
		}
		
		@Test public void recordRun() {
			Date date = new Date();
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns='" + RULES_NS + "'>" +
					"  <rule xml:id='r1'/>" +
					"  <foo xmlns='" + RECORD_NS + "'/>" +
					"</rules>"));
			rulespace.children().create("subrules").documents().load(Name.generate(), Source.xml(
					"<rules xmlns='" + RULES_NS + "' xmlns:rec='" + RECORD_NS + "'>" +
					"  <rule xml:id='r2' rec:bar='x'/>" +
					"</rules>"));
			transformer.recordRun(date);
			
			Node lastRun = recordspace.query().single("/last-run").node();
			assertEquals(date, lastRun.query().single("@date").instantValue());
			assertTrue(lastRun.query().exists("//engine/@version"));
			assertTrue(lastRun.query().all("//block-type").size() >= 4);
			assertTrue(lastRun.query().single("every $c in block-type satisfies $c[@class][@version]").booleanValue());
			
			assertEquals(2, recordspace.query().namespace("", RULES_NS).unordered("//rule").size());
			assertTrue(recordspace.query().namespace("", RULES_NS).exists("//rule[@xml:id='r1']"));
			assertTrue(recordspace.query().single("in-scope-prefixes($_1/id('r1')) = in-scope-prefixes($_2/id('r1'))", rulespace, recordspace).booleanValue());
			assertFalse(recordspace.query().namespace("", RECORD_NS).exists("//foo"));
			assertTrue(recordspace.query().namespace("", RULES_NS).exists("//rule[@xml:id='r2']"));
			assertTrue(recordspace.query().single("in-scope-prefixes($_1/id('r2')) = in-scope-prefixes($_2/id('r2'))", rulespace, recordspace).booleanValue());
			assertFalse(recordspace.query().namespace("r", RECORD_NS).exists("/id('r2')/@r:bar"));
		}
		
		@Test public void findDocsModifiedSince() throws InterruptedException {
			workspace.documents().load(Name.generate(), Source.xml("<foo/>"));
			workspace.children().create("nested").documents().load(Name.generate(), Source.xml("<foo/>"));
			Thread.sleep(50);
			Date date = new Date();
			Set<Document> newDocs = new HashSet<Document>();
			newDocs.add(workspace.documents().load(Name.generate(), Source.xml("<foo/>")));
			newDocs.add(workspace.children().get("nested").documents().load(Name.generate(), Source.xml("<foo/>")));
			Collection<XMLDocument> modifiedDocs = transformer.findXmlDocsModifiedSince(date);
			assertEquals(newDocs, new HashSet<Document>(modifiedDocs));
		}
		
		@Test public void wipeRecords() {
			workspace.documents().load(Name.generate(), Source.xml("<foo xml:id='ok1'><bar xml:id='ok2'/></foo>"));
			workspace.documents().load(Name.generate(), Source.xml("<foo xml:id='ok3'><bar xml:id='bad1'>hello</bar></foo>"));
			workspace.documents().load(Name.create("dead1"), Source.xml("<foo xml:id='bad2'/>"));
			workspace.documents().load(Name.generate(), Source.xml("<foo xml:id='ok4'><bar xml:id='bad3'><baz xml:id='bad4'/></bar></foo>"));
			workspace.documents().load(Name.create("dead2"), Source.xml("<foo xml:id='bad5'><bar xml:id='bad6'/></foo>"));
			workspace.children().create("nested").documents().load(Name.generate(), Source.xml("<foo xml:id='ok5'><bar xml:id='bad7'/></foo>"));
			recordspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'><mods>" +
					"  <mod><affected refid='bad1'/><affected refid='bad2'/></mod>" +
					"  <mod><affected refid='bad3'/><affected refid='bad4'/></mod>" +
					"  <mod><affected refid='bad5'/><affected refid='bad6'/><affected refid='bad7'/></mod>" +
					"</mods></modstore>"));
			transformer.wipeRecords();
			for (int i=1; i<=5; i++) assertTrue(workspace.query().presub().exists("/id('ok$1')", Integer.toString(i)));
			for (int i=1; i<=7; i++) assertFalse(workspace.query().presub().exists("/id('bad$1')", Integer.toString(i)));
			assertFalse(workspace.documents().contains("dead1"));
			assertFalse(workspace.documents().contains("dead2"));
		}
	}
	
}
