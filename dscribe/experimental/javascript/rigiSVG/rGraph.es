//////////////////////////////////////////////
//	  
//	   rGraph
//
//////////////////////////////////////////////

/**
 * rGraph is the topmost class which defines the rGraph component.  rGraph is
 * loosely based on the JGraph component (http://jgraph.sourceforge.net)
 *
 * The following code creates an rGraph component:
 *   //TODO write this up
 */
/*****
 *
 *   constructor
 *
 *****/
function rGraph(model, view) 
{
    if (arguments.length > 0) {
	this.init(model, view);
    }
}

/*****
 *
 *   init
 *
 *****/
rGraph.prototype.init = function(model, view) 
{
    if (model) {
	this.setModel(model);
	if (view) {
	    this.setView(view);
	}
    } else { 
	this.setModel(new DefaultGraphModel(null));
	this.setView(new DefaultGraphView(this.model));
    }

    this.nodeselection	    = new GESet();
    this.graphattrs	    = new AttrSet();
    this.graphattrs.setAttr("MOVEABLE_NODES", true);
};

/* 
 */
rGraph.prototype.realize = function ()
{
};

rGraph.prototype.getModel = function ()
{
    return this.model;
};

rGraph.prototype.setModel = function (model)
{
    if (this.model) {
	//TODO clean up old model properly
	delete this.model;
    }
    this.model = model;
};

rGraph.prototype.setView = function (view)
{
    if (this.view) {
	// clean up old view
	this.view.destroy();
	delete this.view;
	delete this.nodeselectionhandler;
	delete this.rubberband;
	delete this.nodedragger;
    }

    this.view   = view; 

    this.svgNode = this.view.graph_pane;
    this.svgNode.setAttributeNS(null, "id", "graph_pane");
    svgdoc.documentElement.insertBefore(this.svgNode,
	    svgdoc.documentElement.firstChild);

    this.background	= svgdoc.createElementNS(svgns, "rect");
    this.background.setAttributeNS(null, "id", "graph_background"); 
    this.background.style.setProperty("visibility", 'hidden');
    this.background.style.setProperty("pointer-events", 'all');

    this.background.addEventListener('mousedown', this, false);
    this.svgNode.insertBefore(this.background, this.svgNode.firstChild);

    this.node_group	= this.view.node_group;
    this.arc_group	= this.view.arc_group;
    
    this.node_group.addEventListener("mousedown", this, false);
    this.node_group.addEventListener("mouseup", this, false);

    this.view.viewbox.addEventListener("viewBoxChanged", this, false);

    /// set up interactivity ///
    this.nodeselectionhandler = new DefaultNodeSelectionHandler(this);
    this.rubberband = new Rubberband(this);
    this.nodedragger = new NodeDragger(this);
    this.calibrateToViewBox();
};

rGraph.prototype.setBounds = function (x,y,h,w) 
{
    if (x) {
	this.svgNode.setAttributeNS(null, "x", x);
    }
    if (y) {
	this.svgNode.setAttributeNS(null, "y", y);
    }
    if (h) {
	this.svgNode.setAttributeNS(null, "height", h);
    }
    if (w) {
	this.svgNode.setAttributeNS(null, "width", w);
    }
};
/*****
 *
 *   handleEvent
 *
 *****/
rGraph.prototype.handleEvent = function(evt) {
    var type = evt.type;
    var target = evt.getTarget();

    if (type == "viewBoxChanged") {
	this.calibrateToViewBox();
	return;
    } 

    if (evt.button != 0) return; // only handle left mouse clicks for now

    if (target == this.background) {
	switch (type) {
	    case 'mousedown':
		if (!evt.ctrlKey && !evt.altKey) {
		    this.nodeselectionhandler.startRubberband(evt);
		    this.rubberband.beginStretch(evt);
		}
		break;
	    default: 
		throw 
		    new GeneralError("rGraph:Unsupported event: " 
			    + type + " on graph background");
		break;

	}
    } else if (target.parentNode.parentNode == this.node_group) {

	switch (type){
	    case 'mousedown':  
		this.nodeselectionhandler.nodeSelected(evt);
		if (this.graphattrs.getAttr("MOVEABLE_NODES")) {
		    this.nodedragger.beginDrag(evt);
		}
		break;
	    case 'mouseup': 
		this.nodeselectionhandler.nodeSelected(evt);
		break;
	    default: 
		throw 
		    new GeneralError("rGraph: Unsupported event: " 
			    + type + " on graph background");
		break;
	};
    } else {
	throw new GeneralError("Graph: Unsupported target: " + target);
    }

};

/* calibrate to current viewbox */
rGraph.prototype.calibrateToViewBox = function() 
{
    this.background.setAttributeNS(null, "x", this.view.viewbox.x);
    this.background.setAttributeNS(null, "y", this.view.viewbox.y);
    this.background.setAttributeNS(null, "height", "100%");
    this.background.setAttributeNS(null, "width", "100%");
};
// END CLASS: rGraph

//////////////////////////////////////////////
//	  
//	 DefaultGraphModel 
//
//////////////////////////////////////////////

/*****
 *
 *   constructor
 *
 *****/
function DefaultGraphModel(domain) 
{
    if (arguments.length > 0) {
	this.init(domain);
    }
}

DefaultGraphModel.prototype.init = function (domain) 
{
    this.domain = domain || new Domain("default");

    this.nodehandler = new NodeHandler();
    this.archandler = new ArcHandler();

    //TODO change these to Objects
    this.nodes = new Array();
    this.arcs	= new Array();
    
    //The following arrays for history
    this.nodesHistory = new Array();
    this.arcsHistory = new Array();
    this.selectionHistory = new Array();
};

DefaultGraphModel.prototype.createNode = function (id, type) 
{
    // check type
    if (!this.domain.getNodeType(type)) {
	throw 
	    new GeneralError('Model::createNode Error: Type "' 
		    + type + '" is not in the domain"' 
		    + this.domain.domain_name + '"');
    }

    this.nodes[id] = {
       id: id,
       type: type,
       svg: null,
       collapse: false,  // if collapse=true, this node is a child of a collapse node and is invisible.
       parentNode: null, // when collapse=true, a collapse node is a parentNode of this node;
       isVisible: true,
       incoming: new Array(),
       outgoing: new Array(),

       attribute: new Object(),
       objects: new Object(),

       nodeArray: new Array(),  // contains all its children nodes
       arcInArray: new Array(), // contains the arcs which connect among its children
       arcOutArray: new Array(),// contains the arcs which connect outside nodes with its children
    } 
    return this.nodes[id];
}

DefaultGraphModel.prototype.copyNode = function (id,hID)
{  
    var nodesTemp= this.nodesHistory[hID]; 
    var point = graph.view.nodeview.getLocation(graph.view.nodes[id]);
    var vnodes = graph.view.getViewNodes(this.nodes);
    
    nodesTemp[id] = {
       id: id,
       type: this.nodes[id].type,
       svg: null,
       collapse: this.nodes[id].collapse,
       parentNode: null,
       isVisible: graph.view.nodeview.isVisible(vnodes[id]), // true if visible; false if hidden
      
       attribute: this.nodes[id].attribute,
       objects: new Object(),

       nodeArray: new Array(),  // contains all its children nodes
       arcInArray: new Array(), // contains the arcs which connect among its children
       arcOutArray: new Array(),// contains the arcs which connect outside nodes with its children
       x: point.x,
       y: point.y,
    }
    var t = graph.getModel().getNodeHandler().getAttribute(this.nodes[id],"name");
    if(t!=null) graph.getModel().getNodeHandler().setAttribute(nodesTemp[id],"name", t);
    var t = graph.getModel().getNodeHandler().getAttribute(this.nodes[id],"sourcefile");
    if(t!=null) graph.getModel().getNodeHandler().setAttribute(nodesTemp[id],"sourcefile", t);
  
    if(this.nodes[id].parentNode != null) nodesTemp[id].parentNode = this.nodes[id].parentNode.id;
    
    for(var i in this.nodes[id].nodeArray){
        nodesTemp[id].nodeArray.push(this.nodes[id].nodeArray[i].id);
    }
    for(var i in this.nodes[id].arcInArray){
        nodesTemp[id].arcInArray.push(this.nodes[id].arcInArray[i].id);
    }
    for(var i in this.nodes[id].arcOutArray){
        if(this.nodes[id].arcOutArray[i]!=null) nodesTemp[id].arcOutArray.push(this.nodes[id].arcOutArray[i].id);
    }
}

