var XPath = {Grammar: {}, Semantics: {}};

XPath.parse = function(s) {
	var r = XPath.Grammar.Expr(" " + s);
	var tail = r[1];
	for (var i = 0; i < tail.length; i++) {
		if (" \n\t".indexOf(tail.charAt(i)) != -1) throw new Parsing.Exception(tail);
	}
	return r[0];
};

XPath.Semantics.Error = function(code, msg) {
	this.code = code;
	this.msg = msg;
};

{
	var s = XPath.Semantics;
	
	s.eval = function(v, context, env) {
		if ("eval" in v) return v.eval(context, env);
		if (v instanceof Array) return v.map(function(item) {s.eval(item, context, env);});
		return v;
	};
	
	s.atomize = function(v) {
		if ("atomized" in v) return v.atomized();
		if (v instanceof Array) return v.map(s.atomize);
		// TODO: deal with nodes
		return v;
	};
	
	s.effectiveBooleanValue = function(v) {
		if (typeof v == "boolean") return v;
		if (v == null) return false;
		if (v instanceof Array && v.length >= 1 && v[0] instanceof Node) return true;
		if (typeof v == "string") return v.length > 0;
		if (typeof v == "number") return !(v == 0 || v == Number.NaN);
		throw new s.Error("FORG0006", "expression has no effective boolean value: " + v);
	};
	
	s.numberValue = function(v) {
		if (typeof v == "number") return v;
		if (typeof v == "string") {
			var r = Number(v);
			if (r != Number.NaN || v == "NaN") return r;
		}
		if ("numberValue" in v) return v.numberValue();
		throw new s.Error("FORG0001", "not a number: " + v);
	};
	
	s.QName = function(s) {this.full = s; this.flat = s.replace(':', '');};
	s.QName.prototype.toString = function() {return "QName(" + this.full + ")";};
	
	s.Var = function(qname) {this.varName = qname;};
	s.Var.prototype.toString = function() {return "Var($" + this.varName + ")";};
	s.Var.prototype.eval = function(context, env) {return env.vars[this.varName.full];};

	s.Sequence = function(a) {this.items = a;}
	s.Sequence.prototype.toString = function() {return "Sequence(" + items + ")";};
	s.Sequence.prototype.eval = function(context, env) {
		var ev = s.eval(items, context, env);
		var r = [];
		for (var i = 0; i < ev.length; i++) {
			if (ev[i] == null) continue;
			if (ev[i] instanceof Array) r.push.apply(ev[i]); else r.push(ev[i]);
		}
		return r.length ? r : null;
	};
	
	s.Path = function(fromRoot, steps) {this.fromRoot = fromRoot;  this.steps = steps;};
	s.Path.prototype.toString = function() {return "Path(" + (this.fromRoot ? "root" + (this.steps.length ? "," : "") : "") + this.steps + ")";};
	
	s.AxisStep = function(axis, nodeName) {this.axis = axis;  this.nodeName = nodeName;  this.predicates = [];};
	s.AxisStep.prototype.toString = function() {return "AxisStep(" + this.axis + "::" + this.nodeName + " ["+ this.predicates + "])";};
	
	s.Filter = function(base, predicates) {this.base = base; this.predicates = predicates;}
	s.Filter.prototype.toString = function() {return "Filter(" + this.base + " [" + predicates + "])";};
	
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
	
	s.Comparison = function(a, comp, b) {this.a = a; this.comp = comp; this.b = b;}
	s.Comparison.prototype.toString = function() {return "Comparison(" + this.a + " " + this.comp + " " + this.b + ")";};
	s.Comparison.prototype.eval = function(context, env) {
		var va = s.eval(this.a, context, env), vb = s.eval(this.b, context, env);
		// TODO: do comparison
		return null;
	};
	
	s.Range = function(a, b) {this.a = a; this.b = b;}
	s.Range.prototype.toString = function() {return "Range(" + this.a + ", " + this.b + ")";};
	s.Range.prototype.eval = function(context, env) {
		var va = s.atomize(s.eval(this.a, context, env)), vb = s.atomize(s.eval(this.b, context, env));
		if (!(typeof va == "number" && va == parseInt(va))) throw new s.Error("XPTY0004", "first argument to range is not an integer: " + va);
		if (!(typeof vb == "number" && vb == parseInt(vb))) throw new s.Error("XPTY0004", "second argument to range is not an integer: " + vb);
		if (va > vb) return null;
		if (va == vb) return va;
		var r = new Array(vb - va + 1);
		for (var i = va; i <= vb; i++) r[i-va] = i;
		return r;
	};
	
	s.BinaryOp = function(a, op, b) {this.a = a; this.op = op; this.b = b;}
	s.BinaryOp.prototype.toString = function() {return "BinaryOp(" + this.a + " " + this.op + " " + this.b + ")";};
	s.BinaryOp.prototype.eval = function(context, env) {
		var va = s.eval(this.a, context, env), vb = s.eval(this.b, context, env);
		// TODO: do op
		return null;
	};
	
	s.SequenceOp = function(a, op, b) {this.a = a; this.op = op; this.b = b;}
	s.SequenceOp.prototype.toString = function() {return "SequenceOp(" + this.a + " " + this.op + " " + this.b + ")";};
	s.SequenceOp.prototype.eval = function(context, env) {
		var va = s.eval(this.a, context, env), vb = s.eval(this.b, context, env);
		// TODO: do op
		return null;
	};
	
	s.Negate = function(a) {this.arg = a;};
	s.Negate.prototype.toString = function() {return "Negate(" + this.arg + ")";};
	s.Negate.prototype.eval = function(context, env) {
		var v = s.atomize(s.eval(this.arg, context, env));
		if (v == null) return null;
		if (v instanceof Array) {
			if (v.length > 1) throw new s.Error("XPTY0004", "can't negate a sequence");
			v = v[0];
		}
		return -s.numberValue(v);
	};
	
}


