/* Copyright 2007 by Oliver Steele.  All rights reserved. */

/*
 * String utilities
 */

String.prototype.capitalize = function() {
    return this.slice(0,1).toUpperCase() + this.slice(1);
}

String.prototype.escapeHTML = function() {
    return (this.replace('&', '&amp;')
            .replace('<', '&lt;')
            .replace('>', '&gt;')
            .replace('"', '&quot;'));
}

String.prototype.pluralize = function(count) {
    if (arguments.length && count == 1)
        return this;
    return this+'s';
}

String.prototype.startsWith = function(prefix) {
    return this.indexOf(prefix) == 0;
}

String.prototype.endsWith = function(suffix) {
    return this.lastIndexOf(suffix) == this.length - suffix.length;
}

String.prototype.strip = function() {
    var i, j, ws = " \t\n\r";
    for (j = this.length; --j >= 0 && ws.indexOf(this.charAt(j)) >= 0; )
        ;
    for (i = 0; i < j && ws.indexOf(this.charAt(i)) >= 0; i++)
        ;
    return 0 == i && j == this.length-1 ? this : this.slice(i, j+1);
}

String.prototype.truncate = function(length, ellipsis) {
    return (this.length <= length
            ? this
            : this.slice(0, length) + (ellipsis||''));
}

if (!String.prototype.replace || 'aa'.replace('a', 'b') != 'bb') {
	String.prototype.replace =  function(pattern, sub) {
	    var splits = this.split(pattern),
	        segments = new Array(splits.length*2-1);
	    for (var i = 0, dst = 0; i < splits.length; i++) {
	        i && (segments[dst++] = sub);
	        segments[dst++] = splits[i];
	    }
	    return segments.join('');
	};
}