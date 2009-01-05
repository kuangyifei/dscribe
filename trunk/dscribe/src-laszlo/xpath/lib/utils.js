function() {

var s = XPath.Semantics;

Number.prototype.eval = function() {return [this];};
String.prototype.eval = function() {return [this];}

Number.prototype.analyze = function() {};
String.prototype.analyze = function() {};

Array.prototype.atomized = function() {return this.map(function(item) {return item.atomized();});};
Number.prototype.atomized = function() {return this.valueOf();};
String.prototype.atomized = function() {return this.toString();};

Array.prototype.serialized = function() {
	return this.map(function(item) {return item.serialized()}).join("");
};

Array.prototype.effectiveBooleanValue = function() {
	switch(this.length) {
		case 0:
			return false;
		case 1:
			var x = this[0];
			var t = typeof x;
			if (t == "boolean") return x;
			if (t == "string" || x instanceof String) return x.length > 0;
			if (t == "number" || x instanceof Number) return !(x == 0 || isNaN(x));
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
	var r = Number(this);
	if (!isNaN(r) || this == "NaN") return r;
};
Number.prototype.numberValue = function() {return this.valueOf();};
lz.node.prototype.numberValue = function() {};

Number.prototype.isInteger = function() {
	return this.valueOf() === parseInt(this);
};

s.serializeToXML = function(element) {
	if (element.xnode != "element") {
		console.error("trying to serializeToXML a non-element: " + element);
		return;
	}
	var name = element.xname();
	var attributes = element.xattributes(), children = element.xchildren();
	if (!element['text'] && attributes.length == 0 && children.length == 0) return "<" + name + "/>";
	var attrStrings = [], childrenStrings = [];
	for (var i = 0; i < attributes.length; i++) {
		attrStrings.push(attributes[i].key + '="' + attributes[i].atomized() + '"'); // TODO: escape value
	}
	return "<" + name + (attrStrings.length == 0 ? "" : " ") + attrStrings.join(" ") + ">"
			+ children.serialized() + "</" + name + ">";  // TODO: escape text
};

s.equatable = function(v) {
	if (typeof v == "number" || v instanceof Number || typeof v == "string" || v instanceof String || typeof v == "boolean" || v instanceof Boolean || 'equals' in v) return true;
	console.error("[XPTY0004] operand " + v + " of type " + typeof v + " cannot be equated");
	return false;
};

s.orderable = function(v) {
	if (typeof v == "number" || v instanceof Number || typeof v == "string" || v instanceof String || 'lessThan' in v) return true;
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

}();
