var s = XPath.Semantics;

s.QName = function(s) {this.full = s; this.flat = s.replace(':', ''); this.qualified = s.indexOf(":") != -1;};
s.QName.prototype.equals = function(that) {return this.full == that.full;}  // ignores actual namespace
s.QName.prototype.toString = function() {return "QName(" + this.full + ")";};
s.QName.prototype.atomized = function() {return this;};
s.QName.prototype.eval = function() {return this;};
s.QName.prototype.isQualified = function() {return this.qualified;};

s.Var = function(qname) {this.varName = qname;};
s.Var.prototype.toString = function() {return "Var($" + this.varName + ")";};
s.Var.prototype.eval = function(context) {return context.env.vars[this.varName.full];};

s.Sequence = function(a) {this.items = a;}
s.Sequence.prototype.toString = function() {return "Sequence(" + this.items + ")";};
s.Sequence.prototype.eval = function(context) {
	return this.items.concatMap(function(item) {return item.eval(context);});
};

s.Path = function(fromRoot, steps) {
	this.fromRoot = fromRoot;
	for (var i = steps.length - 2; i >= 0; i--) {
		if (steps[i].axis == "descendant-or-self" &&
				steps[i].nodeName == "*" &&
				!steps[i].predicates &&
				steps[i+1].axis == "child") {
			var combinedStep = new s.AxisStep("descendant-by-parent", steps[i+1].nodeName);
			combinedStep.predicates = steps[i+1].predicates;
			steps.splice(i, 2, combinedStep);
		}
	}
	this.steps = steps;
};
s.Path.prototype.toString = function() {return "Path(" + (this.fromRoot ? "root" + (this.steps.length ? "," : "") : "") + this.steps + ")";};
s.Path.prototype.eval = function(context) {
	var nodes = this.fromRoot ? context.env.docs : [context.item];
	var singleDerivation = true;
	for (var i = 0; i < this.steps.length; i++) {
		var step = this.steps[i];
		var stepResults = [];
		if (nodes.length > 1) singleDerivation = false;
		var localContext = {size: nodes.length, env: context.env};
		for (var j = 0; j < nodes.length; j++) {
			localContext.item = nodes[j];
			localContext.position = j + 1;
			stepResults.append(step.eval(localContext));
		}
		nodes = stepResults;
		if (i < this.steps.length - 1 && !nodes.every(function(item) {return item.xnode;})) {
			console.error("[XPTY0019] result of intermediate step contains atomic values: " + this + ", step " + step + ", result " + nodes);
			return;
		}
	}
	if (nodes.length > 1) {
		var allNodes = nodes.every(function(item) {return item.xnode;});
		var someNodes = nodes.some(function(item) {return item.xnode;});
		if (someNodes && !allNodes) {
			console.error("[XPTY0018] result of path contains a mix of nodes and atomic values: " + this + " -> " + nodes);
			return;
		}
		if (allNodes && !singleDerivation) nodes = s.nodeCombine([nodes], function(p) {return p[0];}, context.env);
	}
	return nodes;
};

s.applyPredicates = function(items, predicates, reverse, env) {
	if (!predicates) return items;
	var localContext = {size: items.length, env: env};
	predicates.forEach(function(predicate) {
		items = items.select(function(item, index) {
			localContext.item = item;
			localContext.position = index + 1;
			var r = predicate.eval(localContext);
			if (r.length == 1 && (typeof r[0] == "number" || r[0] instanceof Number)) {
				return r[0] == (reverse ? items.length - index : index + 1);
			}
			return r.effectiveBooleanValue();
		});
	});
	return items;
};

