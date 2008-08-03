var s = XPath.Semantics;

lz.node.prototype.recordXmlId = function(newValue) {
	if (!newValue) return;
	var node = this;
	while (node != null && node != node.immediateparent) {
		if ("trackXmlId" in node) {
			node.trackXmlId(this, newValue);
			return;
		}
		node = node.immediateparent;
	}
	console.error("internal xpath error: no xml:id tracker found for " + this);
};

function() {
	var oldConstruct = lz.node.prototype.construct;
	lz.node.prototype.construct = function(parent, args) {
		oldConstruct.call(this, parent, args);
		if ("xmlid" in args) {
			this.applyConstraintMethod("recordXmlId", [this, "xmlid"]);
			this.recordXmlId(args.xmlid);
		}
	};
}();

lz.node.prototype.xnode = true;

lz.node.prototype.xname = function() {return this.constructor.tagname;}

lz.node.prototype.xparent = function() {
	return this.parent === this ? null : this.parent;
};

lz.node.prototype.xchildren = function() {
	var node = this;
	var prevNode;
	do {
		prevNode = node;
		if (node.defaultplacement) node = node.determinePlacement(null, node.defaultplacement);
	} while (prevNode != node);
	var textChildren = "text" in node ? [new s.TextNode(node)] : [];
	this.xchildren = function() {
		if (!node.subnodes) return textChildren;
		if (node.layouts && node.layouts.length) {
			return node.subnodes.select(function(child) {return !node.layouts.find(child);});
		} else {
			return node.subnodes;
		}
	};
	return this.xchildren();
};

lz.node.prototype.xattributes = function() {
	var results = [];
	for (var key in this) {
		if (key == "text" || key.charAt(0) == "_"  || key.indexOf("$") != -1) continue;
		var t = typeof this[key];
		if (!(t == "null" || t == "boolean" || t == "string" || t == "number")) continue;
		results.push(new s.AttributeNode(this, key));
	}
	results.sort(s.AttributeNode.cmp);
	this.xattributes = function() {return results;};
	return results;
};

lz.node.prototype.xattribute = function(name) {
	if (!name || name == "text" || name.charAt(0) == "_"  || name.indexOf("$") != -1) return null;
	if (!(name in this)) return null;
	var t = typeof this[name];
	if (!(t == "null" || t == "boolean" || t == "string" || t == "number")) return null;
	return new s.AttributeNode(this, name);
}

s.DocumentNode = function(root) {this.root = root;};
s.DocumentNode.prototype.xnode = true;
s.DocumentNode.prototype.xname = function() {return null;};
s.DocumentNode.prototype.xparent = function() {return null;};
s.DocumentNode.prototype.xchildren = function() {return [this.root];};
s.DocumentNode.prototype.toString = function() {return "document(" + this.root + ")";};

s.TextNode = function(node) {this.node = node;};
s.TextNode.prototype.xnode = true;
s.TextNode.prototype.xname = function() {return null;};
s.TextNode.prototype.xparent = function() {return this.node;};
s.TextNode.prototype.xchildren = function() {return [];};
s.TextNode.prototype.atomized = function() {return this.node.text;};
s.TextNode.prototype.toString = function() {return '"' + this.node.text + '"';};

s.AttributeNode = function(node, key) {this.node = node; this.key = key;};
s.AttributeNode.prototype.equals = function(that) {
	return that instanceof s.AttributeNode && this.node === that.node && this.key === that.key;
};
s.AttributeNode.prototype.xnode = true;
s.AttributeNode.prototype.xname = function() {return this.key;};
s.AttributeNode.prototype.xparent = function() {return this.node;};
s.AttributeNode.prototype.xchildren = function() {return [];};
s.AttributeNode.prototype.atomized = function() {return this.node[this.key];}
s.AttributeNode.prototype.toString = function() {return "@" + this.key + "=" + this.node[this.key];};
s.AttributeNode.cmp = function(a, b) {
	var ka = a.key, kb = b.key;
	return ka === kb ? 0 : (ka < kb ? -1 : 1);
};

Number.prototype.eval = function() {return [this];};
String.prototype.eval = function() {return [this];}

Array.prototype.atomized = function() {return this.map(function(item) {return item.atomized();});};
lz.node.prototype.atomized = function() {return this.text;};
Number.prototype.atomized = function() {return this;};
String.prototype.atomized = function() {return this.toString();};

