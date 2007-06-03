module('http://www.ideanest.com/scripts/20040101/util.js', function() {

Array.prototype.each = function(f) {
	for (var i=0; i<this.length; i++) f(this[i]);
};

module('http://www.ideanest.com/scripts/20040101/tunnel.js').mixin();
module('http://www.ideanest.com/scripts/20040101/log.js').mixin();


});
