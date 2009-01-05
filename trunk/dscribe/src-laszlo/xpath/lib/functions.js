function() {

function node() {
	
}

function multiplicityOne(fname, args, index, kind) {
	if (args[index].length != 1) {
		console.error("[XPTY0004] call to " + fname + " needs argument #" + index + " to be a singleton, got " + args[index]);
		return false;
	}
	args[index] = args[index][0];
	var type = kind(args, index);
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
	var type = kind(args, index);
	if (type) {
		console.error("[XPTY0004] call to " + fname + " needs argument #" + index + " to be a " + type + ", got " + args[index]);
		return false;
	}
	return true;
}

function multiplicitySequence(fname, args, index, kind) {
	for (var i = 0; i < args[index].length; i++) {
		var type = kind(args[index], i);
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
	if ('ifMissing' in this) that.ifMissing = this.ifMissing;
	if ('ifEmpty' in this) that.ifEmpty = this.ifEmpty;
	return that;
};
ArgumentValidator.prototype.optional = function() {
	if (!('optionalVariant' in this)) {
		this.optionalVariant = this.clone();
		this.optionalVariant.multiplicity = multiplicityOptional;
	}
	return this.optionalVariant;
};
ArgumentValidator.prototype.sequence = function() {
	if (!('sequenceVariant' in this)) {
		this.sequenceVariant = this.clone();
		this.sequenceVariant.multiplicity = multiplicitySequence;
	}
	return this.sequenceVariant;
};
ArgumentValidator.prototype.useContextIfMissing = function() {
	if (!('useContextIfMissingVariant' in this)) {
		this.useContextIfMissingVariant = this.clone();
		this.useContextIfMissingVariant.ifMissing = replaceArgWithContextItem;
	}
	return this.useContextIfMissingVariant;
};
ArgumentValidator.prototype.useContextIfEmpty = function() {
	if (!('useContextIfEmptyVariant' in this)) {
		this.useContextIfEmptyVariant = this.clone();
		this.useContextIfEmptyVariant.ifEmpty = replaceArgWithContextItem;
	}
	return this.useContextIfEmptyVariant;
};

ArgumentValidator.prototype.validate = function(context, fname, args, index) {
	if (index >= args.length) {
		if (!'ifMissing' in this) {
			console.error("[XPTY0004] call to " + fname + " missing argument #" + index);
			return false;
		}
		if (!this.ifMissing(context, fname, args, index)) return false;
	}
	if (args[index].length == 0 && 'ifEmpty' in this) {
		if (!this.ifEmpty(context, fname, args, index)) return false;
	}
	return this.multiplicity(fname, args, index, this.kind);
};

XPath.ArgumentConstraints = {
	node: new ArgumentValidator(function(x, i) {return x[i].xnode ? null : "node";}),
	item: new ArgumentValidator(function(x, i) {return null;}),
	atom: new ArgumentValidator(function(x, i) {x[i] = x[i].atomized();}),
	string: new ArgumentValidator(function(x, i) {
		x[i] = x[i].atomized(); return (x[i] instanceof String || typeof x[i] == "string") ? null : "string";}),
	number: new ArgumentValidator(function(x, i) {
		x[i] = x[i].atomized(); var n = x[i].numberValue(); if (typeof n == "undefined") return "number"; x[i] = n; return null;}),
	integer: new ArgumentValidator(function(x, i) {
		x[i] = x[i].atomized(); var n = x[i].numberValue(); if (typeof n == "undefined" || !n.isInteger()) return "integer"; x[i] = n; return null;})
};

var arg = XPath.ArgumentConstraints;

XPath.Functions = {

root: {
	args: [arg.node.optional().useContextIfMissing()],
	fn: function(context, node) {
		if (node == null) return [];
		while (node.xparent()) node = node.xparent();
		return [node];
	}
},

id: {
	args: [arg.string.sequence(), arg.node.useContextIfMissing()],
	fn: function(context, idrefs, node) {
		while (node && !('getByXmlId' in node)) node = node.xparent();
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
		return result.length > 1 ? XPath.Semantics.nodeCombine([result], function(p) {return p[0];}, context.env) : result;
	}
},

max: {
	args: [arg.atom.sequence()],
	bounded: true,
	fn: function(context, arg) {
		if (arg.length == 0) return [];
		var max = arg[0].numberValue();
		for (var i = 1; i < arg.length; i++) {
			var n = arg[i].numberValue();
			if (n > max) max = n;
		}
		return [max];
	}
},

min: {
	args: [arg.atom.sequence()],
	bounded: true,
	fn: function(context, arg) {
		if (arg.length == 0) return [];
		var min = arg[0].numberValue();
		for (var i = 1; i < arg.length; i++) {
			var n = arg[i].numberValue();
			if (n < min) min = n;
		}
		return [min];
	}
},

'string-join': {
	args: [arg.string.sequence(), arg.string.optional()],
	bounded: true,
	fn: function(context, parts, separator) {
		return [parts.join(separator ? separator : '')];
	}
}

};

}();

XPath.Environment.prototype.builtinFunctions = XPath.Functions;
