package com.ideanest.dscribe.mixt.blocks;

import java.util.Collection;

import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;

public class CreateDoc implements BlockType {

	public QName xmlName() {
		return new QName(Namespace.RULES, "create-doc", null);
	}

	public Block define(Node def) throws RuleBaseException {
		return new CreateDocBlock(def);
	}
	
	private static class CreateDocBlock implements LinearBlock {
		private final Query.Text query;
		private Collection<String> requiredVariables;
		
		CreateDocBlock(Node def) {
			query = def.query().exists("node()") ? new Query.Text(def) : null;
		}
		
		public void resolve(Mod.Builder modBuilder) throws TransformException {
			modBuilder.supplement().elem("docname")
				.text(resolveName(modBuilder.parent(), modBuilder.scope()))
			.end("docname");
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}

		private String resolveName(Mod keyMod, QueryService scope) throws TransformException {
			String name = (query == null) ? keyMod.key() + ".xml" : query.runOn(scope);
			if (name == null || name.length() == 0)
				throw new TransformException("create-doc failed to resolve document name");
			if (name.charAt(0) == '/') throw new TransformException("create-doc document name cannot begin with a slash");
			if (name.charAt(name.length()-1) == '/') throw new TransformException("create-doc document name cannot end with a slash");
			return name;
		}
		
		public Seg createSeg(Mod mod) {return new CreateDocSeg(mod);}
		
		private class CreateDocSeg extends Seg implements InsertionTarget {
			private String name;
			
			CreateDocSeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				name = mod.data().query().single("docname").value();
			}
			
			@Override public void analyze() {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
			}
			
			@Override public void verify() throws TransformException {
				String resolvedName = resolveName(mod, mod.scope(null));
				if (!name.equals(resolvedName))
					throw new TransformException("stored document name '" + name + "' doesn't match recalculated document name '" + resolvedName + "'");
			}
			
			public ElementBuilder<?> contentBuilder() throws TransformException {
				String docName = name;
				Folder folder;
				int k = docName.lastIndexOf('/');
				if (k == -1) {
					folder = mod.workspace();
				} else {
					folder = mod.workspace().children().create(docName.substring(0, k));
					docName = docName.substring(k+1);
				}
				return folder.documents().build(Name.adjust(docName));
			}
		}
		
	}

}
