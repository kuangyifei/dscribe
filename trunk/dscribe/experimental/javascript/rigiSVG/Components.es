
/************************************************************************
*                                                                       *
*			     Components					*
*                                                                       *
*************************************************************************/

// core.es :: transformFromScreenSpace
// core.es :: getTransformToViewportSpace
// core.es :: getTransformToElement
// core.es :: SVGRect_multiply
// core.es :: getFontHeight
// core.es :: viewport

function inherit (parent, child)
{
    child.prototype = new parent();
    child.prototype.constructor = child;
    child.superclass = parent.prototype;
}

///////////////////////
//  ComponentEvent
//////////////////////

/**
 * Components return either SVGEvents or ComponentEvents
 */
function ComponentEvent ()
{
    this.init();
}

ComponentEvent.prototype.init = function ()
{
    this.target = null; 
    this.type = "";

};

ComponentEvent.prototype.getTarget = function ()
{
    return this.target;
};

///////////////////////
//  Insets 
//////////////////////

function Insets (b, t, l, r)
{
    this.bottom	= b;
    this.top	= t;
    this.left	= l;
    this.right	= r;
}

Insets.prototype.set = function (insets)
{
    this.top	= insets.top;
    this.bottom	= insets.bottom;
    this.left	= insets.left;
    this.right	= insets.right;
};
///////////////////////
//  GridLayout
//////////////////////


/* 
   The container is divided into equal-sized rectangles, and one component is
   placed in each rectangle. Each component is resized to fit in the
   rectangle. 
   defaults to filling 'down' the rows, so if rows and cols are both > 0 then
   cols is ignored
   value <= 0  for rows or cols means unlimited
 */
function GridLayout (rows, cols, vgap, hgap) 
{
    /* default to vertical layout */
    this.rows = rows || 0;
    this.cols = cols || 1;
    this.vgap = vgap || 10;
    this.hgap = hgap || 10;

};

/* lays out the specified container using this layout */
/* default to unlimited rows */
GridLayout.prototype.layoutContainer = function (parent) 
{
    var insets = parent.insets;
    var ncomponents = parent.itemlist.length;
    var nrows = this.rows;
    var ncols = this.cols;

    if (ncomponents == 0) {
	return;
    }

    /* set cols and rows treated as set rows, unlimited cols;
       unset cols and rows treated as single col, unlimited rows */
    if (nrows > 0 && ncols > 0) { 
	ncols = 0;
    } else if (nrows <= 0 && ncols <= 0) {
	ncols = 1;
	nrows = 0;
    }

    if (nrows > 0 && ncols <= 0) { 
	ncols = Math.ceil (ncomponents / nrows);
    } else if (nrows <= 0 && ncols > 0) {
	nrows = Math.ceil (ncomponents / ncols);
    }

    var parentbbox = parent.getBBox();
    var w = parentbbox.width - (insets.left + insets.right);
    var h = parentbbox.height - (insets.top + insets.bottom);
    w = (w - (ncols - 1) * this.hgap) / ncols;
    h = (h - (nrows - 1) * this.vgap) / nrows;

    for (var c = 0, x = insets.left ; c < ncols ; c++, x += w + this.hgap) {
	for (var r = 0, y = insets.top ; r < nrows ; r++, y += h + this.vgap) {
	    var i = r * ncols + c;
	    if (i < ncomponents) {
		parent.setItemBounds(i, x, y, h, w);
	    }
	}
    }
};

/* returns a rect where x,y are the same as parent.getBBox() 
   and height and width reflect the minimum layout size using this layout
   given that each subcomponent remains at it's current size */
GridLayout.prototype.minimumLayoutBounds = function (parent) 
{
    var insets = parent.insets;
    var ncomponents = parent.itemlist.length;
    var nrows = this.rows;
    var ncols = this.cols;

    var w = 0;
    var h = 0;

    if (ncomponents == 0) {
	nrows = 0;
	ncols = 0;
    } else {
	/* set cols and rows treated as set rows, unlimited cols;
	   unset cols and rows treated as single col, unlimited rows */
	if (nrows > 0 && ncols > 0) { 
	    ncols = 0;
	} else if (nrows <= 0 && ncols <= 0) {
	    ncols = 1;
	    nrows = 0;
	}
	
	if (nrows > 0 && ncols <= 0) { 
	    ncols = Math.ceil (ncomponents / nrows);
	} else if (nrows <= 0 && ncols > 0) {
	    nrows = Math.ceil (ncomponents / ncols);
	}

	for (var i in parent.itemlist) {
	    var comp = parent.getComponent(i);
	    var bbox = comp.getBBox();
	    w = Math.max(bbox.width, w);
	    h = Math.max(bbox.height, h);
	}
    }

    var bbox = parent.getBBox();
    bbox.height = insets.top + insets.bottom + nrows*h + (nrows-1)*this.vgap;
    bbox.width = insets.left + insets.right + ncols*w + (ncols-1)*this.hgap;
    return bbox;
}

///////////////////////
//  CellLayout 
//////////////////////

/* 
   Somewhat similar to GridLayout with one critical difference: components are
   not resized but only centered in the rectangles.  Furthermore, rectangle
   sizes are determined for each column by the largest component in each
   column, and similarly for each row.
 */

