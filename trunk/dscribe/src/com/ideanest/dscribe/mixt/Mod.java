package com.ideanest.dscribe.mixt;

import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;

public class Mod {
	
	static final Logger LOG = Logger.getLogger(Mod.class);
	static final NamespaceMap MOD_NAMESPACE = new NamespaceMap("", Namespace.MOD);
	static final NamespaceMap EMPTY_NAMESPACES = new NamespaceMap();
	
	final Rule rule;
	final int stage;
	final Mod parent;
	
	private boolean restored;
	private Set<String> boundVariables;
	private List<Node> references;
	private Seg seg;
	private Node data;

	Mod(Rule rule) {
		this.rule = rule;
		this.stage = -1;
		this.parent = null;
		this.restored = true;
	}
	
	Mod(Mod parent) {
		this.rule = parent.rule;
		this.stage = parent.stage+1;
		this.parent = parent;
		this.restored = false;
	}
	
	Mod binder(String varName) {
		if (boundVariables != null && boundVariables.contains(varName)) return this;
		return parent.binder(varName);
	}
	
	Map<String,Object> variableBindings() {
		return parent.variableBindings();
	}

	public QueryService globalScope() {
		return scope(rule.engine.globalScope);
	}
	
	/**
	 * Take the given query service (or the workspace query service if the given one is <code>null</code>)
	 * and bind all the variables defined by previous blocks the mod chain.  Return a query service ready
	 * to run verification queries on.
	 *
	 * @param qs the query service to use as a base, or <code>null</code> to use the workspace query service
	 * @return a clone of the given query service with variables from previous blocks bound and namespace map cleared
	 */
	public QueryService scope(QueryService qs) {
		if (qs == null) qs = workspace().database().query();
		return parent.prepScopeClone(qs);
	}
	
	QueryService prepScopeClone(QueryService qs) {
		return qs.clone(new NamespaceMap(), Collections.unmodifiableMap(variableBindings()));
	}
	
	public void bindVariable(String name, Object value) throws TransformException {
		Map<String,Object> variableBindings = variableBindings();
		if (variableBindings.containsKey(name)) throw new TransformException("cannot rebind variable " + name);
		variableBindings.put(name, value);
		boundVariables.add(name);
	}
	
	public String key() {
		return parent.key();
	}
	
	public Node data() {
		return data;
	}
	
	public Folder workspace() {
		return rule.engine.workspace.cloneWithoutNamespaceBindings();
	}
	
	public List<Node> references() {
		if (references == null) return Collections.emptyList();
		return Collections.unmodifiableList(references);
	}
	
	public <T> T nearestAncestorImplementing(Class<T> clazz) throws TransformException {
		if (clazz.isInstance(seg)) return clazz.cast(seg);
		return parent.nearestAncestorImplementing(clazz);
	}
	
	Collection<Mod> resolveChildren(Block block, boolean lastBlock, QueryService touchedScope) throws TransformException {
		QueryService scope = prepScopeClone((touchedScope == null || !restored) ? rule.engine.globalScope : touchedScope);
		
		Builder modBuilder;
		if ((block instanceof KeyBlock)) {
			modBuilder = new KeyMod.Builder(this, block, lastBlock, scope);
			((KeyBlock) block).resolve((KeyMod.Builder) modBuilder);
		} else {
			modBuilder = new Mod.Builder(this, block, lastBlock, scope);
			((LinearBlock) block).resolve(modBuilder);
		}

		return modBuilder.children;
	}
	
	Mod deriveChild(Block block, String key) {
		Mod mod = block instanceof KeyBlock ? new KeyMod(this, key) : new Mod(this);
		mod.seg = block.createSeg(mod);
		return mod;
	}
	
	Mod restoreChild(Block block, Node blockData) throws TransformException {
		Mod mod = deriveChild(block, blockData.query().single("../@xml:id").value());
		mod.data = blockData;
		mod.restore();
		return mod;
	}
	
