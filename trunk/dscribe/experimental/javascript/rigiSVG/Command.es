/************************************************************************
*                                                                       *
*                             Commands                                  *
*                                                                       *
*************************************************************************/

function inherit (parent, child)
{
    child.prototype = new parent();
    child.prototype.constructor = child;
    child.superclass = parent.prototype;
}

////////////////////////
//    CommandHistory
////////////////////////

function CommandHistory () {
    this.history = new Array();
    this.redo = new Array();
    this._clearRedo = true;
}

CommandHistory.prototype.push = function (command) 
{
    this.history.push(command);

    if (this._clearRedo && this.redo.length > 0) {
	this.redo = new Array();
    }
};

CommandHistory.prototype.undo = function () 
{
    var command = this.history.pop();
    if (command) {
	this.redo.push(command);
	command.undo();
	return true;
    } else {
	return false;
    }
};

CommandHistory.prototype.redolast = function () 
{
    var command = this.redo.pop();
    if (command) {
	this._clearRedo = false;
	command.redo(); // assume command puts itself back on undo list
	this._clearRedo = true;
	return true;
    } else {
	return false;
    }
};

////////////////////////
//    Command
////////////////////////

/* the following defines the Command Interface */

function Command () {};
Command.prototype.desc = "[no description]";
Command.prototype.init = function () { };
Command.prototype.execute = function () { };
Command.prototype.undo = function () { };
Command.prototype.addToHistory = function () { history.push(this.clone()); };
Command.prototype.handleEvent = function () 
{ 
    try { 
	this.execute(); 
    } catch (e) {
	alert( e.message );
    }
};
/**
 * Commands which produce different results each time they are executed must
 * override this method.
 */ 
Command.prototype.redo = function () { this.execute() };
Command.prototype.clone = function () 
{
    var clone = new Object();
    for (var i in this) clone[i] = this[i];
    return clone;
}

////////////////////////////////////
//	AbstractPositionChangeCommand
////////////////////////////////////

inherit(Command, AbstractPositionChangeCommand);

function AbstractPositionChangeCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

AbstractPositionChangeCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

AbstractPositionChangeCommand.prototype._storeStateData = function (statedata, nodes)
{
    var iter = nodes.iterator();
    iter.first();
    do {
	node = iter.currentItem();
	var loc = this.graph.view.nodeview.getLocation(node);
	statedata.push( { node: node, x: loc.x, y: loc.y } );
    } while (iter.next());
}

/**
 * nodes is a GESet of the nodes which will be moved
 */
AbstractPositionChangeCommand.prototype.storeUndodata = function (nodes)
{
    this.undodata = new Array();
    this._storeStateData(this.undodata, nodes);
}

/**
 * nodes is a GESet of the nodes that have been moved
 */
AbstractPositionChangeCommand.prototype.storeRedodata = function (nodes)
{
    this.redodata = new Array();
    this._storeStateData(this.redodata, nodes);
}

AbstractPositionChangeCommand.prototype._restorePositions = function (statedata)
{
    for (var i in statedata) {
	var state = statedata[i];
	this.graph.view.nodeview.setLocation(state.node, state.x, state.y);
    }
};

AbstractPositionChangeCommand.prototype.undo = function ()
{
    if (this.undodata) 
	this._restorePositions(this.undodata);
}

AbstractPositionChangeCommand.prototype.redo = function ()
{
    if (this.redodata) 
	this._restorePositions(this.redodata);
    this.addToHistory();
}

///////////////////////////////////
//	SelectionGridLayout
////////////////////////////////////

inherit(AbstractPositionChangeCommand, SelectionGridLayoutCommand);

// layout selected nodes in to a square grid pattern .. where the grid size
// is the smallest square that will contain the items
function SelectionGridLayoutCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectionGridLayoutCommand.prototype.init = function (graph) 
{
    SelectionGridLayoutCommand.superclass.init.call(this, graph);
};

SelectionGridLayoutCommand.prototype.execute = function () 
{
    var nodeselection = this.graph.nodeselection;
    if (nodeselection.size() <= 1) {
	throw new GeneralError('You must select at least two nodes');
    } else {
	this.storeUndodata(this.graph.nodeselection);

	var ncols = Math.ceil(Math.sqrt(nodeselection.size()));
	var nrows = ncols;
	var ncomponents = nodeselection.size();

	var iter = nodeselection.iterator();
	var NodeView = graph.view.nodeview;

	iter.first();
	var somenode = iter.currentItem();
	var startpos = NodeView.getLocation(somenode);  
	var w = NodeView.getRadius(somenode);	
	var h = w;
	var hgap = 2 * w;
	var vgap = hgap;


	var i = 0;
	iter.first(); 
	do {
	    var node = iter.currentItem();
	    // change position
	    var row = Math.floor(i / ncols);
	    var col = i % ncols;

	    var x = startpos.x + col * (w + hgap);
	    var y = startpos.y + row * (h + vgap);

	    NodeView.setLocation(node, x, y); 

	    i++;
	} while (iter.next());

	this.storeRedodata(this.graph.nodeselection);
	this.addToHistory();
    }
};

////////////////////////////////////
//	SpringLayoutCommand
////////////////////////////////////

inherit(AbstractPositionChangeCommand, SpringLayoutCommand);

function SpringLayoutCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SpringLayoutCommand.prototype.init = function (graph, iterations) 
{
    SpringLayoutCommand.superclass.init.call(this, graph);

    this.C1 = 2.0;
    this.C2 = 1.0;
    this.C3 = 1.0;
    this.C4 = 0.1;
    this.iterations = iterations | 100 ; 
    this.vertices = null;
    this.GEIndexAttr = "SpringLayoutIndex"; // identify the node by index
};

/**
 *
 *
 */