CellLayout.HALIGN_LEFT = 1;
CellLayout.HALIGN_CENTER = 0;
//CellLayout.HALIGN_RIGHT = 2;
CellLayout.VALIGN_TOP = 3;
CellLayout.VALIGN_CENTER = 4;
//CellLayout.VALIGN_BOTTOM= 5;

function CellLayout (rows, cols, vgap, hgap) 
{
    /* default to vertical layout */
    this.rows = rows || 0;
    this.cols = cols || 0;
    this.vgap = vgap || 1;
    this.hgap = hgap || 1;

    this.FIT_TO_WIDTH = true;
    this.FIT_TO_HEIGHT = true;
    this.HALIGN = CellLayout.HALIGN_LEFT;
    this.VALIGN = CellLayout.VALIGN_CENTER;
};

/* lays out the specified container using this layout */
/* default to unlimited rows */
CellLayout.prototype.layoutContainer = function (parent) 
{
    var insets = parent.insets;
    var ncomponents = parent.itemlist.length;
    var nrows = this.rows;
    var ncols = this.cols;

    if (ncomponents == 0) {
	return;
    }

    /* set cols and rows treated as set rows, unlimited cols;
       unset cols and rows treated as single col, unlimited rows */
    if (nrows > 0 && ncols > 0) { 
	ncols = 0;
    } else if (nrows <= 0 && ncols <= 0) {
	ncols = 1;
	nrows = 0;
    }
    
    if (nrows > 0 && ncols <= 0) { 
	ncols = Math.ceil (ncomponents / nrows);
    } else if (nrows <= 0 && ncols > 0) {
	nrows = Math.ceil (ncomponents / ncols);
    }

    /* find the width of each column and height of each row */
    var w = new Array(); //column width
    var h = new Array(); //row height

    for (var c = 0 ; c < ncols ; c++) {
	for (var r = 0 ; r < nrows ; r++) {
	    w[c] = 0;
	    h[r] = 0;
	}
    }

    for (var c = 0 ; c < ncols ; c++ ) {
	for (var r = 0 ; r < nrows ; r++ ) {
	    var i = r * ncols + c;
	    if (i < ncomponents) {
		var bbox = parent.getItemBBox(i);
		w[c] = Math.max(w[c], bbox.width);
		h[r] = Math.max(h[r], bbox.height);
	    } else { 
		continue;
	    }
	}
    }


    /* layout */
    for (var c = 0, x = insets.left ; c < ncols ; x += w[c] + this.hgap, c++ ) {
	for (var r = 0, y = insets.top ; r < nrows ;y += h[r] + this.vgap, r++ ) {
	    var i = r * ncols + c;
	    if (i < ncomponents) {
		var bbox = parent.getItemBBox(i);

		var fx = x;
		var fy = y;

		if (this.HALIGN == CellLayout.HALIGN_CENTER) {	
		    var fx = (w[c] - bbox.width) / 2 + x;   // center x
		} 
		if (this.VALIGN == CellLayout.VALIGN_CENTER) {	
		    var fy = (h[r] - bbox.height) / 2 + y;	// center y
		}

		var width = this.FIT_TO_WIDTH ? w[c] : null;
		var height = this.FIT_TO_HEIGHT ? h[r] : null;
		parent.setItemBounds(i, fx, fy, height, width);
	    } else {
		continue;
	    }
	}	
    }
};

/* returns a rect where x,y are the same as parent.getBBox() 
   and height and width reflect the minimum layout size using this layout
   given that each subcomponent remains at it's current size */
CellLayout.prototype.minimumLayoutBounds = function (parent) 
{
    var insets = parent.insets;
    var ncomponents = parent.itemlist.length;
    var nrows = this.rows;
    var ncols = this.cols;


    /* total width and height (without v/h gaps or insets) */
    var t_h = 0;
    var t_w = 0;

    if (ncomponents == 0) {
	nrows = 0;
	ncols = 0;
    } else {

	/* set cols and rows treated as set rows, unlimited cols;
	   unset cols and rows treated as single col, unlimited rows */
	if (nrows > 0 && ncols > 0) { 
	    ncols = 0;
	} else if (nrows <= 0 && ncols <= 0) {
	    ncols = 1;
	    nrows = 0;
	}

	if (nrows > 0 && ncols <= 0) { 
	    ncols = Math.ceil (ncomponents / nrows);
	} else if (nrows <= 0 && ncols > 0) {
	    nrows = Math.ceil (ncomponents / ncols);
	}

	/* find the width of each column and height of each row */
	var w = new Array(); //column width
	var h = new Array(); //row height

	for (var c = 0 ; c < ncols ; c++) {
	    for (var r = 0 ; r < nrows ; r++) {
		w[c] = 0;
		h[r] = 0;
	    }
	}

	for (var c = 0 ; c < ncols ; c++ ) {
	    for (var r = 0 ; r < nrows ; r++ ) {
		var i = r * ncols + c;
		if (i < ncomponents) {
		    var bbox = parent.getItemBBox(i);
		    w[c] = Math.max(w[c], bbox.width);
		    h[r] = Math.max(h[r], bbox.height);
		} else { 
		    continue;
		}
	    }
	}


	/* calculate total width and height (without v/h gaps or insets) */
	for (var c = 0; c < ncols ; c++) {
	    t_w += w[c];
	}
	for (var r = 0; r < nrows ; r++) {
	    t_h += h[r];
	}
    }

    var bbox = parent.getBBox();
    bbox.height = insets.top + insets.bottom + t_h + (nrows-1)*this.vgap;
    bbox.width = insets.left + insets.right + t_w + (ncols-1)*this.hgap;
    return bbox;
}

