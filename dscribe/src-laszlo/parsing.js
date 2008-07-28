// Laszlo Parsing Library
// modified by Piotr Kaminski, piotr@ideanest.com
// based on:
// 
// JavaScript Parsing Library
// (c) 2007 Dan Yoder, All Rights Reserved
// Version: 0.5, License: MIT
// Web: http://code.google.com/p/cruiser/wiki/Parsing
// Email: dan@zeraweb.com
// 

console = console || {
	error: function() {Debug.error.apply(Debug, arguments);},
	log: function() {Debug.write.apply(Debug, arguments);},
	level: 0,
	group: function() {
		var prefix = "";
		for (var i = 0; i < this.level; i++) prefix += "  ";
		arguments.unshift(prefix);
		Debug.write.apply(Debug, arguments);
		this.level++;
	},
	groupEnd: function() {this.level--;}
};

var Parsing = {};
(function () {
    var wrapTrace = function(fn) {
    	if (!Parsing.Operators.Trace) return fn;
    	return function(s) {
    		for (var key in this) {
    			if (this[key] == arguments.callee) {
    				console.group(key + ":", s);
    				var r = fn.call(this, s);
   					console.groupEnd();
   					return r;
    			}
    		}
    		return fn.call(this, s);
    	};
    };
    
    var $P = Parsing; 
    var o = ($P.Operators = {
        //
        // Tokenizers
        //
    	
   		token: function() {
    		var samples = arguments;
    		var samplesDelimiting = new Array(samples.length);
    		for (var k = 0; k < samples.length; k++) {
    			var c = samples[k].charAt(0);
    			if (c == ' ') {
    				samples[k] = '';
    				samplesDelimiting[k] = false;
    			} else {
    				samplesDelimiting[k] = !(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z');
    			}
    		}
    		return function(s) {
    			var i;
    			for (i = 0; i < s.length && " \n\t".indexOf(s.charAt(i)) != -1; i++) {}
    			for (var j = 0; j < samples.length; j++) {
    				var sample = samples[j];
    				var delimiting = samplesDelimiting[j];
    				if (!delimiting && i == 0) continue;
    				if (sample == s.slice(i, i + sample.length)) {
    					var tail = s.slice(i + sample.length);
    					if (delimiting && tail.length > 0 && " \n\t".indexOf(tail.charAt(0)) == -1) {
    						tail = " " + tail;
    					}
    					return [sample, tail];
    				}
    			}
   				return null;
    		};
    	},
    	
    	dfsatoken: function(delimiting, dfsa, defaultTransitions, finalStates) {
    		return function(s) {
    			var j;
    			for (j = 0; j < s.length && " \n\t".indexOf(s.charAt(j)) != -1; j++) {}
    			if (!delimiting && j == 0) return null;
    			var i = j, state = "start", transitionFound = true;
    			while (i < s.length && transitionFound) {
    				var c = s.charAt(i);
    				var transitions = dfsa[state];
    				transitionFound = false;
    				for (var key in transitions) {
    					if (key.indexOf(c) != -1) {
    						state = transitions[key];
    						i++;
    						transitionFound = true;
    						break;
    					}
    				}
    				if (!transitionFound && state in defaultTransitions) {
    					state = defaultTransitions[state];
    					i++;
    					transitionFound = true;
    				}
    			}
    			if (state in finalStates) {
					var tail = s.slice(i);
					if (delimiting && tail.length > 0 && " \n\t".indexOf(tail.charAt(0)) == -1) {
						tail = " " + tail;
					}
    				return [s.slice(j, i), tail];
    			} else {
    				return null;
    			}
    		};
    	},
    	
        //
        // Atomic Operators
        // 

        many: function (p) {
            return wrapTrace(function (s) {
                var rx = [], r = null; 
                while (s.length) { 
                    r = p.call(this, s); 
                    if (r == null) return [ rx, s ]; 
                    rx.push(r[0]); 
                    s = r[1];
                }
                return [ rx, s ];
            });
        },

        // generator operators -- see below
        optional: function (p) {
            return wrapTrace(function (s) {
                var r = p.call(this, s); 
                if (r == null) return [ null, s ]; 
                return [ r[0], r[1] ];
            });
        },
        ignore: function (p) {
            return p ? 
            wrapTrace(function (s) { 
                var r = p.call(this, s);
                if (r == null) return null;
                return [null, r[1]]; 
            }) : null;
        },
        cache: function (rule) { 
            var cache = {}; 
            return wrapTrace(function (s) {
                return cache[s] = (cache[s] || rule.call(this, s)); 
            });
        },
    	  
        // vector operators -- see below
        any: function () {
            var px = arguments;
            return wrapTrace(function (s) { 
                var r = null;
                for (var i = 0; i < px.length; i++) { 
                    if (px[i] === null) continue; 
                    r = (px[i].call(this, s)); 
                    if (r) return r; 
                } 
                return null;
            });
        },
        each: function () { 
            var px = arguments;
            return wrapTrace(function (s) { 
                var rx = [], r = null;
                for (var i = 0; i < px.length ; i++) { 
                    if (px[i] === null) continue; 
                    r = (px[i].call(this, s)); 
                    if (r == null) return null;
                    rx.push(r[0]); 
                    s = r[1];
                }
                return [ rx, s]; 
            });
        },

	    //
	    // Composite Operators
	    //
    		
        between: function (d1, p, d2) { 
            d2 = d2 || d1; 
            var xfn = o.each(o.ignore(d1), p, o.ignore(d2));
            return wrapTrace(function (s) { 
                var rx = xfn.call(this, s);
                if (rx == null) return null;
                return [ rx[0][1], rx[1] ]; 
            });
        },
        list: function (p, d, c) {
            d = d || o.token(' ');  
            c = c || null;
            return wrapTrace(function(s) {
            	var r = [];
            	var tail;
            	var rx = p.call(this, s);
            	if (rx == null) return null;
            	while(true) {
            		r.push(rx[0]);
            		tail = rx[1];
            		rx = d.call(this, tail);
            		if (rx == null) break;
            		rx = p.call(this, rx[1]);
            		if (rx == null) break;
            	}
            	if (c) {
            		rx = c.call(this, tail);
            		if (rx == null) return null;
            		tail = rx[1];
            	}
            	return [r, tail];
            });
        },
        forward: function (gr, fname) {
            return wrapTrace(function (s) { 
                return gr[fname].call(this, s); 
            });
        },

        //
        // Translation Operators
        //
        replace: function (rule, repl) {
            return wrapTrace(function (s) { 
                var r = rule.call(this, s);
                if (r == null) return null;
                return [repl, r[1]]; 
            });
        },
        process: function (rule, fn) {
            return wrapTrace(function (s) {  
                var r = rule.call(this, s);
                if (r == null) return null;
                return [fn.call(this, r[0]), r[1]]; 
            });
        }
    });
    
}());
