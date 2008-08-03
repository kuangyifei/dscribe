function() {

function node() {
	
}

function multiplicityOne(fname, args, index, kind) {
	if (args[index].length != 1) {
		console.error("[XPTY0004] call to " + fname + " needs argument #" + index + " to be a singleton, got " + args[index]);
		return false;
	}
	args[index] = args[index][0];
	var type = kind(args[index]);
	if (type) {
		console.error("[XPTY0004] call to " + fname + " needs argument #" + index + " to be a " + type + ", got " + args[index]);
		return false;
	}
	return true;
}

function multiplicityOptional(fname, args, index, kind) {
	if (args[index].length > 1) {
		console.error("[XPTY0004] call to " + fname + " needs argument #" + index + " to be empty or a singleton, got " + args[index]);
		return false;
	}
	args[index] = args[index].length == 0 ? null : args[index][0];
	if (args[index] == null) return true;
	var type = kind(args[index]);
	if (type) {
		console.error("[XPTY0004] call to " + fname + " needs argument #" + index + " to be a " + type + ", got " + args[index]);
		return false;
	}
	return true;
}

function multiplicitySequence(fname, args, index, kind) {
	for (var i = 0; i < args[index].length; i++) {
		var type = kind(args[index][i]);
		if (type) {
			console.error("[XPTY0004] call to " + fname + " needs argument #" + index + " to be a sequence of " + type + "s, got " + args[index]);
			return false;
		}
	}
	return true;
}

function replaceArgWithContextItem(context, fname, args, index) {
	if (!context.item) {
		console.error("[XPDY0002] call to " + fname + " needs context item to be defined for defaulted argument #" + index);
		return false;
	}
	args[index] = [context.item];
	return true;
}

function ArgumentValidator(kind) {
	this.kind = kind;
	this.multiplicity = multiplicityOne;
};
ArgumentValidator.prototype.clone = function() {
	var that = new ArgumentValidator(this.kind);
	that.multiplicity = this.multiplicity;
	that.ifMissing = this.ifMissing;
	that.ifEmpty = this.ifEmpty;
	return that;
};
ArgumentValidator.prototype.optional = function() {
	if (!this.optionalVariant) {
		this.optionalVariant = this.clone();
		this.optionalVariant.multiplicity = multiplicityOptional;
	}
	return this.optionalVariant;
};
ArgumentValidator.prototype.sequence = function() {
	if (!this.sequenceVariant) {
		this.sequenceVariant = this.clone();
		this.sequenceVariant.multiplicity = multiplicitySequence;
	}
	return this.sequenceVariant;
};
ArgumentValidator.prototype.useContextIfMissing = function() {
	if (!this.useContextIfMissingVariant) {
		this.useContextIfMissingVariant = this.clone();
		this.useContextIfMissingVariant.ifMissing = replaceArgWithContextItem;
	}
	return this.useContextIfMissingVariant;
};
ArgumentValidator.prototype.useContextIfEmpty = function() {
	if (!this.useContextIfEmptyVariant) {
		this.useContextIfEmptyVariant = this.clone();
		this.useContextIfEmptyVariant.ifEmpty = replaceArgWithContextItem;
	}
	return this.useContextIfEmptyVariant;
};

ArgumentValidator.prototype.validate = function(context, fname, args, index) {
	if (index >= args.length) {
		if (!this.ifMissing) {
			console.error("[XPTY0004] call to " + fname + " missing argument #" + index);
			return false;
		}
		if (!this.ifMissing(context, fname, args, index)) return false;
	}
	if (args[index].length == 0 && this.ifEmpty) {
		if (!this.ifEmpty(context, fname, args, index)) return false;
	}
	return this.multiplicity(fname, args, index, this.kind);
};

XPath.ArgumentConstraints = {
	node: new ArgumentValidator(function(x) {return x.xnode ? null : "node";}),
	item: new ArgumentValidator(function(x) {return null;}),
	atom: new ArgumentValidator(function(x) {return (x instanceof String || typeof x == "string" || x instanceof Number || typeof x == "number") ? null : "atom";}),
	string: new ArgumentValidator(function(x) {return (x instanceof String || typeof x == "string") ? null : "string";}),
	number: new ArgumentValidator(function(x) {return (x instanceof Number || typeof x == "number") ? null : "number";}),
	integer: new ArgumentValidator(function(x) {return ((x instanceof Number || typeof x == "number") && x === parseInt(x)) ? null : "integer";})
};

}();

with(XPath.ArgumentConstraints) { XPath.Functions = {

root: {
	args: [node.optional().useContextIfMissing()],
	fn: function(context, node) {
		if (node == null) return [];
		while (node.xparent()) node = node.xparent();
		return [node];
	}
},

id: {
	args: [string.sequence(), node.useContextIfMissing()],
	fn: function(context, idrefs, node) {
		while (node && !node.getByXmlId) node = node.xparent();
		if (!node) {
			console.error("internal xpath error: failed to find xml:id map");
			return;
		}
		var result = [];
		for (var i = 0; i < idrefs.length; i++) {
			var ids = idrefs[i].split(' ');
			for (var j = 0; j < ids.length; j++) {
				if (!ids[j]) continue;
				var r = node.getByXmlId(ids[j]);
				if (r) result.push(r);
			}
		}
		return result.length > 1 ? s.nodeCombine([result], function(p) {return p[0];}, context.env) : result;
	}
}

}};

XPath.Environment.prototype.builtinFunctions = XPath.Functions;