DefaultGraphModel.prototype.back1 = function (id,hID)
{
     var nodesTemp = this.nodesHistory[hID];
     newNode = graph.getModel().createNode(id, nodesTemp[id].type); 
     
     var t = graph.getModel().getNodeHandler().getAttribute(nodesTemp[id],"name");
     if(t!=null) graph.getModel().getNodeHandler().setAttribute(newNode,"name", t);
     var t = graph.getModel().getNodeHandler().getAttribute(nodesTemp[id],"sourcefile");
     if(t!=null) graph.getModel().getNodeHandler().setAttribute(newNode,"sourcefile", t);
   
     newNode.collapse = nodesTemp[id].collapse;
     newNode.isVisible = nodesTemp[id].isVisible;
     newNode.svg = nodesTemp[id].svg;
     newNode.objects = nodesTemp[id].objects;
     return this.nodes[id];
}

DefaultGraphModel.prototype.back2 = function (id,hID) 
{
     var nodesTemp = this.nodesHistory[hID];  
     if(nodesTemp[id].parentNode != null) this.nodes[id].parentNode = this.nodes[nodesTemp[id].parentNode];

     for(var i in nodesTemp[id].nodeArray){
          this.nodes[id].nodeArray.push(graph.view.nodes[nodesTemp[id].nodeArray[i]]);
     }
     for(var i in nodesTemp[id].arcInArray){
          this.nodes[id].arcInArray.push(this.arcs[nodesTemp[id].arcInArray[i]]);
     }
     for(var i in nodesTemp[id].arcOutArray){
          this.nodes[id].arcOutArray.push(this.arcs[nodesTemp[id].arcOutArray[i]]);
     }
}

/**
  src, dst are graph nodes and are optional
 */
DefaultGraphModel.prototype.createArc = function (id, type, src, dst) 
{

    // check type
    if (!this.domain.getArcType(type)) {
	throw 
	    new GeneralError('Model::createArc Error: Type "' 
		    + type + '" is not in the domain"' 
		    + this.domain.domain_name + '"');
    }

    this.arcs[id] = {
	id: id,
	type: type,
	svg: null,
	src: null,
	dst: null,
	isVisible: true,
	collapse: false, // if collapse=true,this arc is contained in a collapse node's arcInArray or arcOutArray and is invisible.

	attribute: new Object(),
	objects: new Object() 
    };

    if (!src || !dst) {
	// TODO throw some error
    } else {
	var arc = this.arcs[id];
	arc.src = src;    
	arc.dst = dst;

	src.outgoing.push(arc);
	dst.incoming.push(arc);
    }

    return this.arcs[id];
};

/**
  src, dst are graph nodes and are optional
 */
DefaultGraphModel.prototype.copyArc = function (id,hID) 
{
    var arcsTemp = this.arcsHistory[hID];
    var varcs = graph.view.getViewArcs(this.arcs);
    if(this.arcs[id].src!=null && this.arcs[id].dst!=null){
      arcsTemp[id] = {
	id: id,
	type: this.arcs[id].type,
	svg: this.arcs[id].svg,
	src: this.arcs[id].src.id,
	dst: this.arcs[id].dst.id,
	isVisible: graph.view.arcview.isVisible(varcs[id]), // true if visible; false if hidden
	collapse: this.arcs[id].collapse,
	attribute: this.arcs[id].attribute,
	objects: this.arcs[id].objects 
      };
    }
};

DefaultGraphModel.prototype.backArc = function (id,hID) 
{
    var arcsTemp = this.arcsHistory[hID];
    newArc = graph.getModel().createArc(id, arcsTemp[id].type, this.nodes[arcsTemp[id].src],this.nodes[arcsTemp[id].dst]); 
    
    this.arcs[id].isVisible = arcsTemp[id].isVisible;
    this.arcs[id].collapse = arcsTemp[id].collapse;
    if(this.arcs[id].collapse == true) {
       this.removeArcFromDst(newArc);
       this.removeArcFromSrc(newArc);
    }
    this.arcs[id].svg = arcsTemp[id].svg;
    
    var attributes = arcsTemp[id].attribute;  
    for ( var i = 0 ; i < attributes.length ; i++ ){
    	var attr = attributes.item(i);
    	if (attr.namespaceURI == ATTRNSVAL) {
    		this.archandler.setAttribute(this.arcs[id], attr.localName, attr.value);
    	}
    }
    this.arcs[id].objects = arcsTemp[id].objects;
    return this.arcs[id];
}



DefaultGraphModel.prototype.setDomain = function (domain)
{
    //TODO add necessary notifications and housekeeping
    //Probably have to destroy model
    this.domain = domain;
};

DefaultGraphModel.prototype.getNodeHandler = function () 
{
    return this.nodehandler;
};

DefaultGraphModel.prototype.getArcHandler = function () 
{
    return this.archandler;
};

DefaultGraphModel.prototype.getNodeById = function (id)
{
    return this.nodes[id];
};

DefaultGraphModel.prototype.getArcById = function (id)
{
    return this.arcs[id];
};

// return an arc from src to dst if exists.
DefaultGraphModel.prototype.getArcByConnected = function (src, dst) 
{
    for(var i in this.arcs){
       if (this.arcs[i]!= null && this.arcs[i].src!= null && this.arcs[i].dst!=null){
           if (this.arcs[i].src.id == src.id && this.arcs[i].dst.id == dst.id) return this.arcs[i];
       }
    }
    return null;
};

// remove an arc from its src.outgoing
DefaultGraphModel.prototype.removeArcFromSrc = function (arc) 
{
   if(arc.src!=null) do{
      var tempArc = arc.src.outgoing.shift();
      if(tempArc.id != arc.id) arc.src.outgoing.push(tempArc);
   }while(tempArc.id != arc.id);    
}

// remove an arc from its dst.incoming
DefaultGraphModel.prototype.removeArcFromDst = function (arc) 
{
   if(arc.dst!=null) do{
      var tempArc = arc.dst.incoming.shift();
      if(tempArc.id != arc.id) arc.dst.incoming.push(tempArc);
   }while(tempArc.id != arc.id);    
}

/**
 * returns an iterator over the approriate nodes 
 */
DefaultGraphModel.prototype.getNodesByAttribute = function (attr, pattern)
{
    var set = new GESet();
    set.addAll(this.nodes);
    var iter = new GEAttrSetIterator(set);
    iter.setAttrPattern(attr, pattern);
    return iter;
};

/**
 * returns an iterator over the approriate arcs
 */
DefaultGraphModel.prototype.getArcsByAttribute = function (attr, pattern)
{
    var set = new GESet();
    set.addAll(this.arcs);

    var iter = new GEAttrSetIterator(set);
    iter.setAttrPattern(attr, pattern);

    return iter;
};

/**
 * returns an iterator over all arcs
 */
