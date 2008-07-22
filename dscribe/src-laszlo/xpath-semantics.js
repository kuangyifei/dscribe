var s = XPath.Semantics;

lz.node.prototype.query = function(xpath, env) {
	env = env || {};
	if (!env.roots) env.roots = [canvas];
	env.docs = [];
	env.roots.forEach(function(root) {
		var doc = new s.DocumentNode(root);
		root.xparent = function(env2) {return doc;};
		env.docs.push(doc);
	});
	if (!env.vars) env.vars = {};
	var result = XPath.parse(xpath).eval(this, env);
	env.roots.forEach(function(root) {delete root.xparent;});
	return result;
};

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
		this.xchildren = function() {return node.subnodes;};
	}
	return this.xchildren();
};

lz.node.prototype.xattributes = function() {
	var results = [];
	for (var key in this) {
		if (key == "text" || key.charAt(0) == "_") continue;
		var t = typeof this[key];
		if (!(t == "null" || t == "boolean" || t == "string" || t == "number")) continue;
		results.push(new s.AttributeNode(this, key));
	}
	this.xattributes = function() {return results;};
	return results;
};

lz.node.prototype.xattribute = function(name) {
	if (!name || name == "text" || name.charAt(0) == "_") return null;
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
s.DocumentNode.prototype.xname = function() {return null;};
s.TextNode.prototype.xparent = function() {return this.node;};
s.TextNode.prototype.xchildren = function() {return null;};
s.TextNode.prototype.atomized = function() {return this.node.text;};
s.TextNode.prototype.toString = function() {return '"' + this.node.text + '"';};

s.AttributeNode = function(node, key) {this.node = node; this.key = key;};
s.AttributeNode.prototype.xnode = true;
s.DocumentNode.prototype.xname = function() {return this.key;};
s.AttributeNode.prototype.xparent = function() {return this.node;};
s.AttributeNode.prototype.xchildren = function() {return null;};
s.AttributeNode.prototype.atomized = function() {return this.node[this.key];}
s.AttributeNode.prototype.toString = function() {return "@" + this.key + "=" + this.node[this.key];};

s.eval = function(v, context, env) {
	return "eval" in v ? v.eval(context, env) : v;
};

s.atomize = function(v) {
	if ("atomized" in v) return v.atomized();
	if (v instanceof Array) return v.map(s.atomize);
	if (typeof v == "object") return v.text;
	return v;
};

s.effectiveBooleanValue = function(v) {
	var t = typeof v;
	if (t == "boolean") return v;
	if (v == null) return false;
	if (t == "object") return true;
	if (v instanceof Array && v.length >= 1 && typeof v[0] == "object") return true;
	if (t == "string") return v.length > 0;
	if (t == "number") return !(v == 0 || v == Number.NaN);
	console.error("[FORG0006] expression has no effective boolean value: " + v);
};

s.numberValue = function(v) {
	if (typeof v == "number") return v;
	if (typeof v == "string") {
		var r = Number(v);
		if (r != Number.NaN || v == "NaN") return r;
	}
	if ("numberValue" in v) return v.numberValue();
	console.error("[FORG0001] not a number: " + v);
};

s.equatable = function(v) {
	if (typeof v == "number" || typeof v == "string" || v.equals) {
		return true;
	}
	console.error("[XPTY0004] operands of type " + v.constructor + " cannot be equated");
	return false;
}

s.orderable = function(v) {
	if (typeof v == "number" || typeof v == "string" || v.lessThan) {
		return true;
	}
	console.error("[XPTY0004] operands of type " + v.constructor + " cannot be compared");
	return false;
}

s.QName = function(s) {this.full = s; this.flat = s.replace(':', '');};
s.QName.prototype.atomized = function() {return this;}
s.QName.prototype.equals = function(that) {return this.full == that.full;}  // ignores actual namespace
s.QName.prototype.toString = function() {return "QName(" + this.full + ")";};

s.Var = function(qname) {this.varName = qname;};
s.Var.prototype.toString = function() {return "Var($" + this.varName + ")";};
s.Var.prototype.eval = function(context, env) {return env.vars[this.varName.full];};

s.Sequence = function(a) {this.items = a;}
s.Sequence.prototype.toString = function() {return "Sequence(" + this.items + ")";};
s.Sequence.prototype.eval = function(context, env) {
	var ev = this.items.map(function(item) {return s.eval(item, context, env);});
	var r = [];
	for (var i = 0; i < ev.length; i++) {
		if (ev[i] == null) continue;
		if (ev[i] instanceof Array) r.push.apply(ev[i]); else r.push(ev[i]);
	}
	return r.length ? r : null;
};

s.Path = function(fromRoot, steps) {this.fromRoot = fromRoot;  this.steps = steps;};
s.Path.prototype.toString = function() {return "Path(" + (this.fromRoot ? "root" + (this.steps.length ? "," : "") : "") + this.steps + ")";};
s.Path.prototype.eval = function(context, env) {
	if (this.fromRoot) context = env.root;
	for (var i = 0; i < this.steps.length; i++) {
		context = this.steps[i].eval(context, env);
	}
	return context;
};

s.AxisStep = function(axis, nodeName) {this.axis = axis;  this.axisfn = this.axisTable[axis]; this.nodeName = nodeName;  this.predicates = [];};
s.AxisStep.prototype.axisTable = {
	"self": function(node, name) {
		return node instanceof lz.node && (name == "*" || name.flat == node.xname()) ? node : null;
	},
	"attribute": function(node, name) {
		if (!(node instanceof lz.node)) return null;
		if (name == "*") return node.xattributes();
		return node.xattribute(name.flat);
	},
	"child": function(node, name) {
		var r = node.xchildren();
		return (!r || name == "*") ? r : r.select(function(child) {return child.xname() == name.flat;});
	},
	"descendant": function(node) {},
	"descendant-or-self": function(node) {},
	"following": function(node) {},
	"following-sibling": function(node) {},
	"parent": function(node, name) {
		var r = node.xparent();
		return (!r || name == "*" || r.xname() == name.flat) ? r : null;
	},
	"ancestor": function(node) {},
	"ancestor-or-self": function(node) {},
	"preceding": function(node) {},
	"preceding-sibling": function(node) {}
};
s.AxisStep.prototype.toString = function() {return "AxisStep(" + this.axis + "::" + this.nodeName + " ["+ this.predicates + "])";};
s.AxisStep.prototype.eval = function(context, env) {
	if (context == null) return null;
	if (!(context instanceof Array)) context = [context];
	var result = [];
	for (var i = 0; i < context.length; i++) {
		var node = context[i];
		if (!node.xnode) {
			console.error("[XPTY0019] path expression contained non-node item: " + node);
			return;
		}
		var r = this.axisfn(node, this.nodeName);
		if (r) if (r instanceof Array) result.push.apply(result, r); else result.push(r);
	}
	// TODO: if result is a node sequence, uniquefy and sort
	// TODO: apply predicates
	switch(result.length) {
		case 0: return null;
		case 1: return result[0];
		default: return result;
	}
};

s.Filter = function(base, predicates) {this.base = base; this.predicates = predicates;}
s.Filter.prototype.toString = function() {return "Filter(" + this.base + " [" + predicates + "])";};
s.Filter.prototype.eval = function(context, env) {
	if (context == null) return null;
	// TODO: implement
	return null;
};


s.ContextItem = function() {};
s.ContextItem.prototype.toString = function() {return "ContextItem()";};
s.ContextItem.prototype.eval = function(context, env) {return context;};

s.FunctionCall = function(name, args) {this.name = name;  this.args = args;};
s.FunctionCall.prototype.toString = function() {return "FunctionCall(" + this.name + " with " + this.args + ")";};
s.FunctionCall.prototype.eval = function(context, env) {
	var v = s.eval(args, context, env);
	// TODO: call function
	return null;
};

s.Or = function(args) {this.args = args;}
s.Or.prototype.toString = function() {return "Or(" + this.args + ")";};
s.Or.prototype.eval = function(context, env) {
	for (var i = 0; i < this.args.length; i++) {
		if (s.effectiveBooleanValue(s.eval(this.args[i], context, env))) return true;
	}
	return false;
};

s.And = function(args) {this.args = args;}
s.And.prototype.toString = function() {return "And(" + this.args + ")";};
s.And.prototype.eval = function(context, env) {
	for (var i = 0; i < this.args.length; i++) {
		if (!s.effectiveBooleanValue(s.eval(this.args[i], context, env))) return false;
	}
	return true;
};

s.newComparison = function(a, op, b) {
	if (op == "is" || op == "<<" || op == ">>") return new s.NodeComparison(a, op, b);
	var c = op.charAt(0);
	if (c >= 'a' && c <= 'z') return new s.ValueComparison(a, op, b);
	return new s.GeneralComparison(a, op, b);
};

s.ValueComparison = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.ValueComparison.prototype.opTable = {
	'eq': function(a,b) {return s.equatable(a) ? (a.equals ? a.equals(b) : a == b) : null;},
	'ne': function(a,b) {return s.equatable(a) ? (a.equals ? !a.equals(b) : a != b) : null;},
	'lt': function(a,b) {return s.orderable(a) ? (a.lessThan ? a.lessThan(b) : a < b) : null;},
	'le': function(a,b) {return s.orderable(a) ? (a.lessThan ? a.equals(b) || a.lessThan(b) : a <= b) : null;},
	'gt': function(a,b) {return s.orderable(a) ? (a.lessThan ? !(a.equals(b) || a.lessThan(b)): a > b) : null;},
	'ge': function(a,b) {return s.orderable(a) ? (a.lessThan ? !a.lessThan(b) : a >= b) : null;}
};
s.ValueComparison.prototype.toString = function() {return "ValueComparison(" + this.a + " " + this.op + " " + this.b + ")";};
s.ValueComparison.prototype.eval = function(context, env) {
	var va = s.atomize(s.eval(this.a, context, env)), vb = s.atomize(s.eval(this.b, context, env));
	if (va == null || vb == null) return null;
	if (va instanceof Array || vb instanceof Array) {
		console.error("[XPTY0004] operand to value comparison cannot be a sequence: " + va + " " + this.op + " " + vb);
		return;
	}
	if (va.constructor != vb.constructor) {
		console.error("[XPTY0004] operands to value comparison must be of comparable type: " + va.constructor + " " + this.op + " " + vb.constructor);
		return;
	}
	return this.opfn(va, vb);
};

s.GeneralComparison = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.GeneralComparison.prototype.opTable = {
	'=': s.ValueComparison.prototype.opTable['eq'],
	'!=': s.ValueComparison.prototype.opTable['ne'],
	'<': s.ValueComparison.prototype.opTable['lt'],
	'<=': s.ValueComparison.prototype.opTable['le'],
	'>': s.ValueComparison.prototype.opTable['gt'],
	'>=': s.ValueComparison.prototype.opTable['ge']
};
s.GeneralComparison.prototype.toString = function() {return "GeneralComparison(" + this.a + " " + this.op + " " + this.b + ")";};
s.GeneralComparison.prototype.eval = function(context, env) {
	var va = s.atomize(s.eval(this.a, context, env)), vb = s.atomize(s.eval(this.b, context, env));
	if (va == null || vb == null) return false;
	if (!(va instanceof Array)) va = [va];
	if (!(vb instanceof Array)) vb = [vb];
	for (var i = 0; i < va.length; i++) {
		var wa = va[i];
		for (var j = 0; j < vb.length; j++) {
			var wb = vb[j];
			if (typeof wa == "number" && typeof wb != "number") {wb = s.numberValue(wb); if (typeof wb == "undefined") return;}
			else if (typeof wa != "number" && typeof wb == "number") {wa = s.numberValue(wa); if (typeof wa == "undefined") return;}
			else if (typeof wa == "object" && typeof wb == "string") {wb = new wa.constructor(wb); if (typeof wb == "undefined") return;}
			else if (typeof wb == "object" && typeof wa == "string") {wa = new wb.constructor(wa); if (typeof wa == "undefined") return;}
			if (this.opfn(wa, wb)) return true;
		}
	}
	return false;
};

s.NodeComparison = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.NodeComparison.prototype.opTable = {
	'is': function(a,b) {return a == b;},
	'<<': function(a,b) {return null;},  // TODO: implement
	'>>': function(a,b) {return null}  // TODO: implement
};
s.NodeComparison.prototype.toString = function() {return "NodeComparison(" + this.a + " " + this.op + " " + this.b + ")";};
s.NodeComparison.prototype.eval = function(context, env) {
	var va = s.eval(this.a, context, env), vb = s.eval(this.b, context, env);
	if (va == null || vb == null) return null;
	if (!(va.xnode && vb.xnode)) {
		console.error("[XPTY0004] operands to node comparison must be single nodes: " + va + " " + this.op + " " + vb);
		return;
	}
	return this.opfn(va, vb);
};

s.Range = function(a, b) {this.a = a; this.b = b;}
s.Range.prototype.toString = function() {return "Range(" + this.a + ", " + this.b + ")";};
s.Range.prototype.eval = function(context, env) {
	var va = s.atomize(s.eval(this.a, context, env)), vb = s.atomize(s.eval(this.b, context, env));
	if (!(typeof va == "number" && va == parseInt(va) && typeof vb == "number" && vb == parseInt(vb))) {
		console.error("[XPTY0004] arguments to range must be integers: " + va + ", " + vb);
		return;
	}
	if (va > vb) return null;
	if (va == vb) return va;
	var r = new Array(vb - va + 1);
	for (var i = va; i <= vb; i++) r[i-va] = i;
	return r;
};

s.BinaryOp = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.BinaryOp.prototype.opTable = {
	"+": function(a, b) {return a + b;},
	"-": function(a, b) {return a - b;},
	"*": function(a, b) {return a * b;},
	"div": function(a, b) {return a / b;},
	"idiv": function(a, b) {return parseInt(a/b);},
	"mod": function(a,b) {return a % b;}
};
s.BinaryOp.prototype.toString = function() {return "BinaryOp(" + this.a + " " + this.op + " " + this.b + ")";};
s.BinaryOp.prototype.eval = function(context, env) {
	var va = s.eval(this.a, context, env), vb = s.eval(this.b, context, env);
	if (va == null || vb == null) return null;
	if (va instanceof Array || vb instanceof Array) {
		console.error("[XPTY0004] operand for arithmetic operation cannot be a sequence: " + va + this.op + vb);
		return;
	}
	va = s.numberValue(va);  vb = s.numberValue(vb);
	return this.opfn(va, vb);
};

s.SequenceOp = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.SequenceOp.prototype.opTable = {
	"union": function(a, b) {}, // TODO: implement
	"intersect": function(a, b) {},  // TODO: implement
	"except": function(a, b) {}  // TODO: implement;
};
s.SequenceOp.prototype.opTable["|"] = s.SequenceOp.prototype.opTable["union"];
s.SequenceOp.prototype.toString = function() {return "SequenceOp(" + this.a + " " + this.op + " " + this.b + ")";};
s.SequenceOp.prototype.eval = function(context, env) {
	var va = s.eval(this.a, context, env), vb = s.eval(this.b, context, env);
	return this.opfn(va, vb);
};

s.Negate = function(a) {this.arg = a;};
s.Negate.prototype.toString = function() {return "Negate(" + this.arg + ")";};
s.Negate.prototype.eval = function(context, env) {
	var v = s.atomize(s.eval(this.arg, context, env));
	if (v == null) return null;
	if (v instanceof Array) {
		if (v.length > 1) {
			console.error("[XPTY0004] can't negate a sequence");
			return;
		}
		v = v[0];
	}
	return -s.numberValue(v);
};