SpringLayoutCommand.prototype.execute = function () 
{

    this.vertices = new Array()
    var nodeselection = this.graph.nodeselection;
    if (nodeselection.size() <= 1) {
	throw new GeneralError('You must select at least two nodes');
    } else {
	this.storeUndodata(this.graph.nodeselection);

	this.initVertexData(nodeselection);

	for ( var i = 0; i < this.iterations ; i++ ) {
	    for (var j = 0 ; j < this.vertices.length ; j++) {
		var v = this.vertices[j];
		var force = this.getForce(v);
		this.moveVertex(v, force, (i == this.iterations - 1));
	    }
	}
	this.storeRedodata(this.graph.nodeselection);
	this.addToHistory();
    }
};


/**
 *
 */
SpringLayoutCommand.prototype.getForce = function (v) 
{
    var force_x = 0; // vector 
    var force_y = 0;
    // attraction
    for(var i = 0 ; i < v.adjacent.length ; i++){
	var u_index =  this.graph.view.nodeview
	    .getAttribute(v.adjacent[i], this.GEIndexAttr);
	var u = this.vertices[u_index];

	var uv_x = v.pos.x - u.pos.x;
	var uv_y = v.pos.y - u.pos.y;
	var d = Math.sqrt(uv_x * uv_x + uv_y * uv_y);
	var f = this.C1 * Math.log(d/this.C2);
	force_x += f * ( uv_x / d );
	force_y += f * ( uv_y / d );

    }

    // repulsion of nonadjacent
    for(var i = 0 ; i < v.nonadjacent.length ; i++){
	var u_index =  this.graph.view.nodeview
	    .getAttribute(v.nonadjacent[i], this.GEIndexAttr);
	var u = this.vertices[u_index];

	var uv_x = v.pos.x - u.pos.x;
	var uv_y = v.pos.y - u.pos.y;
	var d = Math.sqrt(uv_x * uv_x + uv_y * uv_y);
	var f = this.C3 / (d * d);

	force_x -= f * ( uv_x / d );
	force_y -= f * ( uv_y / d );
    }

    return {x: force_x, y: force_y};
};

SpringLayoutCommand.prototype.perturb = function (force) 
{
    return Math.random() * force;
};
/**
 *
 */
SpringLayoutCommand.prototype.moveVertex= function (v, force, paint) 
{
    v.pos.x += force.x * this.C4 + this.perturb(force.x);
    v.pos.y += force.y * this.C4 + this.perturb(force.y);

    if (paint) this.graph.view.nodeview.setLocation(v.node, v.pos.x, v.pos.y);
};

/**
 * accepts a set of nodes, creates this.vertices 
 *
 */
SpringLayoutCommand.prototype.initVertexData = function (set) 
{
    var iter = set.iterator();
    iter.first();
    do {
	var node = iter.currentItem();

	var adjacent = new GESet();
	for (var j in node.incoming) {
	    var n = node.incoming[j].src;
	    adjacent.add(n);
	};
	for (var j in node.outgoing) {
	    var n= node.outgoing[j].dst;
	    adjacent.add(n);
	};

	var nonadjacent = new GESet();
	var iter2 = set.iterator();
	iter2.first();
	do {
	    var n = iter2.currentItem();
	    if (!adjacent.contains(n) && n != node){
		nonadjacent.add(n);
	    }
	} while (iter2.next());

	var index = this.vertices.length;
	this.vertices[index] = { 
	    node: node, 
	    pos: {x: Math.random(), y: Math.random()}, 
	    adjacent: this.graph.view.getViewNodes(adjacent.toArray()),
	    nonadjacent: this.graph.view.getViewNodes(nonadjacent.toArray()),
	}
	// link graph node to this
	this.graph.view.nodeview.
	    setAttribute(node, this.GEIndexAttr, index);
    } while (iter.next());
};

////////////////////////////////////
//	SelectCommand
////////////////////////////////////
/**
 *  abstract class to handle selection commands
 */

inherit(Command, SelectCommand);

function SelectCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

SelectCommand.prototype.execute = function () 
{
    // store undo data
    this.undodata = this.graph.nodeselection.toArray();
    this.addToHistory();
};


SelectCommand.prototype.undo = function ()
{
    this.graph.nodeselectionhandler.setSelection(this.undodata);
};

////////////////////////////////////
//	SelectAddCommand	
////////////////////////////////////
/**
 * Add to the current selection
 *
 */

inherit(SelectCommand, SelectAddCommand);

function SelectAddCommand (graph, nodes) {
    if (arguments.length > 0) {
	this.init(graph, nodes);
    }
}

SelectAddCommand.prototype.init = function (graph, nodes) 
{
    this.graph = graph;
    this.nodes = nodes;
};

SelectAddCommand.prototype.execute = function () 
{
    SelectAddCommand.superclass.execute.call(this);

    // update selection
    this.graph.nodeselectionhandler.addNodes(this.nodes);

};

////////////////////////////////////
//	SelectSetCommand	
////////////////////////////////////
/**
 * set the current selection
 */

inherit(SelectCommand, SelectSetCommand);

function SelectSetCommand (graph, nodes) {
    if (arguments.length > 0) {
	this.init(graph, nodes);
    }
}

SelectSetCommand.prototype.init = function (graph, nodes) 
{
    this.graph = graph;
    this.nodes = nodes;
};

SelectSetCommand.prototype.execute = function () 
{
    SelectSetCommand.superclass.execute.call(this);

    // update selection
    this.graph.nodeselectionhandler.clearSelection();
    this.graph.nodeselectionhandler.addNodes(this.nodes);
};

SelectSetCommand.prototype.undo = function () 
{
    SelectSetCommand.superclass.undo.call(this);
}

SelectSetCommand.prototype.redo = function () 
{
    SelectSetCommand.superclass.redo.call(this);
}

////////////////////////////////////
//	SelectAllCommand	
////////////////////////////////////

inherit(SelectSetCommand, SelectAllCommand);

function SelectAllCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectAllCommand.prototype.init = function (graph) 
{
    this.graph = graph;
    SelectAllCommand.superclass.init(graph, graph.view.nodes);
};

