/*
  Author: Oliver Steele
  Copyright: Copyright 2006 Oliver Steele.  All rights reserved.
  Download: http://osteele.com/sources/openlaszlo/simple-logging.js
  License: MIT License.

  This file defines +console.info+, +console.warn+, +console.error+, and
  +console.debug+ functions that are compatible with those defined by
  many HTTP user agents (for example, Firefox with Firebug; and Safari 3.0).
  This allows libraries that use these functions to be used in both
  OpenLaszlo programs and in browser JavaScript.
*/

/*
 * Modified by Piotr Kaminski.
 */

var console = {
    log: function() {Debug.write.apply(Debug, arguments);},
    info: function() {Debug.write.apply(Debug, arguments); return arguments[0]},
    debug: function() {Debug.write.apply(Debug, ['debug'].concat(arguments))},
    warn: function() {Debug.warn(arguments.length > 1 ? arguments.join(', ') : arguments[0])},
    error: function() {Debug.error(arguments.length > 1 ? arguments.join(', ') : arguments[0])},
	level: 0,
	group: function() {
		var prefix = "";
		for (var i = 0; i < this.level; i++) prefix += "  ";
		arguments.unshift(prefix);
		Debug.write.apply(Debug, arguments);
		this.level++;
	},
	groupEnd: function() {this.level--;}
}
