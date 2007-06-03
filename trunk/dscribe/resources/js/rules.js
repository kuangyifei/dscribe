module("http://ideanest.com/reef/js/rules.js", function() {

var ruleClasses = {};

var prefixBindings = {
	rules:	"http://ideanest.com/reef/ns/rules",
	uml:	"http://ideanest.com/reef/ns/uml",
	java:	"http://ideanest.com/reef/ns/java"
};

operation("rule", function(name, maker) {
	if (ruleClasses[name]) throw new Error("Rule named " + name + " already defined");
	var ruleClass = aspect(name, function(rule, space) {
		maker.apply(this);
		this.Rule.mixin(rule, space);
	});
	ruleClasses[name] = ruleClass;
	return ruleClass;
});

aspect("Rule", function(def, space) {

	require(this.execute);
	require(this.prune);

	var id = def.single("@xml:id").value();
	var changed = false;
	
	this.execute.after(function(args, result) {
		result = result || changed;
		changed = false;
		return result;
	});
	
	var Transformer = aspect(function(sources) {
		
		var sourceIds = [];
		var alreadyApplied = false;
		
		var keys = space.query()
			.let("$sources", sources)
			.let("$id", id)
			.unordered(
				"for $key in distinct-values($sources/rules:mod[@rule=$id]/@key) " + 
				"return " +
				"if (" +
				"	(every $source in $sources satisfies exists(rules:mod[@rule=$id and @key=$key])) " +
				"	and count(//rules:mod[@rule=$id and @key=$key])=count($sources)" + 
				") then $key else ()");
				
		switch(keys.length) {
			case 0:	alreadyApplied = false; break;
			case 1:	alreadyApplied = true; break;
			default:		// multiple keys match, should not happen
				// TODO: log error
				alreadyApplied = true;
		}
		
		if (alreadyApplied) {
		
			method("append", function(){});
			method("remove", function(){});
			method("removeSources", function(){});
			method("change", function(){});
			
		} else {
		
			changed = true;
		
			for (var i=0; i<sources.length; i++) {
				var sourceId = sources[i].query().single("@xml:id").value();
				if (sourceId == null) {
					// TODO: log error, all sources should have an id
				} else {
					sourceIds.push(sourceId);
				}
			}
				
			method("append", function(targetNode, builderBlock) {
				var builder = targetNode.append();
				var firstCallCaptured = false;
				builder.elem.after(function() {
					if (!firstCallCaptured) {
						firstCallCaptured = true;
						builder.elem("rules:mod").attr("rule", id).attr("key", key).attr("action", "create");
						for (var i=0; i<sourceIds.length; i++) builder.elem("rules:derived").attr("from", sourceIds[i]).end("rules:derived");
						builder.end("rules:mod");
					}
				});
				builderBlock.call(this, builder);
				builder.commit();
				return this;
			});
			
			method("remove", function() {
				for (var i=0; i<arguments.length; i++) {
					var target = arguments[i];
					target.update().attr("display", "none").commit();
					target.append().elem("rules:mod").attr("rule", id).attr("key", key).attr("action", "use").end("rules:mod").commit();
				}
				return this;
			});
			
			method("removeSources", function() {
				this.remove.apply(this, sources);
			});
			
			method("change", function() {
				// TODO: fill in
			});
		}
		
	});
	
	method("transform", function() {
		return new Transformer(arguments);
	});
	
	this.space = space;
	this.def = def;

});

operation("applyRules", function(ruleList, space) {
	for (var key in prefixBindings) {
		space.namespaceBindings().put(key, prefixBindings[key]);
	}

	var rules = [];
	ruleList.each(function(def) {
		try {
			rules.push(new ruleClasses[def.query().single("@type").value()](def, space));
		} catch (e) {
			// TODO: log error, continue?
		}
	});
	do {
		var changed = false;
		for (var i=0; i<rules.length; i++) changed |= rules[i].execute();
	} while (changed);
});

});