	void restore() throws TransformException {
		restored = true;
		final int storedStage = data.query().single("@stage").intValue();
		if (this.stage != storedStage)
			throw new IllegalArgumentException("stage mismatch on node restore, given " + stage + ", stored " + storedStage);
		
		ItemList refNodes = data.query().unordered("references");
		if (refNodes.size() > 0) {
			references = new ArrayList<Node>(refNodes.size());
			for (Node refNode : refNodes.nodes()) {
				Node node = rule.engine.globalScope.single("/id($_1)", refNode.query().single("@refid").value()).node();
				final String actualPath = rule.engine.relativePath(node.document());
				final String storedPath = refNode.query().single("@doc").value();
				if (!actualPath.equals(storedPath))
					throw new TransformException("doc mismatch on reference, resolves to '" + actualPath + "' but stored as '" + storedPath + "'");
				references.add(node);
			}
		}
		
		data.namespaceBindings().replaceWith(EMPTY_NAMESPACES);
		seg.restore();
	}
	
	void verify() throws TransformException {
		LOG.debug("verifying " + this);
		seg.verify();
	}
	
	void analyze() throws TransformException {
		LOG.debug("analyzing " + this);
		seg.analyze();
	}
	
	void writeAncestors(ElementBuilder<?> builder) {
		parent.writeAncestors(builder);
	}
	
	@Override public String toString() {
		return rule + ".mod[" + stage + ":" + key() + "]";
	}
	
	
	static Mod bootstrap(Rule rule) {
		return new KeyMod(rule) {
			@Override void writeAncestors(ElementBuilder<?> builder) {}
			@Override public String toString() {return "rootmod[" +rule + "]";} 
			@Override Mod restoreChild(Block block, Node data) throws TransformException {
				throw new UnsupportedOperationException();
			}
			@Override public <T> T nearestAncestorImplementing(Class<T> clazz) throws TransformException {
				throw new TransformException("no ancestor found that implements " + clazz);
			}
		};
	}
	
	
	public static class Builder {
		
		final Mod parent;
		private final Block block;
		private final QueryService scope;
		private final boolean lastBlock;
		
		private Set<String>dependentDocNames, unverifiedDocNames, affectedNodeIds;
		private List<Node> references;
		private ElementBuilder<org.w3c.dom.Node> supplement;
		
		private List<Mod> children = new ArrayList<Mod>(1);
		
		Builder(Mod parent, Block block, boolean lastBlock, QueryService scope) {
			this.parent = parent;
			this.block = block;
			this.lastBlock = lastBlock;
			this.scope = scope;
			reset();
		}

		void reset() {
			dependentDocNames = new TreeSet<String>();
			unverifiedDocNames = new TreeSet<String>();
			affectedNodeIds = new TreeSet<String>();
			references = new ArrayList<Node>(1);
			supplement = null;
		}
		
		public Mod parent() {return parent;}
		
		public void commit() throws TransformException {
			try {
				checkChildrenSize();
				Mod mod = createChild();
				mod.references = references;
				
				Node modNode = mod.rule.engine.modStore.query().optional("id($_1)/self::mod", mod.key()).node();
				if (modNode.extant()) {
					final int oldStage = modNode.query().single("@stage").intValue();
					if (oldStage > mod.stage) return;
					if (oldStage == mod.stage) {
						mod.restored = true;
						mod.data = modNode.query().single("block[xs:integer(@stage)=$_1]", mod.stage).node();
					}
				} else {
					ElementBuilder<Node> modDataBuilder = mod.rule.engine.modStore.append()
						.elem("mod").attr("xml:id", mod.key()).attr("rule", mod.rule.id).end("mod");
					parent.writeAncestors(modDataBuilder);
					modNode = modDataBuilder.commit();
				}
				if (!mod.restored) mod.data = writeData(mod, modNode);
				
				mod.seg = block.createSeg(mod);
				mod.seg.restore();
				children.add(mod);
			} finally {
				reset();
			}
		}