SelectAllCommand.prototype.execute = function () 
{
    var nodeArray = new Array();
    for(var i in this.graph.getModel().nodes){
       if(this.graph.getModel().nodes[i].collapse == false)
         nodeArray.push(this.graph.getModel().nodes[i]);
    }
    this.graph.nodeselectionhandler.setSelection(
	    this.graph.view.getViewNodes(nodeArray));
};

////////////////////////////////////
//	SelectDeadCodeCommand
////////////////////////////////////

inherit(SelectCommand, SelectDeadCodeCommand);

function SelectDeadCodeCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectDeadCodeCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

SelectDeadCodeCommand.prototype.execute = function () 
{
    SelectDeadCodeCommand.superclass.execute.call(this);

    var deadnodes = new Array();
    var nodes = this.graph.getModel().nodes;

    for (var i in nodes){
	if ( (nodes[i].outgoing.length == 0) &&
		(nodes[i].incoming.length == 0) &&
		  (nodes[i].collapse == false)) {
	    deadnodes.push(nodes[i]);
	}
    }
    this.graph.nodeselectionhandler.setSelection(
	    this.graph.view.getViewNodes(deadnodes));
};


////////////////////////////////////
//	SelectionClearCommand
////////////////////////////////////

inherit(SelectCommand, SelectionClearCommand);

function SelectionClearCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectionClearCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

SelectionClearCommand.prototype.execute = function () 
{
    SelectionClearCommand.superclass.execute.call(this);
    this.graph.nodeselectionhandler.setSelection([]);
};

////////////////////////////////////
//	SelectComplementCommand
////////////////////////////////////

inherit(SelectCommand, SelectComplementCommand);

function SelectComplementCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectComplementCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

SelectComplementCommand.prototype.execute = function () 
{
    SelectComplementCommand.superclass.execute.call(this);
    var model = this.graph.getModel();
    //TODO call appropriate command.. better yet, this should be a subclass of
    //the approriate command
    this.graph.nodeselectionhandler.setSelectionDifference(
	    this.graph.view.getViewNodes(model.nodes));
};

////////////////////////////////////
//	SelectNodesByAttributeCommand
////////////////////////////////////

inherit(SelectCommand, SelectNodesByAttributeCommand);

function SelectNodesByAttributeCommand (graph, attr, value) {
    if (arguments.length > 0) {
	this.init(graph, attr, value);
    }
}

SelectNodesByAttributeCommand.prototype.init = function (graph, attr, value) 
{
    this.graph = graph;
    this.attr = attr;
    this.value = value
};

SelectNodesByAttributeCommand.prototype.execute = function () 
{
    SelectNodesByAttributeCommand.superclass.execute.call(this);
    var model = this.graph.getModel();
    var gnodes = model.getNodesByAttribute(this.attr, this.value).toArray(); 
    for(var i in gnodes){
       var tempNode = gnodes.pop();
       if(tempNode.collapse == false) gnodes.push(tempNode);
    }
    this.graph.nodeselectionhandler.setSelection(
	    this.graph.view.getViewNodes(gnodes));
};

////////////////////////////////////
//	SelectNodesByTypeCommand
////////////////////////////////////

inherit (SelectNodesByAttributeCommand, SelectNodesByTypeCommand);

function SelectNodesByTypeCommand (graph, type) {
    if (arguments.length > 0) {
	this.init(graph, type);
    }
}

SelectNodesByTypeCommand.prototype.init = function (graph, type) 
{
    SelectNodesByTypeCommand.superclass.init.call(this, graph, "type", type);
};

SelectNodesByTypeCommand.prototype.execute = function () 
{
    SelectNodesByTypeCommand.superclass.execute.call(this);
};

////////////////////////////////////
//	SelectReverseTreeCommand
////////////////////////////////////

inherit(SelectCommand, SelectReverseTreeCommand);

SelectReverseTreeCommand.prototype.desc = "select reverse tree";

function SelectReverseTreeCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectReverseTreeCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

SelectReverseTreeCommand.prototype.execute = function () 
{

    var nodeselection = this.graph.nodeselection;
    if (nodeselection.size() <= 0 || nodeselection.size() > 1) {
	throw new GeneralError('You must select ONE node');
    } else {

	SelectReverseTreeCommand.superclass.execute.call(this);

	var v = nodeselection.toArray()[0].gnode;
	var visited = new GESet();
	var arcs = new Array();

	arcs = arcs.concat(v.incoming);
	visited.add(v);

	while (arcs.length != 0) {
	    v = arcs.pop().src;
	    if (!visited.contains(v)) {
		arcs = arcs.concat(v.incoming);
		if(v.collapse == false) visited.add(v);
	    }
	}

	this.graph.nodeselectionhandler.addNodes(
		this.graph.view.getViewNodes(visited.toArray()));
    }
};

////////////////////////////////////
//	SelectForwardTreeCommand
////////////////////////////////////

inherit(SelectCommand, SelectForwardTreeCommand);

SelectForwardTreeCommand.prototype.desc = "select forward tree";

function SelectForwardTreeCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

SelectForwardTreeCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

SelectForwardTreeCommand.prototype.execute = function () 
{
    var nodeselection = this.graph.nodeselection;
    if (nodeselection.size() <= 0 || nodeselection.size() > 1) {
	throw new GeneralError('You must select ONE node');
    } else {

	SelectForwardTreeCommand.superclass.execute.call(this);

	var v = nodeselection.toArray()[0].gnode;
	var visited = new GESet();
	var arcs = new Array();

	arcs = arcs.concat(v.outgoing);
	visited.add(v);

	while (arcs.length != 0) {
	    v = arcs.pop().dst;
	    if (!visited.contains(v)) {
		arcs = arcs.concat(v.outgoing);
		if(v.collapse == false) visited.add(v);
	    }
	}

	this.graph.nodeselectionhandler.addNodes(
		this.graph.view.getViewNodes(visited.toArray()));
    }
};

////////////////////////////////////
//	ToggleArcFilterCommand
////////////////////////////////////

inherit(Command, ToggleArcFilterCommand);

ToggleArcFilterCommand.prototype.desc = "toggle filter for arc type";

