// check if lang module is already defined, and if so silently skip re-defining it
if (!this['http://www.ideanest.com/scripts/20040101/lang.js'])

(function() {

var PendingModule = function () {};
var Module = function() {};

var lang = new PendingModule();


lang.assert = function(expr, msg) {
	if (!expr) throw new Error(msg ? msg : "assertion failed");
};

lang.Type = {
	typeName: 'Type',
	isTypeOf: function(that) {
		return !!that.isTypeOf && typeof(that.isTypeOf) == "function";
	}
};

lang.Type.NONE = {
	typeName: 'void',
	isTypeOf: function(that) {
		return result === undefined;
	}
};

Object.isTypeOf = function() {return true;};
Object.typeName = 'Object';

String.isTypeOf = function(that) {return typeof(that) == 'string' || that instanceof String;}
String.typeName = 'String';

Number.isTypeOf = function(that) {return typeof(that) == 'number' || that instanceof Number;}
Number.typeName = 'Number';

Function.isTypeOf = function(that) {return typeof(that) == 'function' || that instanceof Function;}
Function.typeName = 'Function';


lang.lambda = function(attribs, func) {
	var wrappedFunc = func;
	if (attribs.params || attribs.result) {
		var fstr = func.toString();
		var paramNames = fstr.substring(fstr.indexOf('(')+1, fstr.indexOf(')')).split(/ *, */);
		var guards = new Array();
		for (var i=0; i<paramNames.length; i++) {
			var guard = attribs.params[paramNames[i]];
			if (guard === undefined) guard = Object;
			guards.push(guard);
		}
		var resultGuard = attribs.result;
		if (resultGuard === null) resultGuard = lang.Type.NONE;
		if (guards.length > 0 && resultGuard !== undefined) {
			wrappedFunc = function() {
				for (var i=0; i < arguments.length; i++) {
					if (guards[i] && !guards[i].isTypeOf(arguments[i])) {
						throw new Error("Illegal argument type for parameter '" + paramNames[i]
							+ "'; expected type " + guards[i].typeName
							+ ", got value '" + arguments[i] + "' instead."
						);
					}
				}
				var result = func.apply(this, arguments);
				if (!resultGuard.isTypeOf(result)) {
					throw new Error("Illegal result type; expected type "
						+ resultGuard.typeName + ", got value '" + result + "' instead."
					);
				}
				return result;
			};
		} else if (guards.length > 0 && resultGuard === undefined) {
			wrappedFunc = function() {
				for (var i=0; i < arguments.length; i++) {
					if (guards[i] && !guards[i].isTypeOf(arguments[i])) {
						throw new Error("Illegal argument type for parameter '" + paramNames[i]
							+ "'; expected type " + guards[i].typeName
							+ ", got value '" + arguments[i] + "' instead."
						);
					}
				}
				return func.apply(this, arguments);
			};
		} else if (guards.length == 0 && resultGuard !== undefined) {
			wrappedFunc = function() {
				var result = func.apply(this, arguments);
				if (!resultGuard.isTypeOf(result)) {
					throw new Error("Illegal result type; expected type "
						+ resultGuard.typeName + ", got value '" + result + "' instead."
					);
				}
				return result;
			};
		} else {
			// else nothing to guard after all
			lang.assert(guards.length == 0);
			lang.assert(resultGuard === undefined);
		}
	}
	wrappedFunc.attribs = attribs;
	return wrappedFunc;
};


lang.Type.any = function() {
	var elements = arguments;
	return {
		isTypeOf: function(that) {
			for (var type in elements) {
				if (type.isTypeOf(that)) return true;
			}
			return false;
		}
	};
};

lang.Type.all = function() {
	var elements = arguments;
	return {
		isTypeOf: function(that) {
			for (var type in elements) {
				if (!type.isTypeOf(that)) return false;
			}
			return true;
		}
	};
};

lang.Type.arrayOf = function(type) {
	return {
		isTypeOf: function(that) {
			for (var item in that) {
				if (!type.isTypeOf(item)) return false;
			}
			return true;
		}
	};
};

lang.Type.oneOf = function() {
	var values = arguments;
	return {
		isTypeOf: function(that) {
			for (var item in values) {
				if (item == that) return true;
			}
			return false;
		}
	};
};

lang.Type.optional = function(type) {
	return {
		isTypeOf: function(that) {
			return that == undefined || that == null || type.isTypeOf(that);
		}
	};
};

lang.Type.not = function(type) {
	return {
		isTypeOf: function(that) {
			return !type.isTypeOf(that);
		}
	};
};


var modules = new Object();
modules['http://www.ideanest.com/scripts/20040101/lang.js'] = lang;
this['http://www.ideanest.com/scripts/20040101/lang.js'] = lang;

lang.load = lang.lambda(
	{
		doc: "Load a JavaScript source code file from the given URI.  The code is\
			loaded in the background, and will almost certainly <em>not</em> have been\
			loaded by the time this function returns.",
		params: {
			uri: String,
		}
	},
	function(uri) {
		// check for DOM 2
		if (document && document.implementation && document.implementation.hasFeature) {
		
			var heads = document.getElementsByTagName('head');
			var head;
			if (heads.length == 0) {
				document.documentElement.insertBefore(document.createElement('head'), document.firstChild);
			}
			lang.assert(heads.length > 0);
			head = heads[0];
			
			var elem = document.createElement('script');
			elem.setAttribute('type', 'text/javascript');
			elem.setAttribute('src', uri);
			
			head.appendChild(elem);
		}
	}
);

var context = new Array();
function currentContext() {return context[context.length-1];}

lang.module = lang.lambda(
	{
		doc: "Either declare or import a module.  The first parameter is always the\
			URI of the module in question.  If there is a second parameter, it must be\
			a function that will define the module.  Otherwise, if there is only one\
			parameter, the system will attempt to resolve the module reference, loading\
			the module from the given URI if necessary.  In either case, the resolved\
			module is returned.  However, if it needed to be loaded, the module might\
			not yet be defined (filled out) when this function returns.",
		params: {
			uri: String,
			fun: lang.Type.optional(Function)
		}
	},
	function(uri, fun) {
		var mod = modules[uri];
		if (mod) {
			if (fun && mod.constructor != PendingModule) throw new Error("module " + uri + " already defined");
		} else {
			modules[uri] = mod = new PendingModule();
		}
		if (fun) {
			context.push(mod);
			try {
				fun.call(mod);
				mod.constructor = Module;
			} catch(e) {
				alert("module " + uri + "\nfailed to initialize:\n" + e);
				throw e;
			} finally {
				context.pop();
			}
		} else {
			lang.load(uri);
		}
		return mod;
	}
);


lang.aspect = function() {
	var attribs, func;
	switch(arguments.length) {
		case 1: attribs = null;         func = arguments[1]; break;
		case 2: attribs = arguments[1]; func = arguments[2]; break;
		default: throw new Error("invalid number of arguments to aspect maker: " + arguments.length);
	}
	
	func = lang.lambda(attribs, func);
	
	var wrappedConstructor = function() {
		context.push(this);
		try {
			func.apply(this, arguments);
		} finally {
			context.pop();
		}
	};
	
	wrappedConstructor.attribs = attribs;
	// TODO: add isTypeOf() function to the class to make it a Type
	
	return wrappedConstructor;
};

lang.aspect.isTypeOf = function(that) {
	// TODO: refine to only accept objects created through Class function
	return typeof(that) == 'function';
};


lang.method = function() {
	var attribs, func;
	switch(arguments.length) {
		case 1: attribs = null;         func = arguments[1]; break;
		case 2: attribs = arguments[1]; func = arguments[2]; break;
		default: throw new Error("invalid number of arguments to method: " + arguments.length);
	}
	
	var self = currentContext();
	func = lang.lambda(attribs, func);
	
	var wrappedMethod = function() {
		return func.apply(self, arguments);
	};
	
	wrappedMethod.attribs = attribs;
	return wrappedMethod;
		
};


lang.constructor = Module;

for (var entry in lang) {
	if (!this[entry]) this[entry] = lang[entry];
}

alert('lang init done');

}).call(this);