DefaultGraphModel.prototype.getArcs = function ()
{
    var set = new GESet();
    set.addAll(this.arcs);

    var iter = new GEAttrSetIterator(set);
    return iter;
};


//////////////////////////////////////////////
//	  
//	 NodeHandler
//
//////////////////////////////////////////////
/* 
 * 'Inner class' of the model which acts as a flyweight for nodes
 */
function NodeHandler() { }

NodeHandler.prototype.setAttribute = function (node, attr, value) 
{
    node.attribute[attr] = value;
};

NodeHandler.prototype.getAttribute = function (node, attr) 
{
    return node.attribute[attr];
};

// END CLASS: NodeHandler 

//////////////////////////////////////////////
//	  
//	 ArcHandler
//
//////////////////////////////////////////////
/* 
 * 'nner class' of the model which acts as a flyweight for arcs 
 */
function ArcHandler() { }

ArcHandler.prototype.setConnection = function (arc, src, dst) 
{
    arc.src = src;    
    arc.dst = dst;

    src.outgoing.push(arc);
    dst.incoming.push(arc);
};

// END CLASS: ArcHandler 

// END CLASS: DefaultGraphModel 

//////////////////////////////////////////////
//	  
//	 SVGImportGraphModel
//
//////////////////////////////////////////////

inherit (DefaultGraphModel, SVGImportGraphModel);

/** 
 * constructor
 * @param svgnode   optional. SVGElement that has node and arc groups as
 * children
 */
function SVGImportGraphModel (svgnode) 
{
    if (arguments.length > 0) {
	this.init(svgnode);
    };
}

/**
 * initialization
 */
SVGImportGraphModel.prototype.init = function (svgnode)
{
    SVGImportGraphModel.superclass.init.call(this, null);
    if (svgnode) {
	this.importFromSVG(svgnode);
    }
};

/**
 * builds model
 */
SVGImportGraphModel.prototype.importFromSVG = function (svgnode)
{
    /* nodes */
    var group = svgnode.getElementById("nodes");
    var nodegroups = group.getElementsByTagName("g");
    for ( var j = 0 ; j < nodegroups.length ; j++ ) {
	var nodegroup = nodegroups.item(j);
	var node = nodegroup.getElementsByTagName("use").item(0);
	var id	= node.id;
	var type = node.getAttributeNS(null, "type");

	if (!this.domain.getNodeType(type)) {
	    this.domain.addNodeType(type);
	}

	var n = this.createNode(id, type);
	var attributes = node.attributes;

	for ( var i = 0 ; i < attributes.length ; i++ ){
	    var attr = attributes.item(i);
	    if (attr.namespaceURI == ATTRNSVAL) {
		this.nodehandler.setAttribute(n, attr.localName, attr.value);	
	    }
	}
    }

    /* arcs */
    var group = svgnode.getElementById("arcs");
    var arcs = group.getElementsByTagName("line");

    for ( var j = 0 ; j < arcs.length ; j++ ) {
	var arc	= arcs.item(j);
	var id	= arc.id;
	var type = arc.getAttributeNS(null, "type");
	var srcid = arc.getAttributeNS(null, "src");
	var dstid = arc.getAttributeNS(null, "dst");
	var src	= this.getNodeById(srcid);
	var dst	= this.getNodeById(dstid);

	if (!this.domain.getArcType(type)) {
	    this.domain.addArcType(type);
	}

	var a = this.createArc(id, type, src, dst);

	var attributes = arc.attributes;

	for ( var i = 0 ; i < attributes.length ; i++ ){
	    var attr = attributes.item(i);
	    if (attr.namespaceURI == ATTRNSVAL) {
		this.archandler.setAttribute(a, attr.localName, attr.value);
	    }
	}
    }
};



//////////////////////////////////////////////
//	  
//	 Domain
//
//////////////////////////////////////////////
/*
 * The graph component can be specialized for particular domains, such as C
 * language programming. Each domain has a set of appropriate node and arc
 * types, and node and arc attributes. These aspects are expressed in a Domain
 * object.
 *
 * NOTE: this format for defining domains differs somewhat from the Rigi
 * format in the following ways:
 *
 *	- In Rigi, 'Class attributes' are declared in the "Rigiattr" domain
 *	file.  Here they are *defined* on-the-fly by the method
 *	NodeView::setAttribute for an individual node.
 *
 *	- In Rigi, node and arc colours are defined in the "Rigicolor" domain
 *	file.  Here they are defined as an attribute of a node or arc type.
 *
 *	- In Rigi, node radii are defined "I-don't-know-where".  Here they are
 *	defined as an attribute of a node type.
 */

function Domain (domain_name) 
{
    if (arguments.length > 0) {
	this.init(domain_name);
    }
}

Domain.prototype.init = function (domain_name) 
{
    this.domain_name = domain_name;
    this.domainattrs = new AttrSet();
    this.nodetypes = new Object();
    this.arctypes = new Object();
    
    var type = this.addNodeType("Variable"); 
    type.setAttr("rgb", "rgb(102,102,255)");
    var type = this.addNodeType("Collapse");
    type.setAttr("rgb", "rgb(255,179,96)");
    var type = this.addNodeType("Function");
    type.setAttr("rgb", "rgb(51,255,51)");
    
    var type = this.addArcType("call");
    type.setAttr("rgb", "rgb(0,0,153)");
    var type = this.addArcType("composite");
    type.setAttr("rgb", "rgb(99,248,177)");
    var type = this.addArcType("Reference");
    type.setAttr("rgb", "rgb(153,0,0)");
    
};

Domain.prototype.addNodeType = function (node_type_name)
{
    return this.nodetypes[node_type_name] = new AttrSet();
};

Domain.prototype.addArcType = function (arc_type_name)
{
    return this.arctypes[arc_type_name] = new AttrSet();
};

Domain.prototype.getNodeType = function (node_type_name)
{
    return this.nodetypes[node_type_name];
};

Domain.prototype.getArcType = function (arc_type_name)
{
    return this.arctypes[arc_type_name];
};

Domain.prototype.getArcType = function (arc_type_name)
{
    return this.arctypes[arc_type_name];
};

Domain.prototype.getDomainAttrs = function ()
{
    return this.domainattrs;
};

//////////////////////////////////////////////
//	  
//	 AttrSet
//
//////////////////////////////////////////////
/*
 *  Method interface for Object-as-a-hash
 */
function AttrSet () { this.attrs = new Object(); }
AttrSet.prototype.setAttr = function (name, value) { this.attrs[name] = value; };
AttrSet.prototype.getAttr = function (name) { return this.attrs[name] || null}; 


//////////////////////////////////////////////
//	  
//	 DefaultGraphView
//
//////////////////////////////////////////////

function DefaultGraphView (model) 
{
    if (arguments.length > 0) {
	this.init(model);
    }
}

DefaultGraphView.prototype.init = function (model)
{
    this.model = model; 
    this.viewattrs = new AttrSet(); // general view attributes
    // set default attrs
    this.viewattrs.setAttr("GRAPH_STYLE", "directed");
    this.viewattrs.setAttr("NODE_RADIUS", 6);

    /* this is just for testing.
       CREATION == clone => one SVGElement generated and then cloned
       CREATION == create => SVGElement generated on each call
     */
    this.viewattrs.setAttr("CREATION", "clone");	// clone,create
    this.viewattrs.setAttr("NODE_STYLE", "use");	// use,circle,square 

    this.nodes = new Object();
    this.arcs  = new Object();  
    this._createViewSVG(); 
    this.nodeview = new NodeView(this);
    this.arcview = new ArcView(this);

    this.viewbox = new ZoomAndPanViewBox (this.graph_pane);

    this.realize();
};

