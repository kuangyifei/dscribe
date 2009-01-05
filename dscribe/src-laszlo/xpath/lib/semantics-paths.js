function() {

var s = XPath.Semantics;


s.Path = function(fromRoot, steps) {
	this.fromRoot = fromRoot;
	for (var i = steps.length - 2; i >= 0; i--) {
		if ('axis' in steps[i] &&
				steps[i].axis == "descendant-or-self" &&
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
s.Path.prototype.analyze = function(analysis) {
	this.steps.forEach(function(step) {step.analyze(analysis);});
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

s.analyzePredicates = function(predicates, analysis) {
	if (predicates) predicates.forEach(function(predicate) {predicate.analyze(analysis);});
};

s.AxisStep = function(axis, nodeName) {
	this.axis = axis;  this.axisfn = this[this.axis.replace('-', '_', 'g')];  this.reverse = 'reverse' in this.axisfn && this.axisfn.reverse;
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
s.AxisStep.prototype.following = function(node) {
	// TODO: implement
	console.error("following:: not yet implemented");
};
s.AxisStep.prototype.following_sibling = function(node) {
	// TODO: implement
	console.error("following-sibling:: not yet implemented");
};
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
s.AxisStep.prototype.preceding = function(node, name) {
	// TODO: implement
	console.error("preceding:: not yet implemented");
};
s.AxisStep.prototype.preceding.reverse = true;
s.AxisStep.prototype.preceding_sibling = function(node, name) {
	// TODO: implement
	console.error("preceding-sibling:: not yet implemented");
};
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
s.AxisStep.prototype.analyze = function(analysis) {
	if (this.wildcard) {
		analysis.bounded = false;
		return;
	}
	var key = this.nodeName.flat;
	if (this.axis == 'attribute') key = '@' + key;
	if (!analysis.referencedNodeNames.find(key)) analysis.referencedNodeNames.push(key);
	s.analyzePredicates(this.predicates, analysis);
};

s.Filter = function(base) {this.base = base; this.predicates = null;}
s.Filter.prototype.toString = function() {return "Filter(" + this.base + " [" + this.predicates + "])";};
s.Filter.prototype.eval = function(context) {
	return s.applyPredicates(this.base.eval(context), this.predicates, false, context.env);
};
s.Filter.prototype.analyze = function(analysis) {
	this.base.analyze(analysis);
	s.analyzePredicates(this.predicates, analysis);
};
}();