///////////////////
//  Container
//////////////////

/*
   Container
    * has a visual representation on the screen
    * can interact with the user
    * can contain other components or SVG elements
    * has the following minimal SVG representation (without ids)
	<g id="container" style="clip-path:...">
	    <clipPath />
		<shape />
	    </clipPath>
	    <shape id="background" />
	    <g id="items">
	    </g>
	</g>
    * guarantee that after layout the component is aligned so that all visible
	elements are in the postive x and y quadrant.
    * has the property 'isComponent' which is equal to the boolean 'true'
    * clips to its bounds
    * assumes that svgdoc is global;
    * 

*/
Container.lastID = 0;

/* provide a unique string ID for each instance.. this is not thread-safe
 * */
Container.newID = function () 
{
    Container.lastID++;
    return "" + Container.lastID;
};

function Container (parent)
{
    if (arguments.length > 0) {
	this.init(parent);
    }
}

Container.prototype.init = function (parent) 
{

    this.isComponent = true;
    this.id = Container.newID();

    this.insets = new Insets (2,2,2,2);

    /* SVGRect representing the x,y,h,w of this container relative to its
     * parent */
    this._bounds = svgdoc.documentElement.createSVGRect();  
    this.layoutManager = new CellLayout();

    this.listeners = new Object();
    this.listeners['selection'] = new Array();

    this.itemlist = new Array(); 

    /* create */
    this.container = svgdoc.createElementNS(svgns, "g");
    this.clipPath = svgdoc.createElementNS(svgns, "clipPath");
    this.background = svgdoc.createElementNS(svgns, "rect");
    this.items = svgdoc.createElementNS(svgns, "g");

    /* position */
    this.background.setAttributeNS(null, "x", 0); 
    this.background.setAttributeNS(null, "y", 0); 

    /* attach */
    
    this.clipPath.setAttributeNS(null, "id", this.id + 'clipPath'); 

    this.container.setAttributeNS(null, 'style', 'clip-path:url(#' +
		this.clipPath.id + ');');

    this.clip = this.background.cloneNode(false);
    this.clipPath.appendChild(this.clip);

    this.container.appendChild(this.clipPath);
    this.container.appendChild(this.background);
    this.container.appendChild(this.items);

    /* style */
    this.container.setAttributeNS(null, "display", "inherit");
    this.background.setAttributeNS(null, "style", 
	    'fill:white;'); 

    /* events */
    // take clicks as selections.
    this.container.addEventListener('click', this, false);

    if (parent) {
	parent.appendChild(this.container);
    }

}

/**
 *
 */
Container.prototype.handleEvent = function (evt) 
{
    var target = evt.getTarget();
    var type = evt.type;

    switch (type) {
	case 'click':
	    var event = new ComponentEvent();
	    event.type = 'selection';
	    event.target = this;
	    this.dispatchEvent(event);
	    break;
	default:
	    break;
    }
};

/**
 *
 */
Container.prototype.dispatchEvent = function (event) 
{
    type = event.type;

    for (var i in this.listeners[type]) {
	var listener = this.listeners[type][i];

	switch (typeof listener) {
	    case "object" : listener.handleEvent(event); break;
	    case "function" : listener(event); break;
	    default: 
	    //TODO remove from list ?
	    break;
	}
    }
};

/**
 *
 */
Container.prototype.addEventListener = function (type, listener, capture)
{
    switch (type) {
	case 'selection':
	    this.listeners[type].push(listener);
	    break;
	default:
	    this.container.addEventListener(type, listener, capture);
	    break;
    }
};

/**
 * returns the container's bounding box relative to its parent
 * NOTE: This BBox is only of the unclipped portion 
 */
Container.prototype.getBBox = function ()
{
    var bbox = this.background.getBBox();
    return SVGRect_multiply(bbox, this.container.getCTM());
};


/**
 * return the transformation relative to its parent
 */
Container.prototype.getCTM = function ()
{
    return this.container.getCTM();
};


Container.prototype.setVisible = function (vis)
{
    //TODO could do some nifty effect here.
    var visibility = vis ? "inherit" : "none";
    this.container.setAttributeNS(null, "display", visibility);
};

Container.prototype.isVisible = function ()
{
    return (this.container.getAttributeNS(null, "display") == 'inherit');
}

Container.prototype.doLayout = function () 
{
    this.layoutManager.layoutContainer(this);
};

Container.prototype.doPreferedLayout = function ()
{
    this.setPreferedSize();
    this.doLayout();
};

Container.prototype.doDeepPreferedLayout= function ()
{
    for (var i in this.itemlist) {
	var item = this.itemlist[i]
	    if (item.isComponent) {
		item.doDeepPreferedLayout();
	    }
    }
    this.doPreferedLayout();
};