/* finalize any display properties that depend on parameters */
DefaultGraphView.prototype.realize = function () {
    //do styling 
    if (this.viewattrs.getAttr("GRAPH_STYLE") == 'directed') {
	this.arrowhead_len = this.viewattrs.getAttr("NODE_RADIUS") * 0.8;
	this.arc_group.style.setProperty("marker-end", "url(#Triangle)");
    } else {
	this.arc_group.style.setProperty("marker-end", "");
    }

    this.labels = new NodeLabels(this);
};

DefaultGraphView.prototype.importModel = function ()
{
    var model = this.model;
    for (var i in model.nodes) {
	this.addNode(model.nodes[i]);
    }

    for (var i in model.arcs) {
	this.addArc(model.arcs[i]);
    }
};

/* set up the SVG to house the graph */
DefaultGraphView.prototype._createViewSVG = function ()
{
    this.graph_pane = svgdoc.createElementNS(svgns, "svg");

    this.graph_contents = svgdoc.createElementNS(svgns, "g")
    this.graph_contents.setAttributeNS(null, "id", "graph_contents");
    this.graph_pane.appendChild(this.graph_contents);

    this.arc_group = svgdoc.createElementNS(svgns, "g");
    this.arc_group.setAttributeNS(null, "id", "arcs");
    this.arc_group.setAttributeNS(null, "style",
	    "stroke-width:1;pointer-events:none;");
    this.graph_contents.appendChild(this.arc_group);

    this.node_group = svgdoc.createElementNS(svgns, "g");
    this.node_group.setAttributeNS(null, "id", "nodes");

    this.node_group.setAttributeNS(null, "style", "stroke:black;");
    this.graph_contents.appendChild(this.node_group);
};

DefaultGraphView.prototype.destroy = function ()
{
    /* we've got a bunch of dangling pointers after this move */
    this.graph_pane.parentNode.removeChild(this.graph_pane);
    //TODO should remove viewattrs for each node
};

DefaultGraphView.prototype.setViewbox = function(x,y,h,w,PAR)
{
    this.viewbox.setViewbox(x,y,h,w,PAR);
};

/* set view box to display entire graph */
//TODO make this a command
DefaultGraphView.prototype.setViewboxToGraphBounds = function ()
{
    var minx;
    var miny;
    var maxx;
    var maxy;
    var maxradius;

    for (var i in this.model.nodes) {
	var node = this.model.nodes[i];
	if (this.nodeview.isInView(node)) {
	    var loc = this.nodeview.getLocation(node);
	    if (!minx) {
		minx = loc.x;
		miny = loc.y;
		maxx = loc.x;
		maxy = loc.y;
		maxradius = this.nodeview.getRadius(node);
	    } else {
		minx = Math.min(minx, loc.x);
		miny = Math.min(miny, loc.y);
		maxx = Math.max(maxx, loc.x);
		maxy = Math.max(maxy, loc.y);
		maxradius = Math.max(maxradius,
			this.nodeview.getRadius(node));
	    }
	};
    };

    var x = minx - 2 * maxradius;
    var y = miny - 2 * maxradius;
    var w = (maxx - x) + 2 * maxradius;
    var h = (maxy - y) + 2 * maxradius;

    var rect = svgdoc.getElementById("newviewboxRECT");
    if (!rect) {
	rect = svgdoc.createElementNS(svgns, "rect");
	this.graph_pane.appendChild(rect);
	rect.setAttributeNS(null, "style",
		"fill:none;fill-opacity:0.1;stroke:black;stroke-width:4");
	rect.setAttributeNS(null, "id","newviewboxRECT");
    };
    rect.setAttributeNS(null, "x", x);
    rect.setAttributeNS(null, "y", y);
    rect.setAttributeNS(null, "width", w);
    rect.setAttributeNS(null, "height", h);
    this.setViewbox(x,y,h,w,null);
};

/**
 * adds the node to the view
 * @param   gnode    model node 
 */
DefaultGraphView.prototype.addNode = function (gnode)
{
    var svgnode;
    var nodegroup;

    if (this.viewattrs.getAttr("CREATION") == 'clone') {
	// get node
	if (!this.node_svg_prototype) {
	    this.node_svg_prototype	= new Object();
	}
	if (!this.node_svg_prototype[gnode.type]) {
	    this.node_svg_prototype[gnode.type] =
		this._createNode(gnode.type);
	}
	
	svgnode	= this.node_svg_prototype[gnode.type].cloneNode(false);
	nodegroup	= this._createNodeGroup();
	nodegroup.appendChild(svgnode);
    } else {
	svgnode = this._createNode(gnode.type); 
	nodegroup = this._createNodeGroup();
	nodegroup.appendChild(svgnode);
    }

    nodegroup.setAttributeNS(null, "id", gnode.id + "group");
    this.node_group.appendChild(nodegroup);

    // create view node, probably should be a seperate function, (but slow..)
    var vnode = {
	id: gnode.id,
	gnode: gnode,  //TODO contain a reference? or associate by id only?
	svg: svgnode,
	viewattr: new Array(),
    }

    this.nodes[vnode.id] = vnode;
    this.nodeview.setAttribute(vnode, "visible", true);
    svgnode.setAttributeNS(null, "id", vnode.id);
};

/**
 * adds the arc to the view
 * @param   garc    model arc 
 */
DefaultGraphView.prototype.addArc = function (garc)
{

    var svgnode;
    if (this.viewattrs.getAttr("CREATION") == 'clone') {
	if (!this.arc_svg_prototype) {
	    this.arc_svg_prototype	= new Object();
	}
	if (!this.arc_svg_prototype[garc.type]) {
	    this.arc_svg_prototype[garc.type] =
		this._createArc(garc.type);
	}
	svgnode	= this.arc_svg_prototype[garc.type].cloneNode(false);
    } else {
	svgnode = this._createArc(garc.type); 
    }

    // create view arc
    var varc = {
	id: garc.id,
	garc: garc, 
	svg: svgnode,
	viewattr: new Array(),
    }
    this.arcs[varc.id] = varc;
    svgnode.setAttributeNS(null, "id", varc.id);
    this.arc_group.appendChild(svgnode);
    this.arcview.setAttribute(varc, "visible", true);
};

DefaultGraphView.prototype._createNodeGroup = function ()
{
    var nodegroup = svgdoc.createElementNS(svgns, "g");
    nodegroup.style.setProperty("display", "inline");
    return nodegroup;
};

DefaultGraphView.prototype._createNode = function (type)
{
    var svgnode;

    switch (this.viewattrs.getAttr("NODE_STYLE")) {
	case "circle":
	    var r = this.viewattrs.getAttr("NODE_RADIUS");

	svgnode = svgdoc.createElementNS(svgns, "circle");
	svgnode.setAttributeNS(null, "cx", 0);
	svgnode.setAttributeNS(null, "cy", 0);
	svgnode.setAttributeNS(null, "r", r);
	break;
	case "square":
	    var r = this.viewattrs.getAttr("NODE_RADIUS");

	svgnode = svgdoc.createElementNS(svgns, "rect");
	svgnode.setAttributeNS(null, "x", -r);
	svgnode.setAttributeNS(null, "y", -r);
	svgnode.setAttributeNS(null, "height", 2*r);
	svgnode.setAttributeNS(null, "width", 2*r);
	break;
	default:

	svgnode = svgdoc.createElementNS(svgns, "use");
	svgnode.setAttributeNS(xlinkns, "xlink:href", 
		"#nodetype_" + type);
	break;
    }

    svgnode.style.setProperty("display", "inherit");
    var colour = graph.model.domain.getNodeType(type).getAttr("rgb");
    svgnode.style.setProperty("fill", colour);

    return svgnode;
};