		private Node writeData(Mod mod, Node modNode) {
			ElementBuilder<Node> dataBuilder = modNode.append();
			dataBuilder.elem("block").attr("stage", mod.stage);
			for (String docName : dependentDocNames) {
				dataBuilder.elem("dependency")
					.attr("kind", unverifiedDocNames.contains(docName) ? "unverified" : "verified")
					.attr("doc", docName)
				.end("dependency");
			}
			for (String nodeId : affectedNodeIds) dataBuilder.elem("affected").attr("refid", nodeId).end("affected");
			for (Node node : references)
				dataBuilder.elem("reference")
					.attr("refid", node.query().single("@xml:id").value())
					.attr("doc", parent.rule.engine.relativePath(node.document()))
				.end("reference");
			if (supplement != null) dataBuilder.node(supplement.commit());
			Node data = dataBuilder.end("block").commit();
			modNode.update().attr("stage", mod.stage).commit();
			return data;
		}
		
		void checkChildrenSize() throws TransformException {
			if (!children.isEmpty()) throw new TransformException("linear block should resolve to at most one mod");
		}
		
		Mod createChild() {
			return new Mod(parent);
		}
		
		public QueryService scope() {
			return scope;
		}
		
		public ElementBuilder<?> supplement() {
			if (supplement == null) supplement = ElementBuilder.createScratch(new NamespaceMap());
			return supplement;
		}
		
		public DependencyModifier dependOn(Document doc) {
			return dependOn(doc, new DependencyModifier());
		}
		
		private DependencyModifier dependOn(Document doc, DependencyModifier depMod) {
			depMod.add(parent.rule.engine.relativePath(doc));
			return depMod;
		}
		
		public DependencyModifier dependOn(Mod ancestor) {
			return dependOn(ancestor, new DependencyModifier());
		}
		
		private DependencyModifier dependOn(Mod ancestor, DependencyModifier depMod) {
			Map<String,Object> varMap = Collections.emptyMap();
			depMod.addAll(ancestor.data().query().clone(MOD_NAMESPACE, varMap)
					.unordered("dependency[@kind='unverified']/@doc").values().asList());
			return depMod;
		}
		
		public DependencyModifier dependOn(Collection<String> variables) {
			DependencyModifier depMod = new DependencyModifier();
			for (String varName : variables) {
				Document doc = null;
				try {
					Object value = parent.variableBindings().get(varName);
					if (value instanceof Node) doc = ((Node) value).document();
				} catch (DatabaseException e) {
					// node may be in-memory, in which case there's no doc to depend on -- that's fine
				}
				if (doc != null) dependOn(doc, depMod);
				dependOn(parent.binder(varName), depMod);
			}
			return depMod;
		}
		
		public void affect(Node node) {
			if (affectedNodeIds == null) affectedNodeIds = new TreeSet<String>();
			affectedNodeIds.add(node.query().single("@xml:id").value());
		}
		
		public void reference(Node node) {
			if (!node.query().exists("@xml:id")) throw new IllegalArgumentException("referenced node doesn't have an xml:id: " + node);
			if (references == null) references = new ArrayList<Node>();
			references.add(node);
			dependOn(node.document());
		}
		
		public String generateId(int serial) {
			return parent.key() + (parent.stage+1) + (serial >= 0 ? "-" + serial : "") + ".";
		}
		
		public class DependencyModifier {
			private final Set<String> docNames = new TreeSet<String>();
			
			void add(String docName) {
				docNames.add(docName);
				dependentDocNames.add(docName);
			}
			
			void addAll(Collection<String> docNames_) {
				docNames.addAll(docNames_);
				dependentDocNames.addAll(docNames_);
			}
			
			public void unverified() throws TransformException {
				if (lastBlock) throw new TransformException("cannot have unverified dependencies in a rule's last block");
				unverifiedDocNames.addAll(docNames);
			}
		}

	}

}