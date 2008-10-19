function dump(data) {
	Debug.write(data);
	return data;
}

MAX_INT = 2 << 32 - 1;

Array.prototype.equals = function(that) {
	if (!(that instanceof Array)) return false;
	if (this.length != that.length) return false;
	for (var i = 0; i < this.length; i++) {
		if (this[i] != that[i]) return false;
	}
	return true;
};

String.prototype.fill = function() {
	var result = this;
	var i = 0;
	arguments.forEach(function(arg) {
		result = result.replace("$" + ++i, arg);
	});
	return result;
};

lz.node.prototype.findFormalAncestorSuchThat = function(predicate) {
	var node = this;
	while (node != null && node != node.parent) {
		if (predicate.call(node)) return node;
		node = node.parent;
	}
	return null;
};

lz.node.prototype.findAncestorSuchThat = function(predicate) {
	var node = this;
	while (node != null && node != node.immediateparent) {
		if (predicate.call(node)) return node;
		node = node.immediateparent;
	}
	return null;
};

lz.node.prototype.findAncestor = function(propertyName) {
	return this.findAncestorSuchThat(function() {return propertyName in this;});
};

lz.node.prototype.findAncestorProperty = function(propertyName, type) {
	var ancestor = this.findAncestorSuchThat(function() {
		return propertyName in this && (!type || this[propertyName] instanceof type);
	});
	return ancestor ? ancestor[propertyName] : null;
};

lz.node.prototype.callAncestorProperty = function(propertyName, type, fn) {
	if (fn == null) {
		fn = type;
		type = null;
	}
	var prop = this.findAncestorProperty(propertyName, type);
	if (prop) fn.call(prop);
	return !!prop;
};

lz.node.prototype.callAncestor = function(methodName) {
	var ancestor = this.findAncestor(methodName);
	if (ancestor) return ancestor[methodName].apply(ancestor, arguments.slice(1));
}