DefaultGraphView.prototype._createArc = function (type)
{
    var svgnode = svgdoc.createElementNS(svgns, "line");
    var colour = graph.model.domain.getArcType(type).getAttr("rgb");
    svgnode.style.setProperty("stroke", colour);
    return svgnode;
};


/**
 * given an SVGElement representing a node, returns the corresponding view
 * node
 */
DefaultGraphView.prototype.getNodeFromSVG = function (svgnode)
{
    var str = svgnode.parentNode.getAttributeNS(null, "id");
    var id = str.match(/^(.*)group/)[1];
    return this.nodes[id];
};

/**
 * return an array of refs to view nodes corresponding to the given graph
 * nodes
 * 
 * @param   gnodes  array of graph nodes
 */
DefaultGraphView.prototype.getViewNodes = function (gnodes) 
{
    var arr = new Array();
    var vnode;
    for (var i in gnodes) {
	if (gnodes[i]!= null && (vnode = this.nodes[gnodes[i].id])) 
	    arr[i] = vnode; 
    }
    return arr;
}

/**
 * return an array of refs to view arcs corresponding to the given graph
 * arcs
 * 
 * @param   garcs  array of graph arcs
 */
DefaultGraphView.prototype.getViewArcs = function (garcs) 
{
    var arr = new Array();
    var varc;
    for (var i in garcs) {
	if ((varc= this.arcs[garcs[i].id])) 
	    arr[i] = varc; 
    }
    return arr;
}

/**
 * redraw all nodes and arcs in the view 
 */
DefaultGraphView.prototype.redraw = function ()
{
    for (var i in this.arcs) {
	this.arcview.redraw(this.arcs[i]);
    }
};

/******
 *
 *   updateNodeSelection
 *
 *   called when the node selection is changed
 *  expect GraphSelectionEvent
 *******/
DefaultGraphView.prototype.updateNodeSelection = function (e) 
{
    for (var i = 0; i < e.elements.length; i++) {
	this.nodeview.setNodeIndication(e.elements[i], e.isAdded[i]);
    }
};

/*****
 *
 *   getGraphSpaceCoordinate  (from KevLinDev.com::Mouser.js)
 *
 *   returns an SVGPoint containing the graph space coord of a given 
 *   point in the Viewport space. 
 *
 *****/
DefaultGraphView.prototype.getGraphSpaceCoordinate = function(x, y) {
    return transformFromScreenSpace(x,y,this.graph_contents);
};

//////////////////////////////////////////////
//	  
//	NodeView 
//
//////////////////////////////////////////////

/* 
 * Inner class of the view which acts as a flyweight for nodes 
 */
function NodeView(view) 
{
    if (arguments.length > 0) {
	this.init(view);
    }
}

NodeView.prototype.init = function (view) 
{
    this.view = view;
};

NodeView.prototype.getRadius = function (node) 
{
    return parseFloat(this.view.viewattrs.getAttr("NODE_RADIUS"));
};

/**
 * given a view node returns its location as a point
 */
NodeView.prototype.getLocation = function (vnode) 
{
    var nodegroup = vnode.svg.parentNode;
    var ctm = nodegroup.getCTM();
    var point = new Point(ctm.e, ctm.f);

    return point;
};

/**
 * set location of the view node with the given id 
 */ 
NodeView.prototype.setInitialLocation = function (id, x, y) 
{
    //TODO store initial location?
    var vnode = this.view.nodes[id];
    if (vnode) this._setLocation(vnode, x, y);
}


/**
 * set location of the view node 
 */ 
NodeView.prototype._setLocation = function (vnode, x, y) 
{
    //TODO use the 'transform' property 
    //ie.. nodegroup.transform.baseVal.consolidate().setTranslate(point.x, point.y);

    var nodegroup = vnode.svg.parentNode;
    var transform = "translate(" + x + " " + y + ")";
    nodegroup.setAttributeNS(null, "transform", transform);

}

/**
 * set location and make sure to call redraw on all locateable attachments 
 */
NodeView.prototype.setLocation = function (vnode, x, y) 
{
    this._setLocation(vnode, x, y);
    this._redrawArcs(vnode);   
    this.redraw(vnode);
};

NodeView.prototype.isEnclosedBy = function (vnode, x1, y1, x2, y2) 
{
    var loc = this.getLocation(vnode);
    return ( ( (x1 < loc.x && loc.x < x2) && (y1 < loc.y && loc.y < y2) ) ||
	    ( (x1 > loc.x && loc.x > x2) && (y1 > loc.y && loc.y > y2) ) );
};

/**
 * true of the given graph node is in the view
 */
NodeView.prototype.isInView = function (gnode)
{
    if (this.view.nodes[gnode.id]) return true;
    return false;
};

/**
 * redraw the node 
 */
NodeView.prototype.redraw = function (vnode)
{
    if (this.getAttribute(vnode, "visible")) {
	vnode.svg.parentNode.style.setProperty("display", "inline");
    } else {
	vnode.svg.parentNode.style.setProperty("display", "none");
    }
};

NodeView.prototype.isVisible = function (vnode)
{
    return (this.getAttribute(vnode, "visible") == true);
};

/**
 * true if the node can be selected
 */
NodeView.prototype.isSelectable = NodeView.prototype.isVisible;


/**
 * make the node visible, and redraw its arcs
 */
NodeView.prototype.setVisible = function (vnode, visible)
{
    if (this.getAttribute(vnode, "visible") != visible) {
	this.setAttribute(vnode, "visible", visible);  
	this._redrawArcs(vnode);
	this.redraw(vnode);
    }
};

NodeView.prototype.translate = function (vnode, delta_x, delta_y) 
{
    var current = this.getLocation(vnode);
    this.setLocation(vnode, current.x + delta_x, current.y + delta_y);
};


//TODO replace following by AttrSet
NodeView.prototype.removeAttribute = function (vnode, attr)
{
    delete vnode.viewattr[attr];
};

NodeView.prototype.setAttribute = function (vnode, attr, value)
{
    vnode.viewattr[attr] = value;
};

NodeView.prototype.getAttribute = function (vnode, attr)
{
    return vnode.viewattr[attr];
};


/**
 * set node indication
 **/
NodeView.prototype.setNodeIndication = function(vnode, indicate) 
{
    var indicator_id = vnode.id + "_indicator";
    if (indicate) { 
	var rect = svgdoc.createElementNS(svgns, "rect");
	rect.setAttributeNS(null, "id", indicator_id);
	var radius = this.getRadius(vnode);
	var border = 4;
	rect.setAttributeNS(null, "width", (radius + border)*2 );
	rect.setAttributeNS(null, "height", (radius + border)*2 );
	rect.setAttributeNS(null, "x", -(radius + border));
	rect.setAttributeNS(null, "y", -(radius + border));
	rect.setAttributeNS(null, "class", "nodeindication");
	vnode.svg.parentNode.insertBefore(rect, vnode.svg.parentNode.firstChild);
    } else {
	var rect = svgdoc.getElementById(indicator_id);
	if (rect) {
	    vnode.svg.parentNode.removeChild(rect);
	}
    }
};

/**
 * redraw all arcs for the given node
 */
NodeView.prototype._redrawArcs = function (vnode) 
{
    //TODO should use model iterators
    for (var i in vnode.gnode.outgoing) {
	var varc = this.view.arcs[vnode.gnode.outgoing[i].id];
	if (varc) {
	    this.view.arcview.redraw(varc);
	}
    }
    for (var i in vnode.gnode.incoming) {
	var varc = this.view.arcs[vnode.gnode.incoming[i].id];
	if (varc) {
	    this.view.arcview.redraw(varc);
	}
    }
};
// END CLASS: NodeView 