Container.prototype.setBounds = function (x, y, h, w)
{

    var bNewDims = ( h != this._bounds.height || w != this._bounds.width);
    this._bounds.x	= x || this._bounds.x;
    this._bounds.y	= y || this._bounds.y;
    this._bounds.height	= h || this._bounds.height;
    this._bounds.width	= w || this._bounds.width;


    var transform = "translate(" + this._bounds.x + " " + this._bounds.y + ")";
    this.background.setAttributeNS(null, 'height', this._bounds.height); 
    this.background.setAttributeNS(null, 'width', this._bounds.width); 
    this.clip.setAttributeNS(null, 'height', this._bounds.height); 
    this.clip.setAttributeNS(null, 'width', this._bounds.width); 

    this.container.setAttributeNS(null, 'transform', transform); 
};

Container.prototype.setInsets = function (b, t, l, r)
{
    this.insets = new Insets(b, t, l, r);
};

/** 
 * add a component or svg item to this container 
 */
Container.prototype.add = function (item)
{
    if (item.isComponent) {
	this.items.appendChild(item.container)
    } else {
	item = this._wrapSVGItem(item);
	this.items.appendChild(item);
    }
    this.itemlist.push(item);
};

Container.prototype._wrapSVGItem = function (svg) 
{
    var wrapped = svgdoc.createElementNS(svgns, "g");
    wrapped.appendChild(svg);
    return wrapped;
}

Container.prototype.removeAll = function ()
{
    for (var i in this.itemlist) {
	var item = this.itemlist[i];

	if (item.isComponent) {
	    item = item.container;
	} else {
	    item = item.parentNode;
	}

	this.items.removeChild(item);
    }
    this.itemlist = new Array();
};

/**
 * return true if item is in this container
 * NOTE: comparison is by SVG content
 *
 * @param	item	component or SVGElement to check
 */
Container.prototype.contains = function (item)
{
    return (this.getComponentIndex(item) != null);
};

/**
 * return index of the item in the item list
 * NOTE: comparison by SVG content. 
 *
 * @param	item	component or SVGElement to find 
 */
Container.prototype.getComponentIndex = function (item)
{
    if (item.isComponent) {
	item = item.container;
    }
    for (var i in this.itemlist) {
	var _item = this.itemlist[i];

	if (!_item.isComponent) {
	    _item = _item.firstChild;
	} else {
	    _item = _item.container;
	}

	if (_item == item) {
	    return i;
	}
    }
    return null;
};


Container.prototype.getComponent = function (index)
{
    var item;
    if (!(item = this.itemlist[index])) {
	return null;
    }

    return item;
}

/**
 * return the BBox of the item relative to this container.
 *
 * @param	index	index of item in the itemlist
 */
Container.prototype.getItemBBox = function (index)
{
    var item = this.getComponent(index);
    if (!item) {
	return null;
    }

    var itemBBox = item.getBBox();

    //TODO isn't this just a single multiplication?
    if (!item.isComponent) {
	var CTM = item.getCTM();
	while ( (item = item.parentNode) != this.container) {
	    CTM = item.getCTM().multiply(CTM);
	} 
	itemBBox = SVGRect_multiply(itemBBox, CTM);
    }

    return itemBBox; 
};

/** 
 * set the bounds of the item. 
 * the point (x,y) is the upper left hand corner of its bounding box
 *
 * @param	index	index of item in the itemlist
 */
Container.prototype.setItemBounds = function (index, x, y, h, w)
{

    var item = this.getComponent(index);
    if (!item) {
	return null;
    }

    if (item.isComponent) {
	item.setBounds(x,y,h,w);
    } else {
	item.setAttributeNS(null, "transform", "");
	var bbox = this.getItemBBox(index);
	var offset_x = x - bbox.x;
	var offset_y = y - bbox.y;
	var transform = "translate(" + offset_x + " " + offset_y + ")";
	item.setAttributeNS(null, "transform", transform);
    }
} 

Container.prototype.setPreferedSize = function ()
{
    var bbox = this.layoutManager.minimumLayoutBounds(this);
    this.setBounds(bbox.x, bbox.y, bbox.height, bbox.width);
};
// END CLASS: Container

///////////////////
//  Label 
//////////////////

inherit(Container, Label);

function Label (txt) 
{
    if (arguments.length > 0) {
	this.init(txt);
    }
}


Label.prototype.init = function (txt)
{
    Label.superclass.init.call(this, null);
    this.insets = new Insets(5,5,5,5);
    this.background.setAttributeNS(null, "style", 
	    "fill:white;visibility:hidden;");

    this.text = svgdoc.createElementNS(svgns, 'text');
    this.text.appendChild(svgdoc.createTextNode(txt));
    this.text.style.setProperty("pointer-events", "none");
    this.text.setAttributeNS(null, "class", "doc");
    this.add(this.text);
};

Label.prototype.setText = function (txt)
{
    this.text.firstChild.data = txt;
};

Label.prototype.setPreferedSize = function ()
{
    var bbox = this.layoutManager.minimumLayoutBounds(this);
    bbox.height = Math.max(bbox.height, getFontHeight(this.text));
    this.setBounds(bbox.x, bbox.y, bbox.height, bbox.width);
};

// END CLASS: Label


////////////////////
// CompositeButton 
////////////////////


inherit (Container, CompositeButton);

function CompositeButton ()
{
    if (arguments.length > 0) {
	this.init();
    }
};

CompositeButton.prototype.init = function ()
{
    this.active = true;  // active or inactive; 
    this.state = "up";

    CompositeButton.superclass.init.call(this, null);

    this.background.setAttributeNS(null, "style",
	    "fill:white;opacity:1;pointer-events:all;stroke:none;stroke-width:1;");
    this.addEventListener("mousedown", this, false);
    this.addEventListener("mouseup", this, false);
    this.addEventListener("mouseout", this, false);
    this.addEventListener("mouseover", this, false);

};

