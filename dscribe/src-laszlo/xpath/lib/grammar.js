function() {
	var g = XPath.Grammar;
	var s = XPath.Semantics;
	var op = Parsing.Operators;

	g.slash = op.token('/');  g.slash2 = op.token('//');
	g.lparen = op.token('(');  g.rparen = op.token(')');
	g.lbracket = op.token('[');  g.rbracket = op.token(']');
	g.lbrace = op.token('{');  g.rbrace = op.token('}');
	g.dollar = op.token('$');  g.star = op.token('*');  g.comma = op.token(',');
	g.comp = op.token('is', '<<', '>>', '=', '!=', '<=', '<', '>=', '>', 'eq', 'ne', 'lt', 'le', 'gt', 'ge');
	g.StringLiteral = op.process(op.dfsatoken(false, {
			start: {'"': "dquotebody", "'": "squotebody"},
			dquotebody: {'"': "dquoteend", "": "dquotebody"},
			dquoteend: {'"' : "dquotebody"},
			squotebody: {"'": "squoteend", "": "squotebody"},
			squoteend: {"'": "squotebody"}
		}, {dquotebody: "dquotebody", squotebody: "squotebody"}, {dquoteend: true, squoteend: true}),
		function(r) {return r.slice(1, -1);});
	g.NumberLiteral = op.process(op.dfsatoken(false, {
			start: {".": "dot", "0123456789": "whole"},
			dot: {"0123456789": "afterdot"},
			whole: {"0123456789": "whole", ".": "afterdot", "eE": "exponent"},
			afterdot: {"0123456789": "afterdot", "eE": "exponent"},
			exponent: {"+-": "expsign", "0123456789": "expvalue"},
			expsign: {"0123456789": "expvalue"},
			expvalue: {"0123456789": "expvalue"}
		}, {}, {whole: true, afterdot: true, expvalue: true}), function(r) {return Number(r);});
	g.QName = op.process(op.dfsatoken(false, {
			start: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ": "first"},
			first: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.": "first", ":": "sep"},
			sep: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ": "second"},
			second: {"_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.": "second"}
		}, {}, {first: true, second: true}), function(r) {return new s.QName(r);});
	
	g.ValueExpr = op.forward(g, 'PathExpr');
	g.UnaryExpr = op.process(op.each(op.many(op.token('-', '+')), g.ValueExpr), function(r) {
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
	
	g.IntersectExceptExpr = op.process(
			op.each(g.UnaryExpr, op.many(op.each(op.token('intersect', 'except'), g.UnaryExpr))),
			processBinaryList(s.SequenceOp));
	g.UnionExpr = op.process(
			op.each(g.IntersectExceptExpr, op.many(op.each(op.token('|', 'union'), g.IntersectExceptExpr))),
			processBinaryList(s.SequenceOp));
	g.MultiplicativeExpr = op.process(
			op.each(g.UnionExpr, op.many(op.each(op.token('*', 'div', 'idiv', 'mod'), g.UnionExpr))),
			processBinaryList(s.BinaryOp));
	g.AdditiveExpr = op.process(
			op.each(g.MultiplicativeExpr, op.many(op.each(op.token('-', '+'), g.MultiplicativeExpr))),
			processBinaryList(s.BinaryOp));
	g.RangeExpr = op.process(
			op.each(g.AdditiveExpr, op.optional(op.each(op.token('to'), g.AdditiveExpr))),
			function(r) {return r[1] == null ? r[0] : new s.Range(r[0], r[1][1]);});
	g.ComparisonExpr = op.process(
			op.each(g.RangeExpr, op.optional(op.each(g.comp, g.RangeExpr))),
			function(r) {return r[1] == null ? r[0] : s.newComparison(r[0], r[1][0], r[1][1]);});
	g.AndExpr = op.process(
			op.list(g.ComparisonExpr, op.token('and')),
			function(r) {return r.length == 1 ? r[0] : new s.And(r);});
	g.OrExpr = op.process(
			op.list(g.AndExpr, op.token('or')),
			function(r) {return r.length == 1 ? r[0] : new s.Or(r);});
	g.IfExpr = op.process(
			op.each(op.token('if'), op.between(g.lparen, op.forward(g, 'Expr'), g.rparen),
					op.token('then'), op.forward(g, 'ExprSingle'), op.token('else'), op.forward(g, 'ExprSingle')),
			function(r) {return new s.If(r[1], r[3], r[5]);});
	g.ExprSingle = op.any(g.IfExpr, g.OrExpr);	// or ForExpr, QuantifiedExpr
	g.Expr = op.process(
			op.list(g.ExprSingle, g.comma),
			function(r) {return r.length == 1 ? r[0] : new s.Sequence(r);});
	g.Literal = op.any(g.StringLiteral, g.NumberLiteral);
	g.VarRef = op.process(op.each(g.dollar, g.QName), function(r) {return new s.Var(r[1]);});
	g.ParenthesizedExpr = op.between(g.lparen, op.optional(g.Expr), g.rparen);
	g.ContextItemExpr = op.replace(op.token('.'), new s.ContextItem());
	g.FunctionCall = op.process(
			op.each(g.QName, op.between(g.lparen, op.optional(op.list(g.ExprSingle, g.comma)), g.rparen)),
			function(r) {return new s.FunctionCall(r[0], r[1] == null ? [] : r[1]);});
	g.ComputedElemOrAttrTail = op.each(
			op.any(g.QName, op.between(g.lbrace, g.Expr, g.rbrace)), op.between(g.lbrace, op.optional(g.Expr), g.rbrace));
	g.CompElemConstructor = op.process(
			op.each(op.token('element'), g.ComputedElemOrAttrTail),
			function(r) {return new s.ElementConstructor(r[1][0], r[1][1]);});
	g.CompAttrConstructor = op.process(
			op.each(op.token('attribute'), g.ComputedElemOrAttrTail),
			function(r) {return new s.AttributeConstructor(r[1][0], r[1][1]);});
	g.CompTextConstructor = op.process(
			op.each(op.token('text'), op.between(g.lbrace, g.Expr, g.rbrace)),
			function(r) {return new s.TextConstructor(r[1]);});
	g.ComputedConstructor = op.any(g.CompElemConstructor, g.CompAttrConstructor, g.CompTextConstructor);
	g.PrimaryExpr = op.any(g.Literal, g.VarRef, g.ParenthesizedExpr, g.ContextItemExpr, g.FunctionCall, g.ComputedConstructor);
	g.Predicate = op.between(g.lbracket, g.Expr, g.rbracket);
	g.NameTest = op.any(g.star, g.QName);
	g.NodeTest = g.NameTest;  // or g.KindTest
	g.Axis = op.process(op.each(op.token(
					'child', 'descendant-or-self', 'descendant', 'attribute', 'self', 'following-sibling',
					'following', 'parent', 'ancestor-or-self', 'ancestor', 'preceding-sibling', 'preceding'), op.token('::')),
					function(r) {return r[0];});
	g.AbbrevStep = op.process(op.each(op.optional(op.token('@')), g.NodeTest), function(r) {
		return new s.AxisStep(r[0] ? 'attribute' : 'child', r[1]);
	});
	g.ParentStep = op.process(op.token('..'), function(r) {return new s.AxisStep('parent', '*');}) 
	g.AxisStep = op.process(op.each(g.Axis, g.NodeTest), function(r) {return new s.AxisStep(r[0], r[1]);});
	g.StepExpr = op.process(op.each(op.any(g.AxisStep, g.ParentStep, g.PrimaryExpr, g.AbbrevStep), op.many(g.Predicate)), function(r) {
		if (r[1].length) {
			if (!(r[0] instanceof s.AxisStep)) r[0] = new s.Filter(r[0]);
			r[0].predicates = r[1];
		}
		return r[0];
	});
	g.PathExpr = op.any(
			op.process(op.each(
					op.optional(op.any(op.replace(g.slash2, new s.AxisStep("descendant-or-self", "*")), g.slash)),
					g.StepExpr,
					op.many(op.each(
							op.any(op.replace(g.slash2, new s.AxisStep("descendant-or-self", "*")), op.ignore(g.slash)),
							g.StepExpr))), function(r) {
				var fromRoot = r[0] != null;
				var steps = [];
				if (r[0] instanceof s.AxisStep) steps.push(r[0]);
				steps.push(r[1]);
				for (var i = 0; i < r[2].length; i++) {
					if (r[2][i][0]) steps.push(r[2][i][0]);
					steps.push(r[2][i][1]);
				}
				return (fromRoot || steps.length > 1) ? new s.Path(fromRoot, steps) : steps[0];
			}),
			op.replace(g.slash, new s.Path(true, [])));
} ();