//////////////////////////////////////////////
//	  
//	ArcView 
//
//////////////////////////////////////////////

/* 
 * Inner class of the view which acts as a flyweight for arcs 
 */
function ArcView(view) 
{
    if (arguments.length > 0) {
	this.init(view);
    }
}

ArcView.prototype.init = function (view) 
{
    this.view = view;
};

/**
 * redraw the given arc
 */
ArcView.prototype.redraw = function (varc)
{
    if(varc.garc.src==null || varc.garc.dst==null) return; 
    var svgnode = varc.svg;
    var src = this.view.nodes[varc.garc.src.id];
    var dst = this.view.nodes[varc.garc.dst.id];
    var nodeview = this.view.nodeview;

    // only draw arc iff both the src, dst, and arc itself are visible 
    if ( nodeview.isVisible(src) && nodeview.isVisible(dst) && 
	    this.isVisible(varc) ){

	var src_point = nodeview.getLocation(src);
	var dst_point = nodeview.getLocation(dst);
	var x1 = src_point.x; 
	var y1 = src_point.y; 
	var x2 = dst_point.x; 
	var y2 = dst_point.y; 

	// arc leaves from 'surface' of src node
	var radius = nodeview.getRadius(src);

	var vecx = x1 - x2;
	var vecy = y1 - y2;
	var len = Math.sqrt(Math.pow(vecx, 2) + Math.pow(vecy, 2));
	var ratio = (len == 0) ? 1 : (len - radius )/ len;

	var new_x1 = x2 + ratio * vecx;
	var new_y1 = y2 + ratio * vecy;

	// arc arrives at 'surface' of dst node'
	var radius = nodeview.getRadius(dst);  
	if (this.view.viewattrs.getAttr("GRAPH_STYLE") == 'directed') {
	    // shorten arc to allow for arrowhead
	    radius += this.view.arrowhead_len;
	}
	var vecx = x2 - x1;
	var vecy = y2 - y1;
	var len = Math.sqrt(Math.pow(vecx, 2) + Math.pow(vecy, 2));
	var ratio = (len == 0) ? 1 : (len - radius )/ len;

	var new_x2 = x1 + ratio * vecx;
	var new_y2 = y1 + ratio * vecy;

	svgnode.setAttributeNS(null, "x1", new_x1);
	svgnode.setAttributeNS(null, "y1", new_y1);
	svgnode.setAttributeNS(null, "x2", new_x2);
	svgnode.setAttributeNS(null, "y2", new_y2);

	svgnode.style.setProperty("display", "inline");
    } else {
	svgnode.style.setProperty("display", "none");
    }
};

/**
 * set arc visibility
 */
ArcView.prototype.setVisible = function (varc, visible)
{
    if (this.getAttribute(varc, "visible") != visible) {
	this.setAttribute(varc, "visible", visible);  
	this.redraw(varc);
    }
};

/**
 * true if arc is visibile
 */
ArcView.prototype.isVisible = function (varc)
{
    return (this.getAttribute(varc, "visible") == true);
};

/**
 * true of the given graph arc is in the view
 */
ArcView.prototype.isInView = function (garc)
{
    if (this.view.arcs[garc.id]) return true;
    return false;
};


//TODO replace following by AttrSet
ArcView.prototype.removeAttribute = function (varc, attr)
{
    delete varc.viewattr[attr];
};

ArcView.prototype.setAttribute = function (varc, attr, value)
{
    if(varc!= null) varc.viewattr[attr] = value;
};

ArcView.prototype.getAttribute = function (varc, attr)
{
    if(varc==null) return; 
    return varc.viewattr[attr];
};
// END CLASS: ArcView
// END CLASS: DefaultGraphView 

//////////////////////////////////////////////
//	  
//	 SVGImportGraphView
//
//////////////////////////////////////////////

inherit (DefaultGraphView, SVGImportGraphView);

/**
 * SVGImportGraphView extends the DefaultGraphView to build the view from a
 * well-defined SVG DocumentFragment.  See //TODO for more information.
 */
function SVGImportGraphView (model, svgnode) {
    if (arguments.length > 0) {
	this.init(model, svgnode);
    }
};

SVGImportGraphView.prototype.init = function (model, svgnode) 
{ 
    this.model = model;
    this.graph_pane = svgnode;

    SVGImportGraphView.superclass.init.call(this, model);

    //import view attrs
    var attributes = this.graph_contents.attributes;
    for ( var i = 0 ; i < attributes.length ; i++ ){
	var attr = attributes.item(i);
	if (attr.namespaceURI == ATTRNSVAL) {
	    //TODO handle datatypes?
	    //TODO could create a general parse for datatypes?
	    this.viewattrs.setAttr(attr.localName, attr.value);
	}
    }

    this.importModel(model);
    this.viewbox.setViewbox();
};

/**
 *  Override the superclass _createViewSVG since all the SVG content is
 *  already available
 */
SVGImportGraphView.prototype._createViewSVG = function ()
{
    this.graph_contents = svgdoc.getElementById("graph_contents");
    this.arc_group	    = svgdoc.getElementById("arcs");
    this.node_group	    = svgdoc.getElementById("nodes");
};

/**
 * assumes that the model associated with this view has also been imported
 * from the SVG
 */
SVGImportGraphView.prototype.importModel = function ()
{
    //TODO should use model iterators or something
    var model = this.model;
    for (var i in model.nodes) {
	var gnode = model.nodes[i];
	var svgnode = svgdoc.getElementById(gnode.id);
	if (svgnode) {
	    var vnode = {
		id: gnode.id,
		gnode: gnode,
		svg: svgnode,
		viewattr: new Array(),
	    }
	    this.nodes[vnode.id] = vnode;
	    this.nodeview.setAttribute(vnode, "visible", true);
	}
    }

    for (var i in model.arcs) {
	var garc = model.arcs[i];
	var svgnode = svgdoc.getElementById(garc.id);
	if (svgnode) {
	    var varc = {
		id: garc.id,
		garc: garc, 
		svg: svgnode,
		viewattr: new Array(),
	    }
	    this.arcs[varc.id] = varc;
	    this.arcview.setAttribute(varc, "visible", true);
	}
    }
};

//////////////////////////////////////////////
//	  
//	NodeLabels	
//
//////////////////////////////////////////////

/*
 * ecapsulate node labels for a particular view
 *  
 * a node label is either showing or hidden, but if the node to which the
 * label is attached is not visible then the label is also not visible 
 *
 */ 
function NodeLabels (view)
{
    if (arguments.length > 0) {
	this.init(view);
    }
}

NodeLabels.prototype.init = function (view) 
{
    this.view = view;
    this.labels = new Array(); // label data, indexed by node id. 

    // add event callbacks
    if (view.viewattrs.getAttr("LABEL_BEHAVIOUR") == "tooltip") {
	this.view.node_group.addEventListener('mouseover', this, false);
	this.view.node_group.addEventListener('mouseout', this, false);
    }
};


/**
 * create labels for the the array of view nodes passed in 
 */ 
NodeLabels.prototype.createLabels = function (nodes) 
{
    for (var i in nodes) {
	var node = nodes[i];
	var label = this._makeNodeLabel(node);
	this.labels[node.id] = 
	{
	    label: label,
	    visible: true
	};
	this.positionLabel(node);
    }
};

/* create and add label to the node.
 *  return SVG label. 
 * NOTE: assumes node.attribute.name is defined 
 */
