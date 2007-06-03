module('http://www.ideanest.com/scripts/20040101/log.js', function() {

var lang = module('http://www.ideanest.com/scripts/20040101/lang.js');

var entries = [];
var levels = {};

method("setLogLevel", function(targetName, level) {
});

var Logger = aspect(function(log) {
	method("error",		function() {return log(3, arguments);});
	method("warning",	function() {return log(2, arguments);});
	method("info",			function() {return log(1, arguments);});
	method("debug",		function() {return log(0, arguments);});
});

lang.operation("makeLog", function() {
	var log = method(function() {
		var level, message, exception;
		switch(arguments.length) {
			case 0: assert(false, "no arguments provided to bound log method"); break;
			case 1: return levels[this.name] <= arguments[0];
			
	});
	return new Logger(log);
});

});
