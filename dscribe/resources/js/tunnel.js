module('http://www.ideanest.com/scripts/20040101/tunnel.js', function() {

// markers
var read = {}, write = {}, empty = {};

var counter = 1;

aspect("Tunnel", function() {
	var deaddrop;
	var action;		// needed to avoid implicit control through deaddrop state, which could be abused
	var name = "#tunnel_" + counter++;
	
	aspect("Terminal", function(initialValue) {
		var vault = initialValue;
		this[name] = method({facets: ["public"]}, function() {
			if (action === read) {
				deaddrop = vault;
			} else if (action === write) {
				vault = deaddrop;
				deaddrop = empty;
			} 
		});
	});
	
	method("get", function(target) {
		if (!target[name]) throw new Error("tunnel terminal not present on target");
		deaddrop = empty;
		action = read;
		target[name]();
		action = null;
		if (deaddrop === empty) throw new Error("supposed terminal not connected to tunnel");
		return deaddrop;
	});
	
	method("set", function(target, value) {
		if (!target[name]) throw new Error("tunnel terminal not present on target");
		deaddrop = value;
		action = write;
		target[name]();
		action = null;
		if (deaddrop !== empty) throw new Error("supposed terminal not connected to tunnel");
	});
});

});