function ToggleArcFilterCommand (graph, type) {
    if (arguments.length > 0) {
	this.init(graph, type);
    }
}

ToggleArcFilterCommand.prototype.init = function (graph, type) 
{
    this.graph = graph;
    this.type= type;
};

ToggleArcFilterCommand.prototype.execute = function () 
{
    var state = !arcfilter[this.type];

    var model = graph.getModel();
    var Arc = graph.view.arcview;
    var garcs = model.getArcsByAttribute('type',this.type).toArray(); 
    var varcs = this.graph.view.getViewArcs(garcs);

    for (var i in varcs) {
	Arc.setVisible(varcs[i], !state);
    }

    // TODO remove this.. 
    arcfilter[this.type] = state;
};

////////////////////////////////////
//	ToggleNodeFilterCommand
////////////////////////////////////

inherit(Command, ToggleNodeFilterCommand);

ToggleNodeFilterCommand.prototype.desc = "toggle filter for node type";

function ToggleNodeFilterCommand (graph, type) {
    if (arguments.length > 0) {
	this.init(graph, type);
    }
}

ToggleNodeFilterCommand.prototype.init = function (graph, type) 
{
    this.graph = graph;
    this.type = type;
};

ToggleNodeFilterCommand.prototype.execute = function () 
{
    var state = !nodefilter[this.type];

    var model = graph.getModel();
    var Node = graph.view.nodeview;
    var gnodes = model.getNodesByAttribute('type',this.type).toArray(); 
    var vnodes = this.graph.view.getViewNodes(gnodes);

    for (var i in vnodes) {
	if(gnodes[i].collapse==false) Node.setVisible(vnodes[i], !state);
    };

    // TODO remove this.. 
    nodefilter[this.type] = state;
};


////////////////////////////////////
//	SelectionSetSVGAttributeCommand
////////////////////////////////////

inherit(Command, SelectionSetSVGAttributeCommand);

SelectionSetSVGAttributeCommand.prototype.desc = "set SVG attribute for selection";

function SelectionSetSVGAttributeCommand (graph, ns, attr, value) {
    if (arguments.length > 0) {
	this.init(graph, ns, attr, value);
    }
}

SelectionSetSVGAttributeCommand.prototype.init = function (graph, ns, attr, value) 
{
    this.graph = graph;
    this.ns = ns;
    this.attr = attr;
    this.value = value;
};

SelectionSetSVGAttributeCommand.prototype.execute = function () 
{
    var nodeselection = graph.nodeselection;
    if (nodeselection.size() <= 0) {
	alert('You must select at least one node');
    } else {
	//this.undodata = new Array();
	var iter = nodeselection.iterator();
	iter.first();
	do { 
	    var svgnode = iter.currentItem().svg;
	    //this.undodata.push({svgnode:svgnode, attrvalue: svgnode.getAttributeNS(this.ns, this.attr)});
	    svgnode.setAttributeNS(this.ns, this.attr, this.value);
	} while (iter.next());
	//this.addToHistory();
    }
};

SelectionSetSVGAttributeCommand.prototype.undo = function ()
{
    for (var i in this.undodata) {
	var state = this.undodata[i];
	state.svgnode.setAttributeNS(this.ns, this.attr, state.attrvalue);
    }
}

////////////////////////////////////
//	SelectionSetSVGStyleCommand
////////////////////////////////////

inherit(Command, SelectionSetSVGStyleCommand);

SelectionSetSVGStyleCommand.prototype.desc = "set style attribute for selection";

function SelectionSetSVGStyleCommand (graph, attr, value) {
    if (arguments.length > 0) {
	this.init(graph, attr, value);
    }
}

SelectionSetSVGStyleCommand.prototype.init = function (graph, attr, value) 
{
    this.graph = graph;
    this.attr = attr;
    this.value = value;
};

SelectionSetSVGStyleCommand.prototype.execute = function () 
{
    var nodeselection = graph.nodeselection;
    if (nodeselection.size() <= 0) {
	alert('You must select at least one node');
    } else {
	this.undodata = new Array();
	var iter = nodeselection.iterator();
	iter.first();
	do { 
	    var svgnode = iter.currentItem().svg;
	    this.undodata.push({svgnode:svgnode, attrvalue:
		    svgnode.style.getPropertyValue(this.attr) || 1.0 });
	    svgnode.style.setProperty(this.attr, this.value);
	} while (iter.next());
	this.addToHistory();
    }
};

SelectionSetSVGStyleCommand.prototype.undo = function ()
{
    for (var i in this.undodata) {
	var state = this.undodata[i];
	state.svgnode.style.setProperty(this.attr, state.attrvalue);
    }
}

////////////////////////////////////
//	showNodeCommand
////////////////////////////////////

inherit(Command, showNodeCommand);

showNodeCommand.prototype.desc = "show hidden nodes info for selection";

function showNodeCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

showNodeCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

showNodeCommand.prototype.execute = function () 
{
    var nodeselection = graph.nodeselection;
    var garcs = graph.getModel().arcs;
    var varcs = this.graph.view.getViewArcs(garcs);
    var gnodes = graph.getModel().nodes;
    var vnodes = this.graph.view.getViewNodes(gnodes);
    
    for (var i in vnodes) {
        if(vnodes[i].gnode.collapse==false){
           graph.view.nodeview.setVisible(vnodes[i],true);
        }
    } 
    
    for (var i in garcs) {
       if(varcs[i].garc.collapse==false){
    	 graph.view.arcview.setVisible(varcs[i],true);
       }
    } 
  
};

////////////////////////////////////
//	hideNodeCommand
////////////////////////////////////

inherit(Command, hideNodeCommand);

hideNodeCommand.prototype.desc = "hide node info for selection";

function hideNodeCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

hideNodeCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

