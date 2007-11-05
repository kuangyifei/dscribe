package com.ideanest.dscribe.mixt.blocks;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;

public class Insert implements BlockType {

	public QName xmlName() {
		return new QName(Namespace.RULES, "insert", null);
	}
	public Block define(Node def) throws RuleBaseException {
		return new InsertBlock(def);
	}

	private static class InsertBlock implements LinearBlock {
		private static final String DIGEST_TYPE = "MD5";
		
		private final Query.Items query;
		private Collection<String> requiredVariables;
		
		private InsertBlock(Node def) throws RuleBaseException {
			query = new Query.Items(def);
		}

		public void resolve(Mod.Builder modBuilder) throws TransformException {
			ItemList nodesToInsert = query.runOn(modBuilder.scope());
			if (nodesToInsert.size() > 0) {
				try {
					modBuilder.supplement()
							.elem("checksum").attr("digestType", DIGEST_TYPE)
							.text(calculateDigest(DIGEST_TYPE, nodesToInsert))
							.end("checksum");
				} catch (NoSuchAlgorithmException e) {
					throw new TransformException("missing digest algorithm", e);
				}
				int serial = nodesToInsert.size() == 1 ? -1 : 1;
				for (Node node : nodesToInsert.nodes()) {
					String id = modBuilder.generateId(serial++);
					node.update().attr("xml:id", id).commit();
				}
				modBuilder.parent().nearestAncestorImplementing(InsertionTarget.class)
						.contentBuilder().nodes(nodesToInsert.nodes()).commit();
				for (String id : nodesToInsert.query().unordered("@xml:id").values()) {
					modBuilder.affect(modBuilder.parent().globalScope().single("id($_1)", id).node());
				}
			}
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}

		private String calculateDigest(String digestType, ItemList nodesToInsert) throws NoSuchAlgorithmException {
			try {
				return DataUtils.toXMLString(MessageDigest.getInstance(digestType)
						.digest(nodesToInsert.toString().getBytes("UTF-8")));
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("missing character encoding", e);
			}
		}

		public Seg createSeg(Mod mod) {
			return new InsertSeg(mod);
		}
		
		private class InsertSeg extends Seg {
			private String digestType;
			private String checksum;
			
			InsertSeg(Mod mod) {super(mod);}
			
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
			}
			
			@Override public void restore() throws TransformException {
				Node checksumNode = mod.data().query().optional("checksum").node();
				digestType = checksumNode.query().optional("@digestType").value();
				checksum = checksumNode.value();
			}
			
			@Override public void verify() throws TransformException {
				ItemList nodesToInsert = query.runOn(mod.scope(null));
				try {
					if (!(checksum == null ? nodesToInsert.size() == 0 : checksum.equals(calculateDigest(digestType, nodesToInsert)))) {
						throw new TransformException("inserted node checksum mismatch");
					}
				} catch (NoSuchAlgorithmException e) {
					throw new TransformException("missing old digest algorithm, assuming mismatch", e);
				}
			}
		}
	}

}
