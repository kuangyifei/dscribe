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

        until: function (p) {
            return wrapTrace(function (s) {
                var qx = [], rx = null;
                while (s.length) { 
                    rx = p.call(this, s); 
                    if (rx == null) { 
                        qx.push(rx[0]); 
                        s = rx[1]; 
                        continue; 
                    }
                    break;
                }
                return [ qx, s ];
            });
        },
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
        not: function (p) {
            return wrapTrace(function (s) {
                var r = p.call(this, s);
                if (r == null) return [null, s];
                return null;
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
        all: function () { 
            var px = arguments, x = x; 
            return wrapTrace(o.each(o.optional(px))); 
        },

        // delimited operators
        sequence: function (px, d, c) {
            d = d || o.token(' ');
            c = c || null;
            
            if (px.length === 1) { 
                return px[0]; 
            }
            return wrapTrace(function (s) {
                var r = null, q = null;
                var rx = []; 
                for (var i = 0; i < px.length ; i++) {
                    r = px[i].call(this, s); 
                    if (r == null) break;
                    rx.push(r[0]);
                    q = d.call(this, r[1]); 
                    if (q == null) break; 
                    s = q[1];
                }
                if (!r || q) return null;
                if (c) {
                    r = c.call(this, r[1]);
                    if (r == null) return null;
                }
                return [ rx, (r?r[1]:s) ];
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
        set: function (px, d, c) {
            d = d || o.token(' '); 
            c = c || null;
            return wrapTrace(function (s) {
                // r is the current match, best the current 'best' match
                // which means it parsed the most amount of input
                var r = null, p = null, q = null, rx = null, best = [[], s], last = false;

                // go through the rules in the given set
                for (var i = 0; i < px.length ; i++) {

                    // last is a flag indicating whether this must be the last element
                    // if there is only 1 element, then it MUST be the last one
                    q = null; 
                    p = null; 
                    r = null; 
                    last = (px.length === 1); 

                    // first, we try simply to match the current pattern
                    // if not, try the next pattern
                    r = px[i].call(this, s);
                    if (r == null) continue; 

                    // since we are matching against a set of elements, the first
                    // thing to do is to add r[0] to matched elements
                    rx = [[r[0]], r[1]];

                    // if we matched and there is still input to parse and 
                    // we don't already know this is the last element,
                    // we're going to next check for the delimiter ...
                    // if there's none, or if there's no input left to parse
                    // than this must be the last element after all ...
                    if (r[1].length > 0 && ! last) {
                        q = d.call(this, r[1]); 
                        if (q == null) last = true; 
                    } else { 
                        last = true; 
                    }

				    // if we parsed the delimiter and now there's no more input,
				    // that means we shouldn't have parsed the delimiter at all
				    // so don't update r and mark this as the last element ...
                    if (!last && q[1].length === 0) { 
                        last = true; 
                    }


				    // so, if this isn't the last element, we're going to see if
				    // we can get any more matches from the remaining (unmatched)
				    // elements ...
                    if (!last) {

                        // build a list of the remaining rules we can match against,
                        // i.e., all but the one we just matched against
                        var qx = []; 
                        for (var j = 0; j < px.length ; j++) { 
                            if (i !== j) { 
                                qx.push(px[j]); 
                            }
                        }

                        // now invoke recursively set with the remaining input
                        // note that we don't include the closing delimiter ...
                        // we'll check for that ourselves at the end
                        p = o.set(qx, d).call(this, q[1]);
                        if (p == null) return null;

                        // if we got a non-empty set as a result ...
                        // (otw rx already contains everything we want to match)
                        if (p[0].length > 0) {
                            // update current result, which is stored in rx ...
                            // basically, pick up the remaining text from p[1]
                            // and concat the result from p[0] so that we don't
                            // get endless nesting ...
                            rx[0] = rx[0].concat(p[0]); 
                            rx[1] = p[1]; 
                        }
                    }

				    // at this point, rx either contains the last matched element
				    // or the entire matched set that starts with this element.

				    // now we just check to see if this variation is better than
				    // our best so far, in terms of how much of the input is parsed
                    if (rx[1].length < best[1].length) { 
                        best = rx; 
                    }

				    // if we've parsed all the input, then we're finished
                    if (best[1].length === 0) { 
                        break; 
                    }
                }

			    // so now we've either gone through all the patterns trying them
			    // as the initial match; or we found one that parsed the entire
			    // input string ...

			    // if best has no matches, just return empty set ...
                if (best[0].length === 0) { 
                    return best; 
                }

			    // if a closing delimiter is provided, then we have to check it also
                if (c) {
                    // we try this even if there is no remaining input because the pattern
                    // may well be optional or match empty input ...
                    q = c.call(this, best[1]); 
                    if (q == null) return null;
                    
                    // it parsed ... be sure to update the best match remaining input
                    best[1] = q[1];
                }

			    // if we're here, either there was no closing delimiter or we parsed it
			    // so now we have the best match; just return it!
                return best;
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
        },
        min: function (min, rule) {
            return wrapTrace(function (s) {
                var rx = rule.call(this, s);
                if (rx == null) return null;
                if (rx[0].length < min) return null;
                return rx;
            });
        }
    });
	

	// Generator Operators And Vector Operators

	// Generators are operators that have a signature of F(R) => R,
	// taking a given rule and returning another rule, such as 
	// ignore, which parses a given rule and throws away the result.

	// Vector operators are those that have a signature of F(R1,R2,...) => R,
	// take a list of rules and returning a new rule, such as each.

	// Generator operators are converted (via the following xgenerator
	// function) into functions that can also take a list or array of rules
	// and return an array of new rules as though the function had been
	// called on each rule in turn (which is what actually happens).

	// This allows generators to be used with vector operators more easily.
	// Example:
	// each(ignore(foo, bar)) instead of each(ignore(foo), ignore(bar))

	// This also turns generators into vector operators, which allows
	// constructs like:
	// not(cache(foo, bar))
	
    var xgenerator = function (op) {
        return function () {
            var args = null, rx = [];
            if (arguments.length > 1) {
                args = Array.prototype.slice.call(arguments);
            } else if (arguments[0] instanceof Array) {
                args = arguments[0];
            }
            if (args) { 
                for (var i = 0, px = args.shift() ; i < px.length ; i++) {
                    args.unshift(px[i]); 
                    rx.push(op.apply(null, args)); 
                    args.shift();
                    return rx;
                } 
            } else { 
                return op.apply(null, arguments); 
            }
        };
    };
    
    var gx = "optional not ignore cache".split(' ');
    
    for (var i = 0 ; i < gx.length ; i++) { 
        o[gx[i]] = xgenerator(o[gx[i]]); 
    }

    var xvector = function (op) {
        return function () {
            if (arguments[0] instanceof Array) { 
                return op.apply(null, arguments[0]); 
            } else { 
                return op.apply(null, arguments); 
            }
        };
    };
    
    var vx = "each any all".split(' ');
    
    for (var j = 0 ; j < vx.length ; j++) { 
        o[vx[j]] = xvector(o[vx[j]]); 
    }
	
}());