hideNodeCommand.prototype.execute = function () 
{
    var nodeselection = graph.nodeselection;
    var garcs = graph.getModel().arcs;
    var varcs = this.graph.view.getViewArcs(garcs);
    
    if (nodeselection.size() <= 0) {
    	alert('You must select at least one node');
    } else {
    	var iter = nodeselection.iterator();
    	iter.first();
    	do {     	    
    	   var v = iter.currentItem();
    	   graph.view.nodeview.setVisible(v, false);
    	    	
	   for (var i in garcs) {
	    	if((garcs[i].src!=null && garcs[i].src.id == v.id) || (garcs[i].dst!=null && garcs[i].dst.id == v.id )) 
	    	   graph.view.arcview.setVisible(varcs[i],false);
           }    
    	} while (iter.next());	
    }
    graph.nodeselectionhandler.clearSelection();
};


////////////////////////////////////
//	showInfoCommand
////////////////////////////////////

inherit(Command, showInfoCommand);

showInfoCommand.prototype.desc = "show info for selected node";

function showInfoCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

showInfoCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

showInfoCommand.prototype.execute = function () 
{
    if (this.graph.nodeselection.size() <= 0) {
         alert('You must select at least one node');
    } else  _populateNodePropWindow();
};

////////////////////////////////////
//	selectIncomingCommand
////////////////////////////////////
inherit(Command, selectIncomingCommand);

selectIncomingCommand.prototype.desc = "select a node and its incoming nodes";

function selectIncomingCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

selectIncomingCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

selectIncomingCommand.prototype.execute = function () 
{
    var nodeselection = this.graph.nodeselection;
    selectIncomingCommand.superclass.execute.call(this);
    var visited = new GESet();
    for(var i in nodeselection.toArray()){	
	var v = nodeselection.toArray()[i].gnode;
	for( var j in v.incoming){
	    if(v.incoming[j].src.collapse == false) visited.add(v.incoming[j].src);
	}
    }
    this.graph.nodeselectionhandler.addNodes(
	this.graph.view.getViewNodes(visited.toArray()));

};
 
////////////////////////////////////
//	selectOutgoingCommand
////////////////////////////////////
inherit(Command, selectOutgoingCommand);

selectOutgoingCommand.prototype.desc = "select a node and its outgoing nodes";

function selectOutgoingCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

selectOutgoingCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

selectOutgoingCommand.prototype.execute = function () 
{
    var nodeselection = this.graph.nodeselection;
    selectOutgoingCommand.superclass.execute.call(this);
    var visited = new GESet();
    for(var i in nodeselection.toArray()){	
	var v = nodeselection.toArray()[i].gnode;
	for( var j in v.outgoing){
	    if(v.outgoing[j].dst.collapse == false) visited.add(v.outgoing[j].dst);
	}
    }
    this.graph.nodeselectionhandler.addNodes(
	this.graph.view.getViewNodes(visited.toArray()));

};
  


////////////////////////////////////
//	collapseCommand
////////////////////////////////////

inherit(Command, collapseCommand);

collapseCommand.prototype.desc = "collapsing for selected nodes";