s.AxisStep = function(axis, nodeName) {
	this.axis = axis;  this.axisfn = this[this.axis.replace('-', '_', 'g')];  this.reverse = !!this.axisfn.reverse;
	this.nodeName = nodeName;  this.wildcard = nodeName === "*"; this.predicates = null;
};
s.AxisStep.prototype.self = function(node) {
	return node instanceof lz.node && (this.wildcard || this.nodeName.flat == node.xname()) ? [node] : [];
};
s.AxisStep.prototype.attribute = function(node) {
	if (!(node instanceof lz.node)) return [];
	if (this.wildcard) return node.xattributes();
	var attr = node.xattribute(this.nodeName.flat);
	return attr ? [attr] : [];
};
s.AxisStep.prototype.child = function(node) {
	var r = node.xchildren();
	if (!this.wildcard) r = r.select(
			function(child) {return child instanceof lz.node && child.xname() == this.nodeName.flat;},
			this);
	return r;
};
s.AxisStep.prototype.descendant = function(node) {
	return node.xchildren().concatMap(
			function(child) {return this.descendant_or_self(child);},
			this);
};
s.AxisStep.prototype.descendant_or_self = function(node) {
	return this.self(node).append(this.descendant(node));
};
s.AxisStep.prototype.descendant_by_parent = function(node, accumulator) {
	if (!accumulator) accumulator = [];
	var children = node.xchildren();
	var r = children;
	if (!this.wildcard) r = r.select(
			function(child) {return child instanceof lz.node && child.xname() == this.nodeName.flat;},
			this);
	if (r.length) accumulator.push(r);
	children.forEach(function(child) {this.descendant_by_parent(child, accumulator);}, this);
	return accumulator;
};
s.AxisStep.prototype.following = function(node) {};  // TODO: implement
s.AxisStep.prototype.following_sibling = function(node) {};  // TODO: implement
s.AxisStep.prototype.parent = function(node) {
	var r = node.xparent();
	return r ? this.self(r) : [];
};
s.AxisStep.prototype.parent.reverse = true;
s.AxisStep.prototype.ancestor = function(node) {
	var parent = node.xparent();
	return parent ? this.ancestor_or_self(parent) : [];
};
s.AxisStep.prototype.ancestor.reverse = true;
s.AxisStep.prototype.ancestor_or_self = function(node) {
	return this.self(node).append(this.ancestor(node));
};
s.AxisStep.prototype.ancestor_or_self.reverse = true;
s.AxisStep.prototype.preceding = function(node, name) {};  // TODO: implement
s.AxisStep.prototype.preceding.reverse = true;
s.AxisStep.prototype.preceding_sibling = function(node, name) {};  // TODO: implement
s.AxisStep.prototype.preceding_sibling.reverse = true;
s.AxisStep.prototype.toString = function() {return "AxisStep(" + this.axis + "::" + this.nodeName + " ["+ this.predicates + "])";};
s.AxisStep.prototype.eval = function(context) {
	if (!context.item.xnode) {
		console.error("[XPTY0020] step context contained non-node item: " + context.item);
		return;
	}
	var result = this.axisfn(context.item);
	if (result.length > 0) {
		if (this.reverse) result.reverse();
		if (result[0] instanceof Array) {
			result = result.concatMap(function(r) {
				return s.applyPredicates(r, this.predicates, this.reverse, context.env);}, this);
		} else {
			result = s.applyPredicates(result, this.predicates, this.reverse, context.env);
		}
	}
	return result;
};

s.Filter = function(base) {this.base = base; this.predicates = null;}
s.Filter.prototype.toString = function() {return "Filter(" + this.base + " [" + this.predicates + "])";};
s.Filter.prototype.eval = function(context) {
	return s.applyPredicates(this.base.eval(context), this.predicates, false, context.env);
};


s.ContextItem = function() {};
s.ContextItem.prototype.toString = function() {return "ContextItem()";};
s.ContextItem.prototype.eval = function(context) {return [context.item];};