/* controller */
CompositeButton.prototype.handleEvent = function (evt)
{
    target = evt.getTarget();
    type = evt.type;
    switch (type) {
	case 'mouseover':
	    this.onmouseoverStyle(); 
	    break;
	case 'mousedown':
	    this.onmousedownStyle();
	    break;
	case 'mouseout':
	    this.onmouseoutStyle();
	    //fallthrough
	case 'mouseup':
	    this.onmouseupStyle();
	    break;
    }

    CompositeButton.superclass.handleEvent.call(this, evt);
};

/* view */
CompositeButton.prototype.onmouseoverStyle = function () 
{
    this.background.style.setProperty("stroke", "red");
    this.background.style.setProperty("stroke-width", "1");
};
CompositeButton.prototype.onmouseoutStyle= function  ()
{
    this.background.style.setProperty("stroke", "none");
};
CompositeButton.prototype.onmousedownStyle= function  ()
{
    this.background.style.setProperty("fill", "yellow");
};
CompositeButton.prototype.onmouseupStyle= function  ()
{
    this.background.style.setProperty("fill", "white");
};
// END CLASS: CompositeButton

////////////////////
// Checkbox
////////////////////

inherit (Container, Checkbox);

function Checkbox (label)
{
    this.init(label);
};

Checkbox.prototype.init = function (label)
{
    var labelstr = label || "";
    Checkbox.superclass.init.call(this, null);

    this.state = true;
    this.insets = new Insets(2,2,2,2);
    this.layoutManager = new CellLayout(1,2,null,5);
    this._makeCheckbox();
    this.add(this.cb);

    this.label = new Label(labelstr);
    this.label.insets = new Insets(1,1,1,1);
    this.add(this.label);

    this.addEventListener('click', this, false);
    this.setState(true);
};

Checkbox.prototype.setState = function (newstate)
{
    this.state = newstate;

    new_vis = (newstate) ? "inherit" : "none";
    this.check.setAttributeNS(null, "display", new_vis);
};

Checkbox.prototype.handleEvent = function (evt)
{
    target = evt.getTarget();
    type = evt.type;

    if (type == 'click') {
	this.setState(!this.state);
    }

    Checkbox.superclass.handleEvent.call(this, evt);
}

Checkbox.prototype._makeCheckbox = function ()
{
    var x = 0;
    var y = 0;

    //TODO scale properly with font-size
    var rect_length = Math.floor( getFontHeight(this.container) * 0.8 );

    var box = svgdoc.createElementNS(svgns, "rect");
    //box.setAttributeNS(null, "id", this.name + "_box");
    box.setAttributeNS(null, "x", x);
    box.setAttributeNS(null, "y", y);
    box.setAttributeNS(null, "width", rect_length);
    box.setAttributeNS(null, "height", rect_length);
    var style = "stroke:black;fill:none;stroke-width:1;";
    box.setAttributeNS(null, "style", style);

    var line_offset = Math.floor( rect_length * 0.1 );

    this.check = svgdoc.createElementNS(svgns, "g");
    //this.check.setAttributeNS(null, "id", this.name + "_check");
    this.check.setAttributeNS(null, "style",
	    "stroke:black;stroke-width:2;stroke-linecap:round;");
    // \
    var ul_lr = svgdoc.createElementNS(svgns, "line");
    ul_lr.setAttributeNS(null, "x1", x + line_offset);
    ul_lr.setAttributeNS(null, "y1", y + line_offset);
    ul_lr.setAttributeNS(null, "x2", x + rect_length - line_offset);
    ul_lr.setAttributeNS(null, "y2", y + rect_length - line_offset);

    // /
    var ll_ur = svgdoc.createElementNS(svgns, "line");
    ll_ur.setAttributeNS(null, "x1", x + line_offset);
    ll_ur.setAttributeNS(null, "y1", y + rect_length - line_offset);
    ll_ur.setAttributeNS(null, "x2", x + rect_length - line_offset);
    ll_ur.setAttributeNS(null, "y2", y + line_offset);
    this.check.appendChild(ul_lr);
    this.check.appendChild(ll_ur);

    this.cb = svgdoc.createElementNS(svgns, "g");
    this.cb.setAttributeNS(null, "style", "pointer-events:none;");
    this.cb.appendChild(this.check);
    this.cb.appendChild(box);
};

///////////////////
//  TitledContainer 
//////////////////


inherit (Container, TitledContainer);

function TitledContainer (title) 
{
    if (arguments.length > 0) {
	this.init(title);
    }

}

TitledContainer.prototype.init = function (title) {

    TitledContainer.superclass.init.call(this, null);

    if (title) {
	this.title_rect = svgdoc.createElementNS(svgns, "rect");
	this.title_rect.setAttributeNS(null, "x", 0);
	this.title_rect.setAttributeNS(null, "y", 0);
	this.title_rect.setAttributeNS(null, "style", "fill:#0062AC;");

	this.title = new Label(title);
	this.title.text.style.setProperty("fill", "white");
	this.container.appendChild(this.title_rect);
	this.container.appendChild(this.title.container);
    } else {
	this.title = null;
    }

};