function collapseCommand(graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

collapseCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

collapseCommand.prototype.execute = function () 
{
    var nodeselection = graph.nodeselection;
    if (nodeselection.size() <= 1) {
       alert('You must select at least two nodes');
    } else {
       var model= graph.getModel();
       var varcs = graph.view.getViewArcs(model.arcs);
       var iter = nodeselection.iterator();
       iter.first();
       var v = iter.currentItem();

       newNode = model.createNode("a"+v.id, "Collapse");  // id = "a" + v.id
       model.getNodeHandler().setAttribute(newNode, "name", "group1");
       model.getNodeHandler().setAttribute(newNode, "sourcefile", "file:/home/kienle/prj/rigi/Rigi/rigiedit/Rigi/db/list-d/src/");
       graph.view.addNode(model.nodes["a"+v.id]);   
       
       // set the node location
       var p = graph.view.nodeview.getLocation(iter.currentItem());	
       graph.view.nodeview.setInitialLocation("a"+v.id, p.x, p.y);
            
       var incomingArcArray = new Array(); // array to remember if an incoming arc has been created
       var outgoingArcArray = new Array(); // array to remember if an outgoing arc has been created
       // remove arcs related to selected nodes, create new arcs to connect 
       do {     	    
           var v = iter.currentItem();
           graph.view.nodeview.setVisible(v, false);
           v.gnode.collapse = true;
           v.gnode.parentNode = newNode;
           newNode.nodeArray.push(v);

           for (var i in v.gnode.incoming){
	      var flag = 0; 
	      var arcG = v.gnode.incoming.pop();
	      if(model.arcs[arcG.id].collapse == false) {
	           model.arcs[arcG.id].collapse = true;  //This arc has being collapsed.
	           graph.view.arcview.setVisible(varcs[arcG.id],false);
	           model.removeArcFromSrc(arcG);  //remove arcG from its src.outgoing
	   
	           var iter2 = nodeselection.iterator();
	           iter2.first();
	           //find out if this arc's src is in nodeselection or not. 
	           do { 
	              var tempNode = iter2.currentItem();
	              // This arc 's src is in nodeSelection.
	              if(model.arcs[arcG.id].src.id == tempNode.id){ 
	                 flag = 1;
	                 break;
	              } 
	           } while (iter2.next() && flag ==0);
	   
	           //This arc 's src is not in nodeSelection. So create a new arc to connect.
	           if(flag == 0 && incomingArcArray[arcG.src.id] == null){ 
	                  str = "a"+arcG.id;
	                  while(model.arcs[str] != null) str = "a"+str;
	                  arc1 = model.createArc(str, "composite", arcG.src, newNode);
	                  
	                  graph.view.addArc(model.arcs[str]); 
	                  incomingArcArray[arcG.src.id] = arcG.src.id;
	                  newNode.arcOutArray.push(model.arcs[arcG.id]); 
	           } else if(flag==0){ 
	                  newNode.arcOutArray.push(model.arcs[arcG.id]); 
	           } else {
	                  newNode.arcInArray.push(model.arcs[arcG.id]);     
	           }
	        }//if
	    }//for
	    
	   for (var i in v.gnode.outgoing){
	      var flag = 0; //
	      var arcG = v.gnode.outgoing.pop();
	      //This arc has not collapsed.
	      if(model.arcs[arcG.id].collapse == false) {
	         model.arcs[arcG.id].collapse = true; //The flag notes that this arc has collapsed.
	         graph.view.arcview.setVisible(varcs[arcG.id],false);
	         model.removeArcFromDst(arcG); //remove arcG from its dst.incoming
	         
	      	 var iter2 = nodeselection.iterator();
	      	 iter2.first();
	      	 do { 
	      	      var tempNode = iter2.currentItem();
	      	       // This arc 's dst is in nodeSelection.
	      	      if(model.arcs[arcG.id].dst.id == tempNode.id){ 
	      	         flag = 1;
	      	         break;
	      	      } 	      
	      	 } while (iter2.next() && flag ==0);
	      	   
	      	 //This arc 's dst is not in nodeSelection. So create a new arc to connect.
	      	 if(flag == 0 && outgoingArcArray[arcG.dst.id] == null){ 
	      	     str = "a"+arcG.id;
	             while(model.arcs[str] != null) str = "a"+str;
		     arc1 = model.createArc(str, "composite",newNode, arcG.dst);

	      	     graph.view.addArc(model.arcs[str]); 
	      	     outgoingArcArray[arcG.dst.id] = arcG.dst.id;   
	      	     newNode.arcOutArray.push(model.arcs[arcG.id]); 
	      	  }else if(flag==0){
	                  newNode.arcOutArray.push(model.arcs[arcG.id]); 
	          }else{
	                  newNode.arcInArray.push(model.arcs[arcG.id]);     
	          }
	      }//if
           }//for
    	} while (iter.next());
        graph.view.redraw();
        
        // The new created node, collapse node, becomes selected.
        selectArray = new Array();
        selectArray.push(newNode);
        graph.nodeselectionhandler.clearSelection();
        graph.nodeselectionhandler.addNodes(graph.view.getViewNodes(selectArray));
    }//else
};



////////////////////////////////////
//	expandCommand
////////////////////////////////////

inherit(Command, expandCommand);

expandCommand.prototype.desc = "expanding for the selected node";

function expandCommand(graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

expandCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

expandCommand.prototype.execute = function () 
{
    var nodeselection = graph.nodeselection;
    if (nodeselection.size() != 1) {
       alert('You must select exactly one node');
    } else {
       var model= graph.getModel();
       var varcs = graph.view.getViewArcs(model.arcs);
       var vnodes = graph.view.getViewNodes(model.nodes);
       
       var v = nodeselection.toArray()[0].gnode;  // the selected collapse node
       if(v.nodeArray.length<1) {
          alert("This node is not a collapsed node!");
          return;
       }

      
       // get the node location
       var p = graph.view.nodeview.getLocation(nodeselection.toArray()[0]);	

       // expand the nodeArray, set the first element's position same as current collapse node 
       var j = 0;  // when j = 0, set the first node position equal to the current collapse node
       for(var i in v.nodeArray){    	    
           var node = v.nodeArray[i];
           // set the nodes' lacation according to the expanding node's lacation
           if (j==0) {
              var p1 = graph.view.nodeview.getLocation(vnodes[node.id]);
              graph.view.nodeview.setInitialLocation(node.id, p.x, p.y);
              j = 1;
           } else {
              var p2 = graph.view.nodeview.getLocation(vnodes[node.id]);	
              graph.view.nodeview.setInitialLocation(node.id, p.x + p2.x - p1.x, p.y + p2.y -p1.y);
           }
           
           graph.view.nodeview.setVisible(node, true);
           node.gnode.collapse = false;
       } 
       //delete incoming arc and remove arc from its src.outgoing
       for(var i in v.incoming){    	    
             var t = v.incoming.pop();
	     model.removeArcFromSrc(t); // remove arc from its src.outgoing
	     graph.view.arcview.setVisible(varcs[t.id], false);
	     delete model.arcs[t.id];
             delete varcs[t.id];
       } 
       
       //delete outgoing arc and remove arc from its src.incoming
       for(var i in v.outgoing){    	    
            var t = v.outgoing.pop();
       	    model.removeArcFromDst(t);
       	    graph.view.arcview.setVisible(varcs[t.id], false);
	    delete model.arcs[t.id];
            delete varcs[t.id];
       } 
      
       for(var i in v.arcInArray){    	    
             var arc = v.arcInArray[i];
             arc.src.outgoing.push(arc);
             arc.dst.incoming.push(arc);
             graph.view.arcview.setVisible(varcs[arc.id], true);
             model.arcs[arc.id].collapse = false;  //This arc is expanded.
       } 
       
       for(var i in v.arcOutArray){    	    
             var arc = v.arcOutArray.pop();
             if(arc == null || arc.src == null ||arc.dst == null) {
             //do nothing;
             }
             else if(model.nodes[arc.src.id]==null || model.nodes[arc.dst.id]==null){
                delete model.arcs[arc.id];
                delete varcs[arc.id];
                //remove this arc because its src or dst has been deleted.
             }else if(arc.src.collapse==false && arc.dst.collapse==false && model.arcs[arc.id].collapse == true){
                 graph.view.arcview.setVisible(varcs[arc.id], true);
                 model.arcs[arc.id].collapse = false;  //This arc is expanded.
                 arc.src.outgoing.push(arc);
                 arc.dst.incoming.push(arc);
             } 
             // arc's src or dst is collapsed into a new node, so we should find out the new node
             else if(arc.src.collapse!=false || arc.dst.collapse!=false ){  
                 srcNode = arc.src;
                 if(srcNode.collapse==true){
                     do{
                         srcNode.parentNode.arcOutArray.push(arc);
                         var arc1 = model.getArcByConnected(srcNode.parentNode, arc.dst);
                         if(arc1 == null) {
                             str = "c"+arc.id;
                             while(model.arcs[str] != null) str = "c"+str;
                             arc1 = model.createArc(str, "composite", srcNode.parentNode, arc.dst);
		             graph.view.addArc(model.arcs[str]); 
	                 
	                     if(srcNode.parentNode.collapse==true) {
			     	srcNode.parentNode.outgoing.pop();
			     	model.arcs[str].collapse = true;  //This arc has being collapsed
			        model.removeArcFromDst(arc1); //remove arc from its dst.incoming  
			     }else break; 
			   }
			   arc =arc1;
			   srcNode = srcNode.parentNode; 
		    }while(srcNode.collapse==true);
	         } else { 
	             dstNode = arc.dst;
	             do{
		         dstNode.parentNode.arcOutArray.push(arc);
		         var arc1 = model.getArcByConnected(arc.src, dstNode.parentNode);
		         if(arc1 == null) {
		            str = "c"+arc.id;
                            while(model.arcs[str] != null) str = "c"+str;
		            arc1 = model.createArc(str, "composite", arc.src, dstNode.parentNode);
		            graph.view.addArc(model.arcs[str]); 
		        
		            if(dstNode.parentNode.collapse==true) {
		               dstNode.parentNode.incoming.pop();
		               model.arcs[str].collapse = true;  //This arc has being collapsed
                               model.removeArcFromSrc(arc1); //remove arcG from its src.outgoing  
	                    }
	                 }
	                 arc =arc1;
	                 dstNode = dstNode.parentNode; 
		     }while(dstNode.collapse==true);
	         }
             }//else
       }//for

       graph.view.nodeview.setVisible(nodeselection.toArray()[0], false);
       delete model.nodes[v.id];
       delete vnodes[v.id];
       graph.view.redraw();
    }//else
};


////////////////////////////////////
//	cloneCommand
////////////////////////////////////

inherit(Command, cloneCommand);

cloneCommand.prototype.desc = "copy for selected nodes";

function cloneCommand(graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

cloneCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

cloneCommand.prototype.execute = function (hID) 
{
    var model= graph.getModel();
    model.nodesHistory[hID] = new Array();
    model.arcsHistory[hID] = new Array();
    model.selectionHistory[hID] = new Array();
    
    for(var i in model.nodes) {
       if(model.nodes[i]!= null) model.copyNode(model.nodes[i].id, hID);   
    }
    for(var i in model.arcs) {
       if(model.arcs[i]!= null) model.copyArc(model.arcs[i].id, hID);   
    }
    
    /* make a copy of current node selection*/
    var iter = graph.nodeselection.iterator();
    iter.first();
    do {     	    
         var v = iter.currentItem();
         if(v!=null) model.selectionHistory[hID].push(v.id);
    } while (iter.next());  
};


////////////////////////////////////
//	historyCommand
////////////////////////////////////

inherit(Command, historyCommand);

historyCommand.prototype.desc = "copy for selected nodes";

function historyCommand(graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

historyCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

historyCommand.prototype.execute = function (hID) 
{
    var model= graph.getModel();
    if(model.nodesHistory[hID]==null) {
      return;
    }
    var varcs = graph.view.getViewArcs(model.arcs);
   
    for(var i in model.nodes) {
         graph.view.nodeview.setVisible(graph.view.nodes[i], false);
    }
    for(var i in model.arcs) {
         graph.view.arcview.setVisible(varcs[i], false);
         delete varcs[i];
    }
    model.nodes = new Array();
    model.arcs = new Array();
    for(var i in model.nodesHistory[hID]) {
       newNode = model.back1(model.nodesHistory[hID][i].id, hID); 
       graph.view.addNode(newNode); 
       graph.view.nodeview.setInitialLocation(model.nodesHistory[hID][i].id, model.nodesHistory[hID][i].x, model.nodesHistory[hID][i].y);
    }
    for(var i in model.arcsHistory[hID]) {
       newArc = model.backArc(model.arcsHistory[hID][i].id, hID); 
       graph.view.addArc(newArc);  
    }
    for(var i in model.nodesHistory[hID]) {
          newNode = model.back2(model.nodesHistory[hID][i].id, hID);     
    }
    for(var i in graph.view.nodes) {
          if(graph.view.nodes[i].gnode.collapse || !graph.view.nodes[i].gnode.isVisible) graph.view.nodeview.setVisible(graph.view.nodes[i], false);
    }
    for(var i in graph.view.arcs) {
              if(!graph.view.arcs[i].garc.isVisible) graph.view.arcview.setVisible(graph.view.arcs[i], false);
    }
   
    /* copy back nodeselection from the sanpshot*/
    var nodeselection = new Array();
    for(var i in model.selectionHistory[hID]){
        nodeselection.push(graph.view.nodes[model.selectionHistory[hID][i]]);
    } 
    graph.nodeselectionhandler.setSelection([]);
    graph.nodeselectionhandler.addNodes(graph.view.getViewNodes(nodeselection));
    
   graph.view.redraw();
};


////////////////////////////////////
//	ZoomCommand
////////////////////////////////////

inherit(Command, ZoomCommand);

ZoomCommand.prototype.desc = "zoom window. 0 zoom out, 1 zoom in";

function ZoomCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

ZoomCommand.prototype.init = function (graph, zoom) 
{
    this.graph = graph;
    this.zoom = zoom;
};

ZoomCommand.prototype.execute = function ( ) 
{
    if ( this.zoom == 0 ) {
	this.graph.view.viewbox.zoomOut();
    } else {
	this.graph.view.viewbox.zoomIn();
    }
};

////////////////////////////////////
//	ZoomTo
////////////////////////////////////

inherit(Command, ZoomToCommand);

ZoomToCommand.prototype.desc = "zoom and center window on location";

function ZoomToCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

ZoomToCommand.prototype.init = function (graph, x, y, zoom) 
{
    this.graph = graph;
    this.x = x;
    this.y = y;
    this.zoom = zoom;
};

ZoomToCommand.prototype.execute = function () 
{
    if ( this.zoom == 0 ) {
	this.graph.view.viewbox.zoomOutTo(this.x, this.y);
    } else {
	this.graph.view.viewbox.zoomInTo(this.x, this.y);
    }
};

////////////////////////////////////
//	PanToCommand
////////////////////////////////////

inherit(Command, PanToCommand);

PanToCommand.prototype.desc = "center window on location";

function PanToCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

PanToCommand.prototype.init = function (graph, x, y) 
{
    this.graph = graph;
    this.x = x;
    this.y = y;
};

PanToCommand.prototype.execute = function () 
{
    this.graph.view.viewbox.panTo(this.x, this.y);
};

////////////////////////////////////
//	PanByCommand
////////////////////////////////////

inherit(Command, PanByCommand);

PanByCommand.prototype.desc = "pan window by offsets";

function PanByCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

PanByCommand.prototype.init = function (graph, x, y) 
{
    this.graph = graph;
    this.x = x;
    this.y = y;
};

PanByCommand.prototype.execute = function () 
{
    this.graph.view.viewbox.panBy (this.x, this.y);
};


////////////////////////////////////
//	PostGraphCommand
////////////////////////////////////

inherit(Command, PostGraphCommand);


function PostGraphCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

PostGraphCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

PostGraphCommand.prototype.execute = function () 
{
    
    var form	    = new Object();
    form["function"]	= "importGraph";
    form["graph"]	= (new ModelGXLEncoder()).toGXL(graph.model);
    form["format"]	= "GXL";
    var text = encodeObject(form);

    var url = "/testing/servlet/rigiPost";
    var callback = this.sent;
    var type = "application/x-www-form-urlencoded";
    var enc = null;
    postURL( url, text, callback, type, enc );

};

PostGraphCommand.prototype.sent = function (data)
{
    var msg = "";
    if (data.success) {
	msg += data.content;  // expect "true" or "false"
    } else {
	msg += "send request has failed";
    }
    alert("RESULT\n" + msg);
};

////////////////////////////////////
//	UndoLastCommand
////////////////////////////////////

inherit(Command, UndoLastCommand);

function UndoLastCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

UndoLastCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

UndoLastCommand.prototype.execute = function () 
{
    history.undo();
};

////////////////////////////////////
//	RedoLastCommand	
////////////////////////////////////

inherit(Command, RedoLastCommand);

function RedoLastCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

RedoLastCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

RedoLastCommand.prototype.execute = function () 
{
    history.redolast(); 
};


////////////////////////////////////
//	snapShotCommand	
////////////////////////////////////
inherit(Command, snapShotCommand);

function snapShotCommand(graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

snapShotCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

snapShotCommand.prototype.execute = function (){      
       if(hID>MAX_History) newHistoryWindow(hID-MAX_History);
       var contents = history_window.contents;
       var str = "histroy " + hID;
       
       /* to save and protect nodeselection*/
       var nodeselection = new Array();
       var iter = graph.nodeselection.iterator();
       iter.first();
       do {     	    
           var v = iter.currentItem();
           nodeselection.push(v);
       } while (iter.next());
       graph.nodeselectionhandler.setSelection([]);
       
       var g = svgdoc.createElement("g");
       g.setAttribute( "id", 'g'+hID);
       
       var rect1 = svgdoc.createElement("rect");
       rect1.setAttribute( "height", "80");
       rect1.setAttribute("width", "80");
       rect1.setAttribute("y", "17");
       rect1.setAttribute( "fill", 'none');
       rect1.setAttribute( "id", 'r'+hID);
       rect1.style.setProperty("stroke", "lightblue");
       rect1.style.setProperty("stroke-width", "2");  
       
       var rect2 = svgdoc.createElement("rect");
       rect2.setAttribute( "height", "16");
       rect2.setAttribute("width", "80");
       rect2.setAttribute( "fill", 'lightgrey');
       rect2.style.setProperty("fill-opacity", '.3');
       rect2.setAttribute( "id", 'rr'+hID);
       rect2.addEventListener('click', deleteHistory, false);
       rect2.addEventListener('mouseover', changeColor1, false);
       rect2.addEventListener('mouseout', changeColor2, false);
       
       var node=svgdoc.createElement('text');
       node.setAttribute('x','40');
       node.setAttribute('y','13');
       node.setAttribute('id','text'+hID);
       node.setAttribute('style','text-anchor:middle;font-size:15;font-family:Arial;fill:black');
       texte=svgdoc.createTextNode('History '+hID);
       node.appendChild(texte); 

       var mini_graph = graph.svgNode.cloneNode(true);
       graph.nodeselectionhandler.addNodes(graph.view.getViewNodes(nodeselection));
       mini_graph.setAttributeNS(null, "shape-rendering", "optimizeSpeed");
       mini_graph.setAttributeNS(null, "color-rendering", "optimizeSpeed");
       mini_graph.setAttributeNS(null, "text-rendering", "optimizeSpeed");
       mini_graph.setAttributeNS(null, "height", 80);
       mini_graph.setAttributeNS(null, "width", 80);
       mini_graph.setAttributeNS(null, "y", 16);
       mini_graph.setAttributeNS(null, "id", str);
       mini_graph.addEventListener('click', backToHistory, false);
       mini_graph.addEventListener('mouseover', redStroke, false);
       mini_graph.addEventListener('mouseout', blueStroke, false);

       g.appendChild(node);
       g.appendChild(mini_graph);
       g.appendChild(rect1);
       g.appendChild(rect2);

       contents.add(g);
       history_window.doDeepPreferedLayout();
       history_window.setVisible(true);
       new cloneCommand().execute(hID);
       hID++;       
};



////////////////////////////////////
//	DemoCommand
////////////////////////////////////

inherit(Command, DemoCommand);

function DemoCommand (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

DemoCommand.prototype.init = function (graph) 
{
    this.graph = graph;
};

DemoCommand.prototype.execute = function () 
{   
    populateDemoWindow();
};


/*
////////////////////////////////////
//	CommandTemplate
////////////////////////////////////

inherit(Command, CommandTemplate);

function CommandTemplate (graph) {
    if (arguments.length > 0) {
	this.init(graph);
    }
}

CommandTemplate.prototype.init = function (graph) 
{
    this.graph = graph;
};

CommandTemplate.prototype.execute = function () 
{
};
*/