s.FunctionCall = function(name, args) {this.name = name;  this.args = args;};
s.FunctionCall.prototype.toString = function() {return "FunctionCall(" + this.name + " with (" + this.args + "))";};
s.FunctionCall.prototype.eval = function(context) {
	var v = this.args.map(function(arg) {return arg.eval(context);});
	var fnEntry = context.env.functions[this.name.full];
	if (!fnEntry) fnEntry = context.env.builtinFunctions[this.name.full];
	if (!fnEntry) {
		console.error("[XPST0017] unknown function " + this.name.full);
		return;
	}
	if (v.length > fnEntry.args.length) {
		console.error("[XPST0017] function " + this.name.full + " expected at most " + fnEntry.args.length + " argument" + (fnEntry.args.length == 1 ? "" : "s") + ", got " + v.length);
		return;
	}
	for (var i = 0; i < fnEntry.args.length; i++) {
		if (!fnEntry.args[i].validate(context, this.name.full, v, i)) return;
	}
	v.unshift(context);
	return fnEntry.fn.apply(null, v);
};

s.Or = function(args) {this.args = args;}
s.Or.prototype.toString = function() {return "Or(" + this.args + ")";};
s.Or.prototype.eval = function(context) {
	return args.some(function(arg) {return arg.eval(context).effectiveBooleanValue();});
};

s.And = function(args) {this.args = args;}
s.And.prototype.toString = function() {return "And(" + this.args + ")";};
s.And.prototype.eval = function(context) {
	return args.every(function(arg) {return arg.eval(context).effectiveBooleanValue();});
};

s.newComparison = function(a, op, b) {
	if (op == "is" || op == "<<" || op == ">>") return new s.NodeComparison(a, op, b);
	var c = op.charAt(0);
	if (c >= 'a' && c <= 'z') return new s.ValueComparison(a, op, b);
	return new s.GeneralComparison(a, op, b);
};