NodeLabels.prototype._makeNodeLabel = function (vnode) 
{
    if (this.labels[vnode.id]) {
	return this.labels[vnode.id];
    }

    var label;
    label = svgdoc.createElementNS(svgns, "text");
    label.setAttributeNS(null, "id", "label" + vnode.id);
    label.setAttributeNS(null, "class", 'graphfont');
    label.style.setProperty("pointer-events", "none");

    label.appendChild(
	    svgdoc.createTextNode(
		this.view.model.nodehandler.getAttribute(vnode.gnode, "name")));
    vnode.svg.parentNode.appendChild(label);
    return label;
};

NodeLabels.prototype._destroyNodeLabel = function (vnode) 
{
    var label = svgdoc.getElementById('label' + vnode.id);
    if (label) {
	vnode.svg.parentNode.removeChild(label);
    }
    delete this.labels[vnode.id];
};

NodeLabels.prototype.handleEvent = function (evt) 
{
    var type = evt.type;
    var vnode;
    if ( !(vnode = this.view.getNodeFromSVG(evt.getTarget())) ) {
	throw new GeneralError("NodeLabels: Unsupported target: " + evt.getTarget());
	return;
    }

    switch (type){
	case 'mouseover':
	    this.setVisible(vnode, true);
	    break;
	case 'mouseout':
	    this.setVisible(vnode, false);
	    break;
	default:
	    throw new GeneralError("NodeLabels: Unsupported event: " + type + " on " +
		    target);
	    break;
    }
};

/**
 * sets default visibility of the node
 * store the default visibility in the view attribute "dflt_lbl_vis"
 *
 * @param   ids	array of node id
 * @param   vis	default visiblity 
 */
NodeLabels.prototype.setDefaultVisibility = function (ids, vis)
{
    for (var i in ids) {
	var vnode;
	if ((vnode = this.view.nodes[ids[i]])) {
	    this.view.nodeview.setAttribute(vnode, 'dflt_lbl_vis', vis);
	}
    }
};

NodeLabels.prototype.showDefault = function (nodes)
{
    for (var i in nodes) {
	var node = nodes[i];
	var vis = this.view.nodeview.getAttribute(node, 'dflt_lbl_vis');
	this.setVisible(node, vis);
    }
};

/**
 * set the visibility of label associated with the node
 * returns false if label for node doesn't exist, true otherwise
 */
NodeLabels.prototype.setVisible = function (node, visible)
{
    var id = node.id;
    if (!this.labels[id]) {
	return false;
    }
    this.labels[id]['visible'] = visible;
    var label = this.labels[id]['label'];
    this._setLabelVisible(label, visible);
    return true;
};

NodeLabels.prototype._setLabelVisible = function (label, visible)
{
    var visibility = (visible) ? 'inherit' : 'none';
    label.style.setProperty("display", visibility);
    return true;
};

NodeLabels.prototype.setAllVisible = function (visible)
{
    for (var i in this.labels){
	var label = this.labels[id]['label'];
	this._setLabelVisible(label, true);
    }
};

/* position the label */
NodeLabels.prototype.positionLabel = function (node)
{
    if (!this.labels[node.id]) {
	return false;
    }

    var label = this.labels[node.id]['label']; 
    var base_x = 0; 
    var base_y = 0;  
    var node_r = this.view.nodeview.getRadius(node);
    var x = Math.floor(base_x + 1.4 * node_r); 
    var y = Math.floor(base_y - 1.4 * node_r); 

    label.setAttributeNS(null, "x", x);
    label.setAttributeNS(null, "y", y);

    return true;
};

//////////////////////////////////////////////
//	  
//	   Rubberband
//
//////////////////////////////////////////////
/*****
 *
 *    constructor
 *
 *****/
function Rubberband(controller) 
{
    if ( arguments.length > 0 ) {
	this.init(controller);
    }
}

/*****
 *
 *    init
 *
 *****/
Rubberband.prototype.init = function(controller) 
{
    this.controller = controller;
    this.view = this.controller.view;

    this.rubberband = svgdoc.createElementNS(svgns, "rect");
    this.rubberband.setAttributeNS(null, "id", "graph_rubberband");
    this.rubberband.setAttributeNS(null, "class", "rubberband");
    this.view.graph_pane.appendChild(this.rubberband);

    this.nodes = new GESet();
};

Rubberband.prototype.handleEvent = function(evt) 
{
    var type = evt.type; 
    switch (type) {
	case 'mousemove': this.stretch(evt); break;
	case 'mouseup'  : this.endStretch(evt); break;
	default: throw 
		 new GeneralError("Rubberband: unsupported event type"); 
		 break;
    }
};

Rubberband.prototype.beginStretch = function (evt) 
{
    this.lastPoint = this.view.getGraphSpaceCoordinate(evt.clientX, evt.clientY); 
    viewport.addEventListener('mousemove', this, false);
    viewport.addEventListener('mouseup', this, false);
    this.rubberband.setAttributeNS(null, "x", this.lastPoint.x);
    this.rubberband.setAttributeNS(null, "y", this.lastPoint.y);
    this.rubberband.setAttributeNS(null, "height", 0);
    this.rubberband.setAttributeNS(null, "width", 0);
    this.rubberband.style.setProperty('display', 'inline');
};

Rubberband.prototype.stretch = function (evt) 
{
    var pos = this.view.getGraphSpaceCoordinate(evt.clientX, evt.clientY);
    var x;
    var y;
    var width = pos.x - this.lastPoint.x;
    var height = pos.y - this.lastPoint.y;
    if (width < 0 ) {
	x = this.lastPoint.x + width;
	width = -width;
	this.rubberband.setAttributeNS(null, "x", x);
    }
    if (height < 0 ) {
	y = this.lastPoint.y + height;
	height = -height;
	this.rubberband.setAttributeNS(null, "y", y);
    }
    this.rubberband.setAttributeNS(null, "height", height);
    this.rubberband.setAttributeNS(null, "width", width);
};

Rubberband.prototype.endStretch = function (evt) 
{
    viewport.removeEventListener('mousemove', this, false);
    viewport.removeEventListener('mouseup', this, false);
    this.rubberband.style.setProperty('display', 'none');

    /* fetch selected nodes */
    var enclosed = this.getEnclosureList();
    this.controller.nodeselectionhandler.endRubberband(enclosed);
};

/* return an array of nodes enclosed by the rubberband. 
 * NOTE: returns only those nodes which are selectable 
 */
Rubberband.prototype.getEnclosureList = function()
{
    // get enclosure list
    //this.svgNode.getEnclosureList(this.rubberband, null);
    var bbox = this.rubberband.getBBox();
    var x1 = bbox.x; 
    var y1 = bbox.y; 

    var width = bbox.width;
    var height = bbox.height;

    var x2 = x1 + width;
    var y2 = y1 + height;

    var nodes = this.controller.view.nodes;
    var nodeview = this.view.nodeview;
    var enclosed = new GESet();
    for (var node in nodes) {
	if (nodeview.isSelectable(nodes[node]) && 
		nodeview.isEnclosedBy(nodes[node], x1, y1, x2, y2)) {
	    enclosed.add(nodes[node]);
	}
    }
    return enclosed.toArray();
};
// END CLASS: RubberBand 

//////////////////////////////////////////////
//	  
//	  NodeDragger 
//
//////////////////////////////////////////////
/*****
 *
 *    constructor
 *
 *****/
function NodeDragger(controller) 
{
    if ( arguments.length > 0 ) {
	this.init(controller);
    }
}

/*****
 *
 *    init
 *
 *****/
NodeDragger.prototype.init = function(controller) 
{
    this.lastPoint	= null; 
    this.controller	= controller;
    this.view		= this.controller.view;
};

