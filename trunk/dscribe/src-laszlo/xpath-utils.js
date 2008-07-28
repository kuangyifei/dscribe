var s = XPath.Semantics;

lz.node.prototype.xnode = true;

lz.node.prototype.xname = function() {return this.constructor.tagname;}

lz.node.prototype.xparent = function(env) {
	var k = env ? env.roots.indexOf(this) : -1;
	return k == -1 ? this.parent : env.docs[k];
};

lz.node.prototype.xchildren = function() {
	var node = this;
	var prevNode;
	do {
		prevNode = node;
		if (node.defaultplacement) node = node.determinePlacement(null, node.defaultplacement);
	} while (prevNode != node);
	if ("text" in node) {
		var children = [new s.TextNode(node)];
		this.xchildren = function() {return children;};
	} else {
		this.xchildren = function() {return node.subnodes ? node.subnodes : [];};
	}
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

s.nodeSort = function(nodes, env) {
	var traces = nodes.map(function(node) {
		var trace = [];
		while(node) {
			trace.push(node);
			node = node.xparent();
		}
		trace.reverse();
		return trace;
	});
	var oldLength = nodes.length;
	nodes.splice(0, nodes.length);
	s.nodeSortHelper(traces, 0, env.docs, nodes);
	if (nodes.length != oldLength) {
		console.error("internal xpath error:  lost nodes during sort, oldLength=" + oldLength + ", new length=" + nodes.length);
		return;
	}
	var i = 0;
	while(i < nodes.length-1) {
		if (nodes[i] === nodes[i+1]) {
			nodes.splice(i, 1);
		} else {
			i++;
		}
	}
};

s.nodeSortHelper = function(traces, level, branches, result) {
	if (traces.length == 1) {
		result.push(traces[0].last());
		return;
	}
	if (s.attributeSortHelper(traces, level, result)) return;
	branches.forEach(function(branch) {
		var subtraces = [];
		for (var i = 0; i < traces.length; i++) {
			var trace = traces[i];
			if (trace === null) continue;
			if (trace[level] === branch) {
				if (trace.length == level + 1) {
					result.push(trace.last());
				} else {
					subtraces.push(trace);
				}
				traces[i] = null;
			}
		}
		if (subtraces.length) s.nodeSortHelper(subtraces, level + 1, branch.xchildren(), result);
	});
	if (!traces.every(function(node) {return node === null;})) {
		console.error("internal xpath error:  leftover traces in node sort traces=[" + traces + "], level=" + level + ", branches=" + branches + ", result=" + result);
		return;
	}
};

s.attributeSortHelper = function(traces, level, result) {
	var attributes = [];
	for (var i = 0; i < traces.length; i++) {
		var trace = traces[i];
		if (trace.length == level + 1 && trace.last() instanceof s.AttributeNode) {
			attributes.push(trace.last());
			traces[i] = null;
		}
	}
	if (attributes.length) {
		attributes.sort(s.AttributeNode.cmp);
		result.append(attributes);
	}
	return attributes.length == traces.length;
}
