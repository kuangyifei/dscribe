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

var Parsing = {};
(function () {

	Parsing.eval = function(fn, s) {
		var stack = [[fn, s]];
		var lastResult = null;
		while (stack.length) {
			var topCall = stack.pop();
			var r = topCall[0].call(null, topCall[1] === null ? lastResult : topCall[1]);
			if (r === null || r.length === 2) {
				lastResult = r;
			} else {
				stack.push([r[2], null]);
				stack.push([r[0], r[1]]);
			}
		}
		return lastResult;
	};
	    
    Parsing.Operators = {
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

        many: function(p) {
            return function(s) {
                var rx = [];
            	if (!s.length) return [rx, s];
                function cont(r) {
                	if (!r) return [rx, s];
                	rx.push(r[0]);
                	s = r[1];
                	if (!s.length) return [rx, s];
                	return [p, s, cont];
                }
                return [p, s, cont];
            };
        },

        optional: function(p) {
            return function(s) {
            	return [p, s, function(r) {
            		return r || [null, s];
            	}];
            };
        },
        
        ignore: function(p) {
            return function(s) { 
                return [p, s, function(r) {
                	if (!r) return null;
                	return [null, r[1]];
                }];
            };
        },
    	  
        any: function() {
            var px = arguments;
            return function(s) {
            	if (!px.length) return null;
            	var i = 0;
            	function cont(r) {
            		return r ? r : (++i < px.length ? [px[i], s, cont] : null);
            	}
            	return [px[0], s, cont];
            };
        },
        
        each: function() { 
            var px = arguments;
            return function(s) {
                var rx = [], i = 0;
            	if (!px.length) return [rx, s];
            	function cont(r) {
            		if (!r) return null;
            		rx.push(r[0]);
            		s = r[1];
            		return ++i < px.length ? [px[i], s, cont] : [rx, s];
            	}
            	return [px[0], s, cont];
            };
        },

	    //
	    // Composite Operators
	    //
    		
        between: function(d1, p, d2) { 
            d2 = d2 || d1; 
            var xfn = Parsing.Operators.each(Parsing.Operators.ignore(d1), p, Parsing.Operators.ignore(d2));
            return function(s) {
            	return [xfn, s, function(r) {
            		return r ? [r[0][1], r[1]] : null;
            	}];
            };
        },
        
        list: function(p, d) {
            d = d || Parsing.Operators.token(' ');  
            return function(s) {
            	var rx = [];
            	var tail;
            	function cont1(r) {
            		return r ? [p, r[1], cont2] : [rx, tail];
            	}
            	function cont2(r) {
            		if (!r) return [rx, tail];
            		rx.push(r[0]);
            		tail = r[1];
            		return [d, tail, cont1];
            	}
            	return [p, s, function(r) {
            		if (!r) return null;
            		return cont2(r);
            	}];
            };
        },
        
        forward: function(gr, fname) {
            return function(s) {
                return gr[fname].call(this, s);
            };
        },

        //
        // Translation Operators
        //
        
        replace: function(rule, repl) {
            return function(s) {
            	return [rule, s, function(r) {
            		return r ? [repl, r[1]] : null;
            	}];
            };
        },
        
        process: function(rule, fn) {
            return function(s) {
            	return [rule, s, function(r) {
            		return r ? [fn.call(this, r[0]), r[1]] : null;
            	}];
            };
        }
    };
    
}());