NodeDragger.prototype.handleEvent = function (evt)
{
    switch (evt.type) {
	case 'mousemove': this.drag(evt); break;
	case 'mouseup'  : this.endDrag(evt); break;
	default: throw 
		 new GeneralError("NodeDragger: unsupported event type"); 
		 break;
    }
};

/*****
 *
 *   beginDrag
 *
 *****/
NodeDragger.prototype.beginDrag = function(evt) 
{
    viewport.addEventListener("mousemove", this, false);
    viewport.addEventListener("mouseup", this, false);
    this.lastPoint = this.view.getGraphSpaceCoordinate( evt.clientX, evt.clientY );
};

/*****
 *
 *   drag
 *
 *****/
NodeDragger.prototype.drag = function(evt) 
{

    var newPoint = this.view.getGraphSpaceCoordinate( evt.clientX, evt.clientY );

    var delta_x = newPoint.x - this.lastPoint.x; 
    var delta_y = newPoint.y - this.lastPoint.y;

    this.lastPoint = newPoint;

    var iter = this.controller.nodeselection.iterator();
    iter.first();
    do {
	   this.view.nodeview.translate(iter.currentItem(), delta_x, delta_y);
    } while (iter.next());
    
    /*else {
    	   for(var i in graph.view.nodes){
                 this.view.nodeview.translate(graph.view.nodes[i], delta_x, delta_y);
    	   }
    } */ 
};


/*****
 *
 *   endDrag
 *
 *****/
NodeDragger.prototype.endDrag = function(evt) 
{
    viewport.removeEventListener('mousemove', this, false);
    viewport.removeEventListener('mouseup', this, false);
    move = 0;
};
// END CLASS: NodeDragger 

//////////////////////////////////////////////
//	  
//	 DefaultNodeSelectionHandler
//
//////////////////////////////////////////////

/**
 *  NodeSelectionHandler deals with the behaviour of node selections as the
 *  user makes adjustments throught the UI and notifies the view of any
 *  changes.
 * 
 *  The handler encapsulates the rules for adding or removing nodes from a
 *  selection based on keyboard state (ie. whether SHIFT or CTRL is held) and
 *  current selection size, etc.  Any changes to the current selection set are
 *  relayed to the view via a GraphSelectionEvent.
 *
 */
function DefaultNodeSelectionHandler (controller) 
{
    if (arguments.length > 0) {
	this.init(controller);
    }
};

DefaultNodeSelectionHandler.prototype.init = function(controller) 
{
    this.controller = controller;
    this.background = this.controller.background;
    //TODO stick to controller
    this.rgraph = this.controller;
};

/*****
 *
 *    nodeSelected
 *
 * DESC: respond to user selecting a node
 *****/
DefaultNodeSelectionHandler.prototype.nodeSelected = function(evt) 
{
    var node = this.controller.view.getNodeFromSVG(evt.getTarget());
    var type = evt.type;

    switch (type) {
	case 'mousedown': 
	    if (!evt.shiftKey) {
		(new SelectSetCommand(this.controller, new Array(node))).execute();
	    } else {
		// add this node when the user moves or mouseup's on the
		// background
		this.preSelectedNode = node;
		this.background.addEventListener('mousemove', this, false);
		this.background.addEventListener('mouseup', this, false);
	    }
	    break;
	default: break;
    }
};

DefaultNodeSelectionHandler.prototype.handleEvent = function(evt)
{
    var type = evt.type;

    if (evt.getTarget() != this.background) return;

    switch (type) {
	case 'mousemove':
	    if (evt.shiftKey && this.preSelectedNode != null) {
		var node = this.preSelectedNode;

		this.preSelectedNode = null;
		this.background.removeEventListener('mousemove', this, false);
		this.background.removeEventListener('mouseup', this, false);

		(new SelectAddCommand(this.rgraph, new Array(node))).execute();
	    }
	    break;
	case 'mouseup':
	    if (evt.shiftKey && this.preSelectedNode != null) {
		var node = this.preSelectedNode;

		this.preSelectedNode = null;
		this.background.removeEventListener('mousemove', this, false);
		this.background.removeEventListener('mouseup', this, false);

		this.setSelectionDifference(new Array(node));
	    }
	    break;
	default: break;
    }
};

DefaultNodeSelectionHandler.prototype.startRubberband = function(evt)
{
    this.rubberband_shift = evt.shiftKey;

    if (!evt.shiftKey) {
	(new SelectionClearCommand(this.controller)).execute();
    } else {
    }
};

DefaultNodeSelectionHandler.prototype.endRubberband = function(nodes)
{
    if (!this.rubberband_shift) { 
	this.setSelection(nodes);
    } else {
	this.setSelectionDifference(nodes);
    }
};

/**
 * set the selection.  expects an array of view nodes.
 */
DefaultNodeSelectionHandler.prototype.setSelection = function (nodes)
{
    this.clearSelection();
    this.addNodes(nodes); 
};

/**
 * add nodes which are not already in the selection removes the nodes
 * otherwise.
 */
DefaultNodeSelectionHandler.prototype.setSelectionDifference= function(nodes) 
{
    var node_list = new Array();
    var isAdded = new Array(); 

    for (var i in nodes) {
	if(nodes[i].gnode.collapse == false) {
	   node_list.push(nodes[i]);
	   var was_selected = !this.rgraph.nodeselection.add(nodes[i]);	
	
	   if (was_selected) {
	       this.rgraph.nodeselection.remove(nodes[i]);
	       isAdded.push(false);
	   } else {
	       isAdded.push(true);
	   }
	} 
    }
    this.rgraph.view.updateNodeSelection(new GraphSelectionEvent(node_list,
		isAdded));
};


/**
 * add nodes to selection 
 */
DefaultNodeSelectionHandler.prototype.addNodes = function(nodes) 
{
    var node_list = new Array();
    var isAdded = new Array(); 

    for (var i in nodes) {
	var was_selected = !this.rgraph.nodeselection.add(nodes[i]);	 
	if (!was_selected) {
	    node_list.push(nodes[i]);
	    isAdded.push(true);
	}
    }

    this.rgraph.view.updateNodeSelection(new GraphSelectionEvent(node_list,
		isAdded));
};

/**
 * remove all nodes from selection 
 */
DefaultNodeSelectionHandler.prototype.clearSelection = function() 
{
    var node_list = this.rgraph.nodeselection.toArray();
    var isAdded = new Array();
    for (var i = 0; i < node_list.length; i++) isAdded[i] = false;	
    this.rgraph.nodeselection.clear();
    this.rgraph.view.updateNodeSelection(new GraphSelectionEvent(node_list,
		isAdded));
};

/**
 * remove nodes from the selection 
 */
DefaultNodeSelectionHandler.prototype.removeNodes = function(nodes) 
{
    var node_list = new Array();
    var isAdded = new Array(); 

    for (var i = 0; i < nodes.length; i++) {
	var was_selected = this.rgraph.nodeselection.remove(nodes[i]);	 
	if (was_selected) {
	    node_list.push(nodes[i]);
	    isAdded.push(false);
	}
    }

    this.rgraph.view.updateNodeSelection(new GraphSelectionEvent(node_list,
		isAdded));
};
// END CLASS: DefaultNodeSelectionHandler

//////////////////////////////////////////////
//	  
//	 GraphSelectionEvent
//
//////////////////////////////////////////////


function GraphSelectionEvent (elements, isAdded) 
{
    this.elements = elements; 
    this.isAdded = isAdded;
}
// END CLASS: GraphSelectionEvent 

//////////////////////////////////////////////
//	  
//	 Point
//
//////////////////////////////////////////////
function Point (x,y) {
    this.x = x || 0;
    this.y = y || 0;
};
// END CLASS: Point