TitledContainer.prototype.doLayout = function ()
{
    this._paintTitleBar();
    TitledContainer.superclass.doLayout.call(this);
};

TitledContainer.prototype._paintTitleBar = function ()
{
    if (this.title) {

	if (!this.updatedInsets) {
	    this.title.doDeepPreferedLayout();
	} 

	var w = this._bounds.width;
	var h = this.getTitlebarBBox().height;
	this.title.setBounds(0, 0, h); 
	this.title_rect.setAttributeNS(null, "width", w);
	this.title_rect.setAttributeNS(null, "height", h);

	// TODO not so hackish way? 
	if (!this.updatedInsets) {
	    this.insets.top += h;
	    this.updatedInsets = true;
	    this.setBounds(null,null,this._bounds.height + h,null);
	}
    }
}

TitledContainer.prototype.setPreferedSize = function (x,y,h,w)
{
    var bbox = this.layoutManager.minimumLayoutBounds(this);
    if (this.title) { 
	this.title.doPreferedLayout();
	var title_width = this.getTitlebarBBox().width; 
	bbox.width = Math.max(title_width, bbox.width);
    }
    this.setBounds(bbox.x, bbox.y, bbox.height, bbox.width);
};

TitledContainer.prototype.getTitlebarBBox = function () 
{
    var bbox = this.title.getBBox();
    bbox.width += this.insets.left + this.insets.right;
    return bbox;
};

////////////////////
// Menu
////////////////////

/* 
Menu: a container that coordinates the display of submenus 
 */

inherit(TitledContainer, Menu);

function Menu (title) 
{
    Menu.superclass.init.call(this, title);

    this.displayoffset = Menu.LOWER_LEFT;
    this.submenus = new Array();  // list of submenus
    this.isMenu = true;
    this.listeners['itemselection'] = new Array();
}

Menu.prototype.addEventListener = function (type, listener, capture)
{
    if (type == 'itemselection') {
	this.listeners[type].push(listener);
    }

    Menu.superclass.addEventListener.call(this, type, listener, capture);
};

Menu.prototype.handleEvent = function (evt) 
{

    type = evt.type;
    target = evt.getTarget();

    var i;
    if (type == 'selection' && (this.getComponentIndex(target) != null) ) {
	i = this.getComponentIndex(target);
	var submenu;
	if ((submenu = this.submenus[i])) {
	    var vis = submenu.isVisible(); 
	    this.hideAllSubmenus();
	    submenu.setVisible(!vis);
	} else {
	    this.hideAllSubmenus();
	}

	var event = new ComponentEvent();
	event.target = this;
	event.type = 'itemselection';
	event.componentIndex = i;

	this.dispatchEvent(event);

    } else if (type == 'selection' && (i = this.getSubmenuIndex(target))) {
	this.hideAllSubmenus();
    }

    Menu.superclass.handleEvent.call(this, evt);
};


Menu.prototype.getSubmenuIndex = function (target)
{
    for (var i in this.submenus) {
	if (this.submenus[i] == target) {
	    return i;
	}
    };
    return null;
};

Menu.prototype.doLayout = function ()
{
    Menu.superclass.doLayout.call(this);

    this._positionSubmenus();

};

Menu.prototype._positionSubmenus = function () 
{
    for (var i in this.submenus) {
	this._positionSubmenu(i);
    }
};

/* layout submenus too */
Menu.prototype.doDeepPreferedLayout = function ()
{
    for (var i in this.submenus) {
	this.submenus[i].doDeepPreferedLayout();
    }
    Menu.superclass.doDeepPreferedLayout.call(this);
};

Menu.prototype.setBounds = function (x,y,h,w)
{

    var bMoved = (this._bounds.x != x) || (this._bounds.y !=y);

    Menu.superclass.setBounds.call(this, x,y,h,w);
    this._paintTitleBar();

    if (bMoved) {
	this._positionSubmenus();
    }
};

Menu.prototype.attachSubmenu = function (menu, item) 
{
    var index; 
    if (!item) {
	index = this.itemlist.length - 1;
	if (index == -1) return false;
    } else {
	index = this.getComponentIndex(item);
	if (!index) return false;
    };

    if (menu.isMenu) {
	this.submenus[index] = menu;
	menu.setVisible(false);
	menu.addEventListener('selection', this, false);
    } else {
	return false;
    }
};

Menu.prototype.add = function (obj) 
{
    if (obj.isComponent) {
	obj.addEventListener('selection', this, false);
	Menu.superclass.add.call(this, obj);
    } else {
	Menu.superclass.add.call(this, obj);
    }
};

Menu.prototype.hideAllSubmenus = function () 
{
    for (var i in this.submenus) {
	var submenu = this.submenus[i];
	submenu.setVisible(false);
    };
};

/*
   item_index is the index of the item which has the submenu to layout. we
   assume that the submenu and the menu are in the same coordinate system */

