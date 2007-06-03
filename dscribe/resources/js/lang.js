// check if lang module is already defined, and if so silently skip re-defining it
if (!this['http://www.ideanest.com/scripts/20040101/lang.js'])

(function() {

var PendingModule = function () {};
var Module = function() {};

var lang = new PendingModule();
var modules = {'http://www.ideanest.com/scripts/20040101/lang.js': lang};
this['http://www.ideanest.com/scripts/20040101/lang.js'] = lang;		// marker to avoid accidental redefinition

lang.operation = function() {
	var name, attribs, baseFunc;
	switch(arguments.length) {
		case 1: name = null;						attribs = {};							baseFunc = arguments[0]; break;
		case 2:
			if (typeof(arguments[0]) == "string") {
				name = arguments[0];			attribs = {};
			} else {
				name = null;								attribs = arguments[0];
			}
																													baseFunc = arguments[1]; break;
		case 3: name = arguments[0];		attribs = arguments[1];		baseFunc = arguments[2]; break;
		default: throw new Error("lambda needs 1, 2 or 3 arguments, got " + arguments.length);
	}

	var advice;
	
	var proceed = function(i) {
		if (i < advice.length) return function() {return advice[i].call(this, arguments, proceed(i+1));};
		else return function() {return baseFunc.apply(this, arguments);};
	};
	
	var wrappedFunc = function() {
		if (!advice) return baseFunc.apply(this, arguments);
		var result;
		result = proceed(0).apply(this, arguments);
		return result;
	};
	
	wrappedFunc.around = function(adviceFunc) {
		if (!advice) advice = [];
		advice.push(adviceFunc);
	};
	wrappedFunc.before = function(adviceFunc) {
		wrappedFunc.around(function(args, proceed) {
			adviceFunc.call(this, args);
			return proceed.apply(this, args);
		});
	};
	wrappedFunc.after = function(adviceFunc) {
		wrappedFunc.around(function(args, proceed) {
			return adviceFunc.call(this, args, proceed.apply(this, args));
		});
	};

	wrappedFunc.attribs = attribs;
	if (name) {
		if (context.length == 0) {
			wrappedFunc.name = name;
		} else {
			var c = currentContext();
			wrappedFunc.name = c.name ? c.name + (c.name.indexOf('#') == -1 ? '#' : '.') + name : name;
			if (c[name] === undefined) c[name] = wrappedFunc;
			else throw new Error("member named '" + name + "' already defined in current context");
		}
	}
	return wrappedFunc;
};

var context = new Array();
function currentContext() {
	if (context.length == 0) throw new Error("no context; you must define or instantiate your object within a module or an aspect constructor");
	return context[context.length-1];
}

context.push(lang);


lang.method = function() {
	var self = currentContext();
	var func = lang.operation.apply(this, arguments);
	func.around(function(args, proceed) {return proceed.apply(self, args);});
	return func;
};

//  bootstrap wrapper on operation/method, so they're advisable
lang.operation = lang.method(lang.operation);
lang.method = lang.method({facets: ["keyword"]}, lang.method);


lang.method("assert", {facets: ["keyword"]}, function(expr, msg) {
	if (!expr) throw new Error(msg ? msg : "assertion failed");
});


lang.method("load",
	{
		doc: "Load a JavaScript source code file from the given URI.  The code is\
			loaded in the background, and will almost certainly <em>not</em> have been\
			loaded by the time this function returns."
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

lang.method("module",
	{
		doc: "Either declare or import a module.  The first parameter is always the\
			URI of the module in question.  If there is a second parameter, it must be\
			a function that will define the module.  Otherwise, if there is only one\
			parameter, the system will attempt to resolve the module reference, loading\
			the module from the given URI if necessary.  In either case, the resolved\
			module is returned.  However, if it needed to be loaded, the module might\
			not yet be defined (filled out) when this function returns.",
		facets: ["keyword"]
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
				mod.unload = lang.method(function() {delete modules[uri];});
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
		mod.mixin = lang.operation(function() {
			var c = currentContext();
			for (var entry in mod) if (!c[entry]) c[entry] = mod[entry];
		});
		mod.name = uri;
		return mod;
	}
);


lang.method("aspect", {facets: ["keyword"]}, function() {
	var func = lang.operation.apply(this, arguments);
	func.around(function(args, proceed) {
		context.push(this);
		try {return proceed.apply(this, args);}
		finally {context.pop();}
	});
	
	func.mixin = lang.operation(function() {
		func.apply(currentContext(), arguments);
	});
	return func;
});


lang.method("require", function(member) {
	lang.assert(member && typeof(member) == "function", "host missing required method");
});


lang.aspect("Faceted", function() {
	this.facet = lang.method({facets: ["public"]}, function() {
		var all = arguments.length == 0;
		var wanted = {"public": true};
		for (var i=0; i<arguments.length; i++) wanted[arguments[i]] = true;
		var result = {};
		for (var member in this) {
			if (this[member].attribs && this[member].attribs.facets) {
				var facets = this[member].attribs.facets;
				for (var i=0; i<facets.length; i++) if (all || wanted[facets[i]]) result[member] = this[member];
			}
		}
		return result;
	});
});

lang.aspect.after(function(args, result) {
	result.before(function(args) {
		lang.Faceted.mixin();
	});
	return result;
});

lang.Faceted.mixin();

// done building module
context.pop();
lang.constructor = Module;

// promote keywords to global context
for (var entry in lang.facet("keyword")) if (entry != "facet" && !this[entry]) this[entry] = lang[entry];

alert('lang init done');

}).call(this);