Parsing.Operators.Trace = false;

with(Parsing.Operators) {
	var g = XPath.Grammar;
	var s = XPath.Semantics;
	
	g.slash = token('/');  g.slash2 = token('//');
	g.dot = token('.');  g.dot2 = token('..');
	g.lparen = token('(');  g.rparen = token(')');
	g.lbracket = token('[');  g.rbracket = token(']');
	g.dollar = token('$');  g.star = token('*');  g.comma = token(',');
	g.comp = token('=', '!=', '<', '<=', '>', '>=', ' eq ', ' ne ', ' lt ', ' le ', ' gt ', ' ge ', ' is ', '<<', '>>');
	g.StringLiteral = process(dfsatoken(false, {
			start: {'"': "dquotebody", "'": "squotebody"},
			dquotebody: {'"': "dquoteend", "": "dquotebody"},
			dquoteend: {'"' : "dquotebody"},
			squotebody: {"'": "squoteend", "": "squotebody"},
			squoteend: {"'": "squotebody"}
		}, {dquotebody: "dquotebody", squotebody: "squotebody"}, {dquoteend: true, squoteend: true}),
		function(r) {return r.slice(1, -1);});
	g.NumberLiteral = process(dfsatoken(false, {
			start: {".": "dot", "0123456789": "whole"},
			dot: {"0123456789": "afterdot"},
			whole: {"0123456789": "whole", ".": "afterdot", "eE": "exponent"},
			afterdot: {"0123456789": "afterdot", "eE": "exponent"},
			exponent: {"+-": "expsign", "0123456789": "expvalue"},
			expsign: {"0123456789": "expvalue"},
			expvalue: {"0123456789": "expvalue"}
		}, {}, {whole: true, afterdot: true, expvalue: true}), function(r) {return Number(r);});
	g.QName = process(dfsatoken(false, {
			start: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ": "first"},
			first: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.": "first", ":": "sep"},
			sep: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ": "second"},
			second: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.": "second"}
		}, {}, {first: true, second:true}), function(r) {return new s.QName(r);});
	
	g.ValueExpr = forward(g, 'PathExpr');
	g.UnaryExpr = process(each(many(token('-', '+')), g.ValueExpr), function(r) {
		var a = r[0];
		if (a) {
			var count = 0;
			for (var i = 0; i < a.length; i++) if (a[i] == '-') count++;
			if (count % 2 == 1) return new s.Negate(r[1]);
		}
		return r[1];
	});
	
	var processBinaryList = function(fn) {
		return function(r) {
			if (r[1].length == 0) return r[0];
			var exp = r[0];
			for (var i = 0; i < r[1].length; i++) exp = new fn(exp, r[1][i][0], r[1][i][1]);
			return exp;
		};
	};
	
	g.IntersectExceptExpr = process(each(g.UnaryExpr, many(each(token('intersect', 'except'), g.UnaryExpr))),
			processBinaryList(s.SequenceOp));
	g.UnionExpr = process(each(g.IntersectExceptExpr, many(each(token('|', 'union'), g.IntersectExceptExpr))),
			processBinaryList(s.SequenceOp));
	g.MultiplicativeExpr = process(each(g.UnionExpr, many(each(token('*', 'div', 'idiv', 'mod'), g.UnionExpr))),
			processBinaryList(s.BinaryOp));
	g.AdditiveExpr = process(each(g.MultiplicativeExpr, many(each(token('-', '+'), g.MultiplicativeExpr))),
			processBinaryList(s.BinaryOp));
	g.RangeExpr = process(each(g.AdditiveExpr, optional(each(token('to'), g.AdditiveExpr))), function(r) {
		return r[1] == null ? r[0] : new s.Range(r[0], r[1][1]);
	});
	g.ComparisonExpr = process(each(g.RangeExpr, optional(each(g.comp, g.RangeExpr))), function(r) {
		return r[1] == null ? r[0] : new s.Comparison(r[0], r[1][0], r[1][1]);		
	});
	g.AndExpr = process(list(g.ComparisonExpr, token('and')), function(r) {return r.length == 1 ? r[0] : new s.And(r);});
	g.OrExpr = process(list(g.AndExpr, token('or')), function(r) {return r.length == 1 ? r[0] : new s.Or(r);});
	g.ExprSingle = g.OrExpr;	// or ForExpr, QuantifiedExpr, IfExpr
	g.Expr = process(list(g.ExprSingle, g.comma), function(r) {return r.length == 1 ? r[0] : new s.Sequence(r);});
	g.Literal = any(g.StringLiteral, g.NumberLiteral);
	g.VarRef = process(each(g.dollar, g.QName), function(r) {return new s.Var(r[1]);});
	g.ParenthesizedExpr = between(g.lparen, optional(g.Expr), g.rparen);
	g.ContextItemExpr = replace(g.dot, new s.ContextItem());
	g.FunctionCall = process(
			each(g.QName, between(g.lparen, optional(list(g.ExprSingle, g.comma)), g.rparen)),
			function(r) {
				return new s.FunctionCall(r[0], r[1] == null ? [] : r[1]);
			});
	g.PrimaryExpr = any(g.Literal, g.VarRef, g.ParenthesizedExpr, g.ContextItemExpr, g.FunctionCall);
	g.Predicate = between(g.lbracket, g.Expr, g.rbracket);
	g.FilterExpr = process(each(g.PrimaryExpr, many(g.Predicate)), function(r) {
		if (r[1].length == 0) return r[0];
		return new s.Filter(r[0], r[1]);
	});
	g.NameTest = any(g.star, g.QName);
	g.NodeTest = g.NameTest;	// or KindTest
	g.Axis = process(each(token(
					'child', 'descendant', 'attribute', 'self', 'descendant-or-self', 'following-sibling',
					'following', 'parent', 'ancestor', 'preceding-sibling', 'preceding', 'ancestor-or-self'), token('::')),
					function(r) {return r[0];});
	g.AbbrevStep = any(
			process(g.dot2, function(r) {return new s.AxisStep('parent', '*');}),
			process(each(optional(token('@')), g.NodeTest), function(r) {
				return new s.AxisStep(r[0] ? 'attribute' : 'child', r[1]);
			}));
	g.AxisStep = process(each(
			any(
					process(each(g.Axis, g.NodeTest), function(r) {return new s.AxisStep(r[0], r[1]);}),
					g.AbbrevStep),
			many(g.Predicate)), function(r) {r[0].predicates = r[1]; return r[0];});
	g.StepExpr = any(g.FilterExpr, g.AxisStep);
	g.PathExpr = any(
			process(each(
					optional(any(replace(g.slash2, new s.AxisStep("descendant-or-self", "*")), g.slash)),
					g.StepExpr,
					many(each(
							any(replace(g.slash2, new s.AxisStep("descendant-or-self", "*")), ignore(g.slash)),
							g.StepExpr))), function(r) {
				var fromRoot = r[0] != null;
				var steps = [];
				if (r[0] instanceof s.AxisStep) steps.push(r[0]);
				steps.push(r[1]);
				for (var i = 0; i < r[2].length; i++) {
					if (r[2][i][0]) steps.push(r[2][i][0]);
					steps.push(r[2][i][1]);
				}
				return !fromRoot && steps.length == 1 ? steps[0] : new s.Path(fromRoot, steps);
			}),
			replace(g.slash, new s.Path(true, [])));
}

g = XPath.Grammar;
s = XPath.Semantics;