Menu.prototype._positionSubmenu = function (item_index, tracer) 
{

    var rootElement = svgdoc.documentElement;

    var submenu = this.submenus[item_index];
    var item = this.itemlist[item_index];

    //submenu.setVisible(true);
    var submenuBBox = submenu.getBBox(); /* relative to parent */
    var submenuCTM = getTransformToElement(
	    submenu.container.parentNode, rootElement);
    submenuBBox = SVGRect_multiply(submenuBBox, submenuCTM);
    submenu.setVisible(false);

    var itemBBox =item.getBBox();  /* relative to menu */
    var itemCTM = getTransformToElement(this.container, rootElement);
    itemBBox = SVGRect_multiply(itemBBox, itemCTM);

    var offset = svgdoc.documentElement.createSVGPoint();
    offset.x = itemBBox.x - submenuBBox.x;
    offset.y = itemBBox.y - submenuBBox.y;

    /*
       switch (this.displayoffset) {
       default : //Menu.UPPER_LEFT : 
     */
    offset.x -= submenuBBox.width;
    offset.y -= submenuBBox.height - itemBBox.height;

    /*
       break;
    //case Menu.UPPER_MIDDLE: break;
    case Menu.UPPER_RIGHT: 
    offset.x += submenuBBox.width;
    offset.y -= submenuBBox.height - us_itemBBox.height;
    break;
    //case Menu.MIDDLE_LEFT: break;
    //case Menu.MIDDLE_MIDDLE: break;
    //case Menu.MIDDLE_RIGHT: break;
    default: // fallthrough
    case Menu.LOWER_LEFT: 
    offset.x -= submenuBBox.width;
    break;
    //case Menu.LOWER_MIDDLE: break;
    case Menu.LOWER_RIGHT: 
    offset.x += submenuBBox.width;
    break;
    };
     */

    submenuBBox.x += offset.x;
    submenuBBox.y += offset.y;
    submenuBBox = SVGRect_multiply(submenuBBox, submenuCTM.inverse());
    submenu.setBounds(submenuBBox.x, submenuBBox.y);
};

///////////////////
//  Button 
//////////////////

inherit (CompositeButton, Button);

function Button (lbl)
{
    Button.superclass.init.call(this, null);
    this.insets = new Insets(0,0,0,0); //(2,2,2,2)
    var str = lbl || "";
    name = str;
    this.label = new Label(str);
    this.add(this.label);
    this.setPreferedSize();
};

///////////////////
//  Window 
//////////////////

inherit (TitledContainer, Window);

function Window (title) {

    title = title || "Window";  /* we must have a title */
    Window.superclass.init.call(this,title);


    this.close = new CompositeButton(null);
    this.cross = svgdoc.createElementNS(svgns, "g");
    this.cross.style.setProperty("stroke", "black");
    this.cross.style.setProperty("stroke-width", "2");
    var ul_lr = svgdoc.createElementNS(svgns, "line");
    var x = 0;
    var y = 0;

    //TODO scale properly with font size
    var rect_length = getFontHeight(this.container) * 0.8;
    ul_lr.setAttributeNS(null, "x1", x );
    ul_lr.setAttributeNS(null, "y1", y );
    ul_lr.setAttributeNS(null, "x2", x + rect_length );
    ul_lr.setAttributeNS(null, "y2", y + rect_length );

    // /
    var ll_ur = svgdoc.createElementNS(svgns, "line");
    ll_ur.setAttributeNS(null, "x1", x );
    ll_ur.setAttributeNS(null, "y1", y + rect_length );
    ll_ur.setAttributeNS(null, "x2", x + rect_length );
    ll_ur.setAttributeNS(null, "y2", y );
    this.cross.appendChild(ul_lr);
    this.cross.appendChild(ll_ur);

    this.close.add(this.cross);
    this.close.insets = new Insets(3,3,3,3);
    this.close.addEventListener('selection', this, false);
    this.container.appendChild(this.close.container);

    this.space = 10;
    this.dragging = false;

    this.background.style.setProperty("stroke", "black");
    this.background.style.setProperty("stroke-width", "2");

    if (this.title_rect) {
	this.title_rect.addEventListener("mousedown", this, false);
    }

}

Window.prototype._paintTitleBar = function ()
{
    Window.superclass._paintTitleBar.call(this);

    //TODO use _bounds for 'close'
    var x = this._bounds.width - this.insets.right - this.close.getBBox().width; 
    this.close.setBounds(x, 0);
};

Window.prototype.getTitlebarBBox = function () 
{
    this.title.doPreferedLayout();
    this.close.doDeepPreferedLayout();

    var bbox = this.title.getBBox();
    bbox.width += this.insets.left + this.space;
    bbox.width += this.close.getBBox().width + this.insets.right;
    bbox.height = Math.max(bbox.height, this.close.getBBox().height);
    return bbox;

};

Window.prototype.handleEvent = function (evt)
{
    target = evt.getTarget();
    type = evt.type;
    if (target == this.close && type == "selection") {
	this.setVisible(false);
    }

    if (type == "mousedown" && target == this.title_rect) {
	this.container.parentNode.appendChild(this.container); // move to front
	viewport.addEventListener("mousemove", this, false);
	viewport.addEventListener("mouseup", this, false);
	this.lastpos = transformFromScreenSpace(
		    evt.clientX, evt.clientY, this.container.parentNode);
	this.items.style.setProperty("display", "none");
    }

    if (type == "mousemove" && target == viewport) {
	current_pos = transformFromScreenSpace(
		    evt.clientX, evt.clientY, this.container.parentNode);
	var delta_x = current_pos.x - this.lastpos.x; 
	var delta_y = current_pos.y - this.lastpos.y; 
	this.lastpos = current_pos; 
	this.setBounds(this._bounds.x + delta_x, this._bounds.y + delta_y);
    }

    if (type == "mouseup" && target == viewport) {
	viewport.removeEventListener("mousemove", this, false);
	viewport.removeEventListener("mouseup", this, false);
	this.items.style.setProperty("display", "inherit");
    }
    Window.superclass.handleEvent.call(this, evt);

};