Array.prototype.effectiveBooleanValue = function() {
	switch(this.length) {
		case 0:
			return false;
		case 1:
			var x = this[0];
			var t = typeof x;
			if (t == "boolean") return x;
			if (t == "string") return x.length > 0;
			if (t == "number") return !(x == 0 || x == Number.NaN);
		default:
			if (this[0].xnode) return true;
	}
	console.error("[FORG0006] expression has no effective boolean value: [" + this + "]");
};

Array.prototype.numberValue = function() {
	if (this.length == 1) return this[0].numberValue();
	console.error("[FORG0001] not a number: [" + this + "]");
};
String.prototype.numberValue = function() {
	var r = Number(x);
	if (r != Number.NaN || x == "NaN") return r;
};
Number.prototype.numberValue = function() {return this;};
lz.node.prototype.numberValue = function() {};

Number.prototype.isInteger = function() {
	return this === parseInt(this);
};

s.equatable = function(v) {
	if (typeof v == "number" || v instanceof Number || typeof v == "string" || v instanceof String || v.equals) return true;
	console.error("[XPTY0004] operand " + v + " of type " + typeof v + " cannot be equated");
	return false;
};

s.orderable = function(v) {
	if (typeof v == "number" || v instanceof Number || typeof v == "string" || v instanceof String || v.lessThan) return true;
	console.error("[XPTY0004] operand " + v + " of type " + typeof v + " cannot be compared");
	return false;
};

s.trace = function(node) {
	var trace = [];
	while(node) {
		trace.push(node);
		node = node.xparent();
	}
	trace.reverse();
	return trace;
};

s.nodeCombine = function(nodesList, combiner, env) {
	var traceList = nodesList.map(function(nodes) {
		return nodes.map(function(node) {return s.trace(node);});
	});
	var nodes = [];
	s.nodeSortHelper(traceList, combiner, 0, env.docs, nodes);
	return nodes;
};

s.nodeSortHelper = function(traceList, combiner, level, branches, result) {
	// Special short-circuiting case:  if only one node list, and only one node left,
	// push it directly into the result.  This assumes that a single-list sort will
	// always use the identity combiner, but saves time since we don't explore
	// the remainder of the subtree once there's only one node left.
	if (traceList.length == 1 && traceList[0].length == 1) {
		result.push(traceList[0][0].last());
		return;
	}
	// Pluck out any attributes that belong to the parent, which was inserted just
	// before the recursive call to nodeSortHelper.
	if (s.attributeSortHelper(traceList, combiner, level, result)) return;
	// Traverse subtrees in order.
	branches.forEach(function(branch) {
		var branchNodePresent = [], exploreSubtree = false;
		var subtraceList = traceList.map(function(traces, tracesIndex) {
			var subtraces = [];
			for (var i = 0; i < traces.length; i++) {
				var trace = traces[i];
				if (trace === null) continue;
				if (trace[level] === branch) {
					if (trace.length == level + 1) {
						branchNodePresent[tracesIndex] = true;
					} else {
						exploreSubtree = true;
						subtraces.push(trace);
					}
					traces[i] = null;
				}
			}
			return subtraces;
		});
		if (combiner(branchNodePresent)) result.push(branch);
		if (exploreSubtree) s.nodeSortHelper(subtraceList, combiner, level + 1, branch.xchildren(), result);
	});
	if (!traceList.every(function(traces) {return traces.every(function(node) {return node === null;});})) {
		console.error("internal xpath error:  leftover traces in node sort traceList=[" + traceList + "], level=" + level + ", branches=" + branches + ", result=" + result);
		return;
	}
};

s.attributeSortHelper = function(traceList, combiner, level, result) {
	var allNodesWereAttributes = true;
	var masterAttributes = [];
	var attributeList = traceList.map(function(traces) {
		var attributes = [];
		for (var i = 0; i < traces.length; i++) {
			var trace = traces[i];
			if (trace.length == level + 1 && trace.last() instanceof s.AttributeNode) {
				attributes.push(trace.last());
				traces[i] = null;
			} else {
				allNodesWereAttributes = false;
			}
		}
		masterAttributes.append(attributes);
		return attributes;
	});
	if (masterAttributes.length) {
		masterAttributes.sort(s.AttributeNode.cmp);
		attributeList.forEach(function(attributes) {attributes.sort(s.AttributeNode.cmp);});
		var lastAttr = null;
		masterAttributes.forEach(function(attr) {
			if (!attr.equals(lastAttr)) {
				lastAttr = attr;
				var attrPresent = attributeList.map(function(attributes) {
					var len = attributes.length;
					while(attributes.length && attr.equals(attributes[0])) attributes.shift();
					return len != attributes.length;
				});
				if (combiner(attrPresent)) result.push(attr);
			}
		});
	}
	return allNodesWereAttributes;
}
