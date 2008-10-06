function() {

var s = XPath.Semantics;

lz.node.prototype.recordXmlId = function(newValue) {
	if (!newValue) return;
	var node = this;
	while (node != null && node != node.immediateparent) {
		if ("trackXmlId" in node) {
			node.trackXmlId(this, newValue);
			return;
		}
		node = node.immediateparent;
	}
	console.error("internal xpath error: no xml:id tracker found for " + this);
};

function() {
	var oldConstruct = lz.node.prototype.construct;
	lz.node.prototype.construct = function(parent, args) {
		oldConstruct.call(this, parent, args);
		this.savedArgs = args;
		if ("xml_id" in args) {
			this.applyConstraintMethod("recordXmlId", [this, "xml_id"]);
			this.recordXmlId(args.xmlid);
		}
	};
}();

lz.node.prototype.xnode = "element";

lz.node.prototype.xname = function() {return this.constructor.tagname;}

lz.node.prototype.xparent = function() {
	return this.parent === this ? null : this.parent;
};

lz.node.prototype.xchildren = function() {
	var node = this;
	var prevNode;
	do {
		prevNode = node;
		if (node.defaultplacement) node = node.determinePlacement(null, node.defaultplacement);
	} while (prevNode != node);
	var textChildren = "text" in node ? [new s.TextNode(node)] : [];
	this.xchildren = function() {
		if (!node.subnodes) return textChildren;
		if (node.layouts && node.layouts.length) {
			return node.subnodes.select(function(child) {return !node.layouts.find(child);});
		} else {
			return node.subnodes;
		}
	};
	return this.xchildren();
};

lz.node.prototype.xattributes = function() {
	var results = [];
	for (var key in this.savedArgs) {
		if (key == "text" || key.charAt(0) == "_"  || key.indexOf("$") != -1) continue;
		var t = typeof this[key];
		if (!(t == "null" || t == "boolean" || t == "string" || t == "number")) continue;
		results.push(new s.AttributeNode(this, key));
	}
	results.sort(s.AttributeNode.cmp);
	this.xattributes = function() {return results;};
	return results;
};

lz.node.prototype.xattribute = function(name) {
	if (!name || name == "text" || name.charAt(0) == "_"  || name.indexOf("$") != -1) return null;
	if (!(name in this.savedArgs)) return null;
	var t = typeof this[name];
	if (!(t == "null" || t == "boolean" || t == "string" || t == "number")) return null;
	return new s.AttributeNode(this, name);
};

lz.node.prototype.atomized = function() {return !this.subnodes && this.text ? this.text : "";};
lz.node.prototype.serialized = function() {return s.serializeToXML(this);};

s.ConstructedElementNode = function(name) {this.name = name; this.attributes = []; this.text = ""; this.children = [];};
s.ConstructedElementNode.prototype.xnode = "element";
s.ConstructedElementNode.prototype.xname = function() {return this.name;};
s.ConstructedElementNode.prototype.xparent = function() {return null;};
s.ConstructedElementNode.prototype.xchildren = function() {
	if (this.children.length == 0) {
		return this.text ? [new ConstructedTextNode(this.text)] : [];
	} else {
		return this.children;
	}
};
s.ConstructedElementNode.prototype.xattributes = function() {return this.attributes;};
s.ConstructedElementNode.prototype.xattribute = function(name) {
	// Slow search, but should never be called
	console.warn("slow constructed element attribute lookup");
	for (var i = 0; i < this.attributes.length; i++) {
		if (name == this.attributes[i].key) return this.attributes[i];
	}
	return null;
};
s.ConstructedElementNode.prototype.atomized = function() {return this.children.length == 0 ? this.text : "";};
s.ConstructedElementNode.prototype.serialized = function() {return s.serializeToXML(this);};
s.ConstructedElementNode.prototype.toString = function() {
	return "element " + this.name + " { " + this.attributes + "; text = " + this.text + "; " + this.children + " } ";
};

s.WrappedElementNode = function(element, parent) {this.element = element; this.parent = parent;};
s.WrappedElementNode.prototype.xnode = "element";
s.WrappedElementNode.prototype.xname = function() {return this.element.xname();};
s.WrappedElementNode.prototype.xparent = function() {return this.parent;};
s.WrappedElementNode.prototype.xchildren = function() {return this.element.xchildren();};
s.WrappedElementNode.prototype.xattributes = function() {return this.element.xattributes();};
s.WrappedElementNode.prototype.xattribute = function(name) {return this.element.xattribute(name);};
s.WrappedElementNode.prototype.atomized = function() {return this.element.atomized();};
s.WrappedElementNode.prototype.toString = function() {
	return "wrappedElement(parentName = " + this.parent.xname() + "; " + this.element + ")";
};

s.DocumentNode = function(root) {this.root = root;};
s.DocumentNode.prototype.xnode = "document";
s.DocumentNode.prototype.xname = function() {return null;};
s.DocumentNode.prototype.xparent = function() {return null;};
s.DocumentNode.prototype.xchildren = function() {return [this.root];};
s.DocumentNode.prototype.toString = function() {return "document(" + this.root + ")";};

s.TextNode = function(node) {this.node = node;};
s.TextNode.prototype.xnode = "text";
s.TextNode.prototype.xname = function() {return null;};
s.TextNode.prototype.xparent = function() {return this.node;};
s.TextNode.prototype.xchildren = function() {return [];};
s.TextNode.prototype.atomized = function() {return this.node.text;};
s.TextNode.prototype.serialized = function() {return this.node.text;};
s.TextNode.prototype.toString = function() {return '"' + this.node.text + '"';};

s.ConstructedTextNode = function(text) {this.text = text;}
s.ConstructedTextNode.xnode = "text";
s.ConstructedTextNode.prototype.xname = function() {return null;};
s.ConstructedTextNode.prototype.xparent = function() {return null;};
s.ConstructedTextNode.prototype.xchildren = function() {return [];};
s.ConstructedTextNode.prototype.atomized = function() {return this.text;};
s.ConstructedTextNode.prototype.serialized = function() {return this.text;};
s.ConstructedTextNode.prototype.toString = function() {return '"' + this.text + '"';};

s.AttributeNode = function(node, key) {this.node = node; this.key = key;};
s.AttributeNode.prototype.equals = function(that) {
	return that instanceof s.AttributeNode && this.node === that.node && this.key === that.key;
};
s.AttributeNode.prototype.xnode = "attribute";
s.AttributeNode.prototype.xname = function() {return this.key;};
s.AttributeNode.prototype.xparent = function() {return this.node;};
s.AttributeNode.prototype.xchildren = function() {return [];};
s.AttributeNode.prototype.atomized = function() {return this.node[this.key];}
s.AttributeNode.prototype.toString = function() {return "@" + this.key + "=" + this.node[this.key];};
s.AttributeNode.cmp = function(a, b) {
	var ka = a.key, kb = b.key;
	return ka === kb ? 0 : (ka < kb ? -1 : 1);
};

s.ConstructedAttributeNode = function(key, value) {this.key = key; this.value = value;};
s.ConstructedAttributeNode.prototype.xnode = "attribute";
s.ConstructedAttributeNode.prototype.xname = function() {return this.key;};
s.ConstructedAttributeNode.prototype.xparent = function() {return null;};
s.ConstructedAttributeNode.prototype.xchildren = function() {return [];};
s.ConstructedAttributeNode.prototype.atomized = function() {return this.value;}
s.ConstructedAttributeNode.prototype.toString = function() {return "@" + this.key + "=" + this.value;};

}();