///////////////////
//  Textbox
//////////////////

/*
   Lots of code taken from 
http://www.kevlindev.com/gui/widgets/textbox/index.htm 
 */


inherit (Container, Textbox);

/*****
 *
 *	contructor
 *
 *****/
function Textbox() {
    if (arguments.length > 0) {
	this.init();
    }
}

Textbox.prototype.init = function () 
{
    Textbox.superclass.init.call(this, null);

    this.insets = new Insets(5,5,5,5);
    this.listeners['action'] = new Array();
    this.background.style.setProperty("stroke", "black");
    this.background.style.setProperty("stroke-width", "2");


    this.textbox = svgdoc.createElementNS(svgns, 'text'); 
    this.textbox.style.setProperty("fill", "black");
    this.add(this.textbox);
    this.add_char("?");
    this.background.addEventListener("keypress", this, false);
    this.textbox.addEventListener("keypress", this, false);
    //rect3.node.addEventListener("mouseover", TextboxFocus, false);
    //rect3.node.addEventListener("mouseout", TextboxBlur, false);
}
Textbox.prototype.handleEvent = function (evt)
{
    Textbox.superclass.handleEvent.call(this, evt);
    switch (evt.type) {
	case "keypress" : 
	    this.TextboxKeypress(evt);
	break;
	default: break;
    };
};

/*****
 *
 *	TextboxKeypress
 *
 *	Process a keypress event
 *
 *****/
Textbox.prototype.TextboxKeypress = function(event) {
    try {
	var key = event.getCharCode();
	if ( key >= 32 && key <= 127 ) {
	    try {
		this.add_char(String.fromCharCode(key));
	    } catch (e) { }
	} else if ( key == 8 ) {
	    this.delete_char();
	} else if ( key == 13 ) {
	    var event = new ComponentEvent();
	    event.type = 'action';
	    event.target = this;
	    this.dispatchEvent(event);
	} else {
	    alert(key);
	}
    } catch (e) {}
}

/*****
 *
 *	add_char
 *
 *	Add a character to end of the current line
 *	If the current line exceeds the width of the
 *	textbox, then create a new line
 *
 *****/
Textbox.prototype.add_char = function (new_char) 
{
    var textbox = this.textbox;
    if ( !textbox.hasChildNodes() ) { this.add_tspan("", 0) }
    var tspan = textbox.getLastChild();
    var data  = tspan.getFirstChild();
    data.appendData(new_char);
    if ( tspan.getComputedTextLength() > this._bounds.width ) {
	this.delete_char();
	this.add_tspan(new_char);
    }
}

/*****
 *
 *	delete_char
 *
 *	Delete the last character of the last line
 *	If a line is empty as a result, then remove
 *	that line from the <text> element
 *
 *****/
Textbox.prototype.delete_char = function () 
{
    var textbox = this.textbox;

    if ( textbox.hasChildNodes() ) {
	var tspan  = textbox.getLastChild();
	var data   = tspan.getFirstChild();
	var length = data.getLength();

	if ( length > 1 ) {
	    data.deleteData(length-1, 1);
	} else {
	    textbox.removeChild(tspan);
	}
    }
}

/*****
 *
 *	add_tspan
 *
 *	Used to add a new line to the textbox
 *	Offset is an optional parameter which designates
 *	the vertical offset of the new <tspan> element.
 *	This was needed to handle the first <tspan> added
 *	to the <text> element
 *
 *****/
Textbox.prototype.add_tspan = function(new_char, offset) 
{
    var tspan  = svgdoc.createElementNS(svgns, "tspan");
    var data   = svgdoc.createTextNode(new_char);

    if ( offset == null || offset == 0) { offset = "1em" }
    tspan.setAttribute("x", 0);
    tspan.setAttribute("dy", offset);
    tspan.appendChild(data);
    this.textbox.appendChild(tspan);
}

Textbox.prototype.getText = function() 
{
    var textbox = this.textbox;

    // clear text box
    if ( textbox.hasChildNodes() ) { 
	var tspan = textbox.firstChild;
	do {
	    text += tspan.firstChild.nodeValue;
	} while ((tspan = tspan.nextSibling) != null);
    }

    if ( !textbox.hasChildNodes() ) { this.add_tspan("", 0) }
    var tspan = textbox.firstChild;
    var text = "";
    do {
	text += tspan.firstChild.nodeValue;
    } while ((tspan = tspan.nextSibling) != null);
    return text;
}

Textbox.prototype.setText = function (text) 
{
    text = text || "";
    var textbox = this.textbox;
    var tspan;
    while ((tspan = textbox.firstChild) != null) {
	textbox.removeChild(tspan);
    }
    for (var i = 0; i < text.length; i++) {
	this.add_char(text[i]);
    }
}

Textbox.prototype.addEventListener = function (type, listener, capture)
{
    switch (type) {
	case 'action':
	    this.listeners[type].push(listener);
	    break;
	default:
	    Textbox.superclass.addEventListener(this,type,listener, capture);
	    break;
    }
};
