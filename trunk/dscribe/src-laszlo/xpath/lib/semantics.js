function() {

var s = XPath.Semantics;

s.QName = function(s) {this.full = s; this.flat = s.replace(':', '_').replace('-', '_'); this.qualified = s.indexOf(":") != -1;};
s.QName.prototype.equals = function(that) {return this.full == that.full;}  // ignores actual namespace
s.QName.prototype.toString = function() {return "QName(" + this.full + ")";};
s.QName.prototype.atomized = function() {return this.flat;};
s.QName.prototype.eval = function(context) {return [this.flat];};
s.QName.prototype.isQualified = function() {return this.qualified;};

s.Var = function(qname) {this.varName = qname;};
s.Var.prototype.toString = function() {return "Var($" + this.varName + ")";};
s.Var.prototype.eval = function(context) {return context.env.vars[this.varName.full];};

s.Sequence = function(a) {this.items = a;}
s.Sequence.prototype.toString = function() {return "Sequence(" + this.items + ")";};
s.Sequence.prototype.eval = function(context) {
	return this.items.concatMap(function(item) {return item.eval(context);});
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

s.evalConstructedName = function(context, nameExpr) {
	var nameValue = nameExpr.eval(context).atomized();
	if (nameValue.length != 1 || typeof nameValue[0] != "string") {
		console.error("[XPTY0004] constructed attribute name is not a single string: " + nameValue);
		return;
	}
	return nameValue[0];
}

s.ElementConstructor = function(nameExpr, contentsExpr) {this.nameExpr = nameExpr; this.contentsExpr = contentsExpr;};
s.ElementConstructor.prototype.toString = function() {return "ElementConstructor(" + this.nameExpr + ", " + this.contentsExpr + ")";};
s.ElementConstructor.prototype.eval = function(context) {
	var name = s.evalConstructedName(context, this.nameExpr);
	if (!name) return;
	var contents = this.contentsExpr == null ? [] : this.contentsExpr.eval(context);
	if (!contents) return;
	
	var result = new s.ConstructedElementNode(name);
	var i = 0;
	
	var attribSet = {};
	while (i < contents.length) {
		var attr = contents[i];
		if (attr.xnode != "attribute") break;

		if (attr.key in attribSet) {
			console.error("[XQDY0025] duplicate attribute name in element contents: " + attr.key);
			return;
		}
		attribSet[attr.key] = true;
		result.attributes.push(attr);
		i++;
	}
	delete attribSet;
	result.attributes.sort(s.AttributeNode.cmp);
	
	var lastItemWasAtomic = false;
	while (i < contents.length) {
		var item = contents[i];
		if (item.xnode) {
			switch (item.xnode) {
				case "text":		result.text += item.atomized();  break;
				case "element":	result.children.push(new s.WrappedElementNode(item, result));  break;
				case "document":	result.children.push(new s.WrappedElementNode(item.root, result));  break;
				case "attribute":
					console.error("[XQTY0024] attribute following non-attribute in element contents: " + item);
					return;
			}
			lastItemWasAtomic = false;
		} else {
			if (lastItemWasAtomic) result.text += " ";
			result.text += item.atomized();
			lastItemWasAtomic = true;
		}
		i++;
	}
	
	return [result];
};

s.AttributeConstructor = function(nameExpr, contentsExpr) {this.nameExpr = nameExpr; this.contentsExpr = contentsExpr;};
s.AttributeConstructor.prototype.toString = function() {return "AttributeConstructor(" + this.nameExpr + ", " + this.contentsExpr + ")";};
s.AttributeConstructor.prototype.eval = function(context) {
	var name = s.evalConstructedName(context, this.nameExpr);
	if (!name) return;
	if (this.contentsExpr == null) {
		var contents = "";
	} else {
		var contents = this.contentsExpr.eval(context).atomized().join(" ");
		if (!contents) return;
	}
	return [new s.ConstructedAttributeNode(name, contents)];
};

s.TextConstructor = function(textExpr) {this.textExpr = textExpr;};
s.TextConstructor.prototype.toString = function() {return "TextConstructor(" + this.textExpr + ")";};
s.TextConstructor.prototype.eval = function(context) {
	return [new s.ConstructedTextNode(this.textExpr.eval(context).atomized().join(" "))];
};

s.If = function(condition, thenBranch, elseBranch) {this.condition = condition; this.thenBranch = thenBranch; this.elseBranch = elseBranch;};
s.If.prototype.toString = function() {return "If(" + this.condition + "; then " + this.thenBranch + "; else " + this.elseBranch + ")";};
s.If.prototype.eval = function(context) {
	if (this.condition.eval(context).effectiveBooleanValue()) {
		return this.thenBranch.eval(context);
	} else {
		return this.elseBranch.eval(context);
	}
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

}();