s.ValueComparison = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.ValueComparison.prototype.opTable = {
	'eq': function(a,b) {if (s.equatable(a)) return a.equals ? a.equals(b) : a == b;},
	'ne': function(a,b) {if (s.equatable(a)) return a.equals ? !a.equals(b) : a != b;},
	'lt': function(a,b) {if (s.orderable(a)) return a.lessThan ? a.lessThan(b) : a < b;},
	'le': function(a,b) {if (s.orderable(a)) return a.lessThan ? a.equals(b) || a.lessThan(b) : a <= b;},
	'gt': function(a,b) {if (s.orderable(a)) return a.lessThan ? !(a.equals(b) || a.lessThan(b)): a > b;},
	'ge': function(a,b) {if (s.orderable(a)) return a.lessThan ? !a.lessThan(b) : a >= b;}
};
s.ValueComparison.prototype.toString = function() {return "ValueComparison(" + this.a + " " + this.op + " " + this.b + ")";};
s.ValueComparison.prototype.eval = function(context) {
	var va = this.a.eval(context).atomized(), vb = this.b.eval(context).atomized();
	if (!va.length || !vb.length) return [];
	if (va.length > 1 || vb.length > 1) {
		console.error("[XPTY0004] operand to value comparison cannot be a sequence: " + va + " " + this.op + " " + vb);
		return;
	}
	if (va[0].constructor != vb[0].constructor) {
		console.error("[XPTY0004] operands to value comparison must be of comparable type: " + va.constructor + " " + this.op + " " + vb.constructor);
		return;
	}
	return [this.opfn(va[0], vb[0])];
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
s.GeneralComparison.prototype.eval = function(context) {
	var va = this.a.eval(context).atomized(), vb = this.b.eval(context).atomized();
	if (!va.length || !vb.length) return [false];
	for (var i = 0; i < va.length; i++) {
		var wa = va[i];
		for (var j = 0; j < vb.length; j++) {
			var wb = vb[j];
			if (typeof wa == "number" && typeof wb != "number") {wb = wb.numberValue(); if (typeof wb == "undefined") return;}
			else if (typeof wa != "number" && typeof wb == "number") {wa = wa.numberValue(); if (typeof wa == "undefined") return;}
			else if (typeof wa == "object" && typeof wb == "string") {wb = new wa.constructor(wb); if (typeof wb == "undefined") return;}
			else if (typeof wb == "object" && typeof wa == "string") {wa = new wb.constructor(wa); if (typeof wa == "undefined") return;}
			if (this.opfn(wa, wb)) return [true];
		}
	}
	return [false];
};

s.NodeComparison = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.NodeComparison.prototype.opTable = {
	'is': function(a, b, env) {return a.equals ? a.equals(b) : a === b;},
	'<<': function(a, b, env) {
		var nodes = [a, b];
		s.nodeSort(nodes, env);
		return nodes.length == 2 && nodes[0] === a;
	},
	'>>': function(a, b, env) {
		return s.NodeComparison.prototype.opTable['<<'](b, a, env);
	}
};
s.NodeComparison.prototype.toString = function() {return "NodeComparison(" + this.a + " " + this.op + " " + this.b + ")";};
s.NodeComparison.prototype.eval = function(context) {
	var va = this.a.eval(context), vb = this.b.eval(context);
	if (!va.length || !vb.length) return [];
	if (va.length !=1 || vb.length != 1) {
		console.error("[XPTY0004] operands to node comparison must be single nodes: " + va + " " + this.op + " " + vb);
		return;
	}
	return [this.opfn(va[0], vb[0], env)];
};

s.Range = function(a, b) {this.a = a; this.b = b;}
s.Range.prototype.toString = function() {return "Range(" + this.a + ", " + this.b + ")";};
s.Range.prototype.eval = function(context) {
	var va = this.a.eval(context).atomized(), vb = this.b.eval(context).atomized();
	if (!(va.length == 1 && typeof va[0] == "number" && va[0].isInteger() &&
			vb.length == 1 && typeof vb[0] == "number" && vb[0].isInteger())) {
		console.error("[XPTY0004] arguments to range must be integers: " + va + ", " + vb);
		return;
	}
	va = va[0];  vb = vb[0];
	if (va > vb) return [];
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
s.BinaryOp.prototype.eval = function(context) {
	var va = this.a.eval(context), vb = this.b.eval(context);
	if (!va.length || !vb.length) return [];
	if (va.length > 1 || vb.length > 1) {
		console.error("[XPTY0004] operand for arithmetic operation cannot be a sequence: " + va + this.op + vb);
		return;
	}
	return [this.opfn(va.numberValue(), vb.numberValue())];
};

s.SequenceOp = function(a, op, b) {this.a = a; this.op = op; this.opfn = this.opTable[op]; this.b = b;}
s.SequenceOp.prototype.opTable = {
	"union": function(a, b, env) {return s.nodeCombine([a, b], function(p) {return p[0] || p[1];}, env);},
	"intersect": function(a, b, env) {return s.nodeCombine([a, b], function(p) {return p[0] && p[1];}, env);},
	"except": function(a, b, env) {return s.nodeCombine([a, b], function(p) {return p[0] && !p[1];}, env);}
};
s.SequenceOp.prototype.opTable["|"] = s.SequenceOp.prototype.opTable["union"];
s.SequenceOp.prototype.toString = function() {return "SequenceOp(" + this.a + " " + this.op + " " + this.b + ")";};
s.SequenceOp.prototype.eval = function(context) {
	var va = this.a.eval(context), vb = this.b.eval(context);
	return this.opfn(va, vb, context.env);
};

s.Negate = function(a) {this.arg = a;};
s.Negate.prototype.toString = function() {return "Negate(" + this.arg + ")";};
s.Negate.prototype.eval = function(context) {
	var v = this.arg.eval(context).atomized();
	if (!v.length) return [];
	if (v.length == 1) return [-v.numberValue()];
	console.error("[XPTY0004] can't negate a sequence");
};
