//
// $Id: core.es,v 1.30 2002/08/28 00:36:42 jpipi Exp $
//

var svgns = "http://www.w3.org/2000/svg";
var xlinkns = 'http://www.w3.org/2000/xlink/namespace/';
var svgdoc;
var graph;
var scriptengine;
var node_window;
var history = new CommandHistory();
var viewport;
var txtEntry;
var timerID;
var hID = 1;
var currentHID = 1;
var MAX_History = 7;  // maximum number of sanpshots in a page.

var history_window;
var nodefilter = new Object();
var arcfilter = new Object();

function DoOnLoad(evt) 
{
    var svgnode = evt.getTarget();
    svgdoc  = svgnode.getOwnerDocument();
    viewport = new Viewport();
    buildGraph();
    createMenus();
    createNodePropWindow();
    createHistoryWindow();
    createHistoryControlWindow();
    createDemoWindow();
    scriptengine = new RCLScriptEngine(graph);
    createScriptEntry();
}

function buildGraph() 
{
    if (CREATION == "dynamic") {
	graph = new rGraph(null);
	graph = createGraph(graph);
    } else {
	var graph_svg_node = svgdoc.getElementById("graph_pane");
	var model = new SVGImportGraphModel(graph_svg_node);
	var view = new SVGImportGraphView(model, graph_svg_node);
	graph = new rGraph(model, view);
    }
}
/************************************************************************
 *                                                                       *
 *                      RCL Command Listing Window	        	*
 *                                                                       *
 *************************************************************************/

function createRCLCommandWindow() 
{

    var root = svgdoc.documentElement;
    desc_window = new Window("RCL Command Listing");
    root.appendChild(desc_window.container);
    var contents = desc_window.contents = new Container(null);
    desc_window.add(new Label("click a command name to copy it to the command box"));
    desc_window.add(desc_window.contents); 

    desc_window.background.style.setProperty('fill-opacity', 0.9);
    contents.background.style.setProperty('fill', 'none');
    contents.layoutManager = new CellLayout(null, 3, null, 10);

    var lbl_command_name = new Label("command name");
    var lbl_args = new Label("arguments");
    var lbl_desc = new Label("description");

    var font_colour = "black" 
    lbl_command_name.text.style.setProperty("stroke", font_colour);
    lbl_args.text.style.setProperty("stroke", font_colour);
    lbl_desc.text.style.setProperty("stroke", font_colour);

    contents.add(lbl_command_name);
    contents.add(lbl_args);
    contents.add(lbl_desc);

    for (var name in scriptengine.bindings) {
	var arg_sig = scriptengine.printFunctionSignature(name);
	var desc = scriptengine.printFunctionDesc(name);
	var commandname = new Button(name);
	commandname.value = name;
	commandname.addEventListener("selection", copyCommandName, false);
	contents.add(commandname);
	contents.add(new Label(arg_sig));
	contents.add(new Label(desc));
    }

    desc_window.doDeepPreferedLayout();
    desc_window.setVisible(false);

    return desc_window;
};

function copyCommandName (evt) 
{
    txtEntry.setText(evt.getTarget().value);
}
function showRCLCommandWindow (evt)
{
    var desc_window = evt.getTarget().window;
    if (desc_window == null) {
	desc_window = evt.getTarget().window = createRCLCommandWindow();
    }
    desc_window.setVisible(true);
};

/************************************************************************
 *                                                                       *
 *                         Node Properties Window			*
 *                                                                       *
 *************************************************************************/

function createNodePropWindow() 
{
    var root = svgdoc.documentElement;
    node_window = new Window("node status");
    root.appendChild(node_window.container);
    node_window.contents = new Container(null);
    node_window.add(node_window.contents); 
    node_window.doDeepPreferedLayout();
    node_window.setVisible(false);

    node_window.background.style.setProperty('fill-opacity', 0.9);
    node_window.contents.background.style.setProperty('fill', 'none');

    graph.node_group.
	addEventListener("click", populateNodePropWindow, false);
};

function populateNodePropWindow (evt)
{

    if (evt.detail != 2) return; 
    if (graph.nodeselection.size() != 1) return;
    _populateNodePropWindow();
}

function _populateNodePropWindow () {
    iter = graph.nodeselection.iterator();
    iter.first();
    var node = iter.currentItem();

    node_window.graph_element = node;
    var contents = node_window.contents;
    contents.removeAll();
    
    /* display its attributes */
    /* id */
    lbl = new Label("id:");
    contents.add(lbl);
    lbl = new Label(node.id);
    contents.add(lbl);

    /* type name */
    lbl = new Label("type name:");
    contents.add(lbl);
    lbl = new Label(node.gnode.type);
    contents.add(lbl);
    
     /* incomings */
    lbl = new Label("incoming:");
    contents.add(lbl);
    lbl = new Label(node.gnode.incoming.length);
    contents.add(lbl);
    
    /* outgoings */
    lbl = new Label("outgoing:");
    contents.add(lbl);
    lbl = new Label(node.gnode.outgoing.length);
    contents.add(lbl);

    var row = 3;
    //TODO find a nicer way to list node attributes
    for (var attr in node.gnode.attribute) {
	row++
	node_window.title.setText("node properties");
	contents.add(new Label(attr));
	value = node.gnode.attribute[attr] || "";
	lbl = new Label("for starters");
	if (value.match(/^file:/) || value.match(/^http:\/\//)) {
	    var a = svgdoc.createElementNS(svgns, "a");
	    a.setAttributeNS(xlinkns, "xlink:href", value);
	    a.setAttributeNS(null, "xlink:show", "new");
	    a.setAttributeNS(null, "target", "_new");
	    lbl.text.parentNode.appendChild(a);
	    a.appendChild(lbl.text);
	    lbl.text.style.setProperty("fill", "blue");
	    lbl.text.style.setProperty("pointer-events", "all");
	    lbl.text.style.setProperty("text-decoration", "underline");
        }
        lbl.setText(node.gnode.attribute[attr]);
        contents.add(lbl);
    }

    contents.layoutManager = new CellLayout(null, 2, null, 10);
    node_window.doDeepPreferedLayout();
    node_window.setVisible(true);
};

function addProperty (evt) 
{
    node = node_window.graph_element;
    attr = node_window.contents.field1.getText();
    value = node_window.contents.field2.getText();
    node.attribute[attr] = value;
    _populateNodePropWindow ();
};

/************************************************************************
 *                                                                       *
 *                             Menu					*
 *                                                                       *
 *************************************************************************/

function createMenus () 
{
    var root = svgdoc.getElementById('menu_pane');
    var submenu_root = root; 
    var menupane = new Menu (null);
    menupane.insets = new Insets(0,0,10,0);
    menupane.background.style.setProperty("visibility", "hidden");
    root.appendChild(menupane.container);

    createFilterMenu(menupane, submenu_root);
    createSelectionMenu(menupane, submenu_root);
    createInfoMenu(menupane, submenu_root);
    createCustomizeMenu(menupane, submenu_root);
    
    //createNavigatorMenu(menupane, submenu_root);
    createLayoutMenu(menupane, submenu_root);
    createExchangeMenu(menupane, submenu_root);
    createCollapseMenu(menupane, submenu_root);
    createUndoMenu(menupane, submenu_root);
    
    

    menupane.doDeepPreferedLayout();
}

function heading (caption) {
    var heading = new Label(caption);
    heading.background.style.setProperty("fill", "#0062AC");
    heading.background.style.setProperty("visibility", "visible");
    heading.text.style.setProperty("fill", "white");
    return heading;
}

function createFilterMenu (menupane, submenu_root) 
{
    var domain = graph.getModel().domain;
    var nodetypes = domain.nodetypes;
    var arctypes = domain.arctypes;

    /* title */
    menupane.add(heading("filter by type"));

    /* arc filters */

    //var arcfiltermenu = new Container(null);
    menupane.add(new Label("arcs"));
    for (var type in arctypes) {
	var cb = new Checkbox(type);
	cb.check.style.setProperty("stroke", arctypes[type].getAttr("rgb"));
	cb.addEventListener(
		'selection', new ToggleArcFilterCommand(graph, type), false);
	menupane.add(cb);
    }

    /* node filters */
    //var nodefiltermenu = new Container(null);
    menupane.add(new Label("nodes"));
    for (var type in nodetypes) {
	var cb = new Checkbox(type);
	cb.check.style.setProperty("stroke", nodetypes[type].getAttr("rgb"));
	cb.addEventListener(
		'selection', new ToggleNodeFilterCommand(graph, type), false);
	menupane.add(cb);
    }

    /* filter menu */
    /*
       var filtermenu = new Menu("filter by type");
       filtermenu.add(arcfiltermenu);
       filtermenu.add(nodefiltermenu);
     */

    //return filtermenu;
}

function createSelectionMenu (menupane, submenu_root) 
{

    /* node types submenu */
    var nodetypemenu = new Menu ("node types");
    submenu_root.appendChild(nodetypemenu.container);

    nodetypemenu.background.style.setProperty('stroke', 'blue');
    nodetypemenu.background.style.setProperty('stroke-width', '2');

    /* fetch node types */
    var nodetypes = graph.getModel().domain.nodetypes; 

    for (var type in nodetypes) {
	var typeitem = new CompositeButton(null);
	typeitem.layoutManager = new CellLayout(null,2);

	var use = svgdoc.createElementNS(null, 'use');
	use.setAttributeNS(null, "style", "stroke:black;stroke-width:1;");
	use.setAttributeNS(null, "r",
		graph.view.viewattrs.getAttr("NODE_RADIUS"));
	use.setAttributeNS(xlinkns, "xlink:href", "#nodetype_" + type);
	var colour = graph.model.domain.getNodeType(type).getAttr("rgb");
	use.style.setProperty("fill", colour);
	typeitem.add(use);
	var label = new Label(type);
	typeitem.add(label);

	// set value
	typeitem.addEventListener(
		"selection", 
		new SelectNodesByAttributeCommand(graph, "type", type), false);
	nodetypemenu.add(typeitem);
    }

    /* select menu */
    //var selectmenu = new Menu("node selection");

    var btnFwt = new Button("select forward tree")
	var btnRvt = new Button("select reverse tree");
    var btnBytype = new Button("select by type");
    var btnAll = new Button ("select all");
    var btnComplement = new Button ("select complement");
    var btnClear = new Button ("clear selection");
    var btnDeadcode = new Button ("select dead code");

    menupane.add(heading("node selection")); // title
    menupane.add(btnFwt);
    menupane.add(btnRvt);
    menupane.add(btnBytype);
    menupane.attachSubmenu(nodetypemenu);   // sub menu 
    menupane.add(btnAll);
    menupane.add(btnComplement);
    menupane.add(btnClear);
    menupane.add(btnDeadcode);

    btnFwt.addEventListener('click', new SelectForwardTreeCommand(graph), false);
    btnRvt.addEventListener('click', new SelectReverseTreeCommand(graph), false);
    btnAll.addEventListener('click', new SelectAllCommand(graph), false);
    btnComplement.addEventListener('click', new SelectComplementCommand(graph), false);
    btnClear.addEventListener('click', new SelectionClearCommand(graph), false);
    btnDeadcode.addEventListener('click', new SelectDeadCodeCommand(graph), false);

    //return selectmenu;
}

function createCustomizeMenu (menupane, submenu_root) 
{
    /* opacity values submenu */
    var op_vals = new Menu("opacity");

    op_vals.background.style.setProperty('stroke', 'blue');
    op_vals.background.style.setProperty('stroke-width', '2');
    op_vals.layoutManager = new GridLayout(null, null, 10, null);

    var op_group = svgdoc.createElementNS(svgns, "g"); 

    var op_base = svgdoc.createElementNS(svgns, "rect");
    op_base.setAttributeNS(null, "x", "-15");
    op_base.setAttributeNS(null, "y", "7.5");
    op_base.setAttributeNS(null, "height", "5");
    op_base.setAttributeNS(null, "width", "60");
    op_base.style.setProperty("fill", "black");

    var op_rect = svgdoc.createElementNS(svgns, "rect");
    op_rect.setAttributeNS(null, "x", "0");
    op_rect.setAttributeNS(null, "y", "0");
    op_rect.setAttributeNS(null, "height", "20");
    op_rect.setAttributeNS(null, "width", "30");
    op_rect.style.setProperty("fill", "blue");
    op_rect.style.setProperty("stroke", "black");

    op_group.appendChild(op_base);
    op_group.appendChild(op_rect);

    function _makeOpacityButton (opacity) {
	var op_temp_group = op_group.cloneNode(true);
	op_temp_group.lastChild.style.setProperty("fill-opacity", opacity);
	var btn = new CompositeButton(null);
	btn.layoutManager = new CellLayout (1);
	btn.add(op_temp_group);
	var label = new Label(opacity * 100 + "%");
	label.text.style.setProperty("font-size", "12");
	btn.add(label);
	btn.addEventListener('selection', new
		SelectionSetSVGStyleCommand(graph, "opacity", opacity), false);
	return btn; 
    }

    var btn_op00 = _makeOpacityButton(0.00);
    var btn_op25 = _makeOpacityButton(0.25);
    var btn_op50 = _makeOpacityButton(0.50);
    var btn_op75 = _makeOpacityButton(0.75);
    var btn_op100= _makeOpacityButton(1.00);

    op_vals.add(btn_op00);
    op_vals.add(btn_op25);
    op_vals.add(btn_op50);
    op_vals.add(btn_op75);
    op_vals.add(btn_op100);

    /* colour values submenu */
    var colour_vals = new Menu("colour");
    colour_vals.background.style.setProperty('stroke', 'blue');
    colour_vals.background.style.setProperty('stroke-width', '2');
    colour_vals.layoutManager = new GridLayout(4);

    var rect = svgdoc.createElementNS(svgns, "rect");
    rect.setAttributeNS(null, "x", "0");
    rect.setAttributeNS(null, "y", "0");
    rect.setAttributeNS(null, "height", "20");
    rect.setAttributeNS(null, "width", "20");
    rect.style.setProperty("stroke", "black");
    rect.style.setProperty("stroke-width", "1");

    var colourRectArray = new Object();
    var colourButtonArray = new Object();

    function _makeColourButton(colour) {
	rect = colourRectArray[colour] = rect.cloneNode(false);
	btn = colourButtonArray[colour] = new CompositeButton(null);
	rect.style.setProperty("fill", colour);
	btn.add(rect);
	btn.addEventListener('selection', new
		SelectionSetSVGStyleCommand(graph, "fill", colour), false);
	return btn;
    }

    colour_vals.add(_makeColourButton("red"));
    colour_vals.add(_makeColourButton("yellow"));
    colour_vals.add(_makeColourButton("orange"));
    colour_vals.add(_makeColourButton("gold"));
    colour_vals.add(_makeColourButton("violet"));
    colour_vals.add(_makeColourButton("blue"));
    colour_vals.add(_makeColourButton("lightblue"));
    colour_vals.add(_makeColourButton("lightgreen"));
    colour_vals.add(_makeColourButton("brown"));
    colour_vals.add(_makeColourButton("white"));
    colour_vals.add(_makeColourButton("gray"));
    colour_vals.add(_makeColourButton("black"));

    /* shapes submenu */
    var shapes = new Menu("shapes");
    shapes.background.style.setProperty('stroke', 'blue');
    shapes.background.style.setProperty('stroke-width', '2');

    // use all symbols defined in this file
    var symbols = svgdoc.getElementsByTagName('symbol');
    for (var i = 0; i < symbols.length; i++) {
	var symbol = symbols.item(i);
	var use = svgdoc.createElementNS(null, 'use');
	var radius
	    if (!symbol.getAttributeNS(null, "r")) {
		radius = 10;
	    } else {
		radius = parseFloat(symbol.getAttributeNS(null, "r"));
	    }
	use.setAttributeNS(null, "r", radius);
	use.setAttributeNS(null, "style", "stroke:black;stroke-width:1;");

	var xlink = "#" + symbol.id;
	use.setAttributeNS(xlinkns, "xlink:href", xlink);

	var btn = new CompositeButton(null);
	btn.addEventListener("selection", new
		SelectionSetSVGAttributeCommand(graph, xlinkns, "xlink:href",
		    xlink), false);
	btn.add(use);
	shapes.add(btn);
    }

    //var customizemenu = new Menu("customize");
    menupane.add(heading("customize"));

    submenu_root.appendChild(op_vals.container);
    submenu_root.appendChild(colour_vals.container);
    submenu_root.appendChild(shapes.container);

    var btnOp	    = new Button("change opacity");
    var btnColour   = new Button("change colour"); 
    var btnShape    = new Button("change shape"); 

    menupane.add(btnOp);
    menupane.attachSubmenu(op_vals);
    menupane.add(btnColour);
    menupane.attachSubmenu(colour_vals);
    menupane.add(btnShape);
    menupane.attachSubmenu(shapes);

    //return customizemenu;
}

function createInfoMenu (menupane, submenu_root)
{
    menupane.add(heading("Node Infomation"));
    var btnShow = new Button("show nodes");
    var btnHide = new Button("Hide nodes");
    btnShow.addEventListener('click', new showNodeCommand(graph), false);
    btnHide.addEventListener('click', new hideNodeCommand(graph), false);
    menupane.add(btnShow);
    menupane.add(btnHide);
    var btnShowInfo = new Button("show Infomation");
    btnShowInfo.addEventListener('click', new showInfoCommand(graph), false);
    menupane.add(btnShowInfo);

};

function createCollapseMenu(menupane, submenu_root){
   menupane.add(heading("Collapse and Expand"));
   var btnSelect = new Button("select incoming nodes");
   var btnSelectOutgoing = new Button("select outgoing nodes");
   var btnCollapse = new Button("collapsing");
   var btnExpand = new Button("expanding");
   btnSelect.addEventListener('click', new selectIncomingCommand(graph), false);
   btnSelectOutgoing.addEventListener('click', new selectOutgoingCommand(graph), false);  
   btnCollapse.addEventListener('click', new collapseCommand(graph), false);
   btnExpand.addEventListener('click', new expandCommand(graph), false);
   menupane.add(btnSelect);
   menupane.add(btnSelectOutgoing);
   menupane.add(btnCollapse);
   menupane.add(btnExpand);
};

function createLayoutMenu (menupane, submenu_root) 
{
    menupane.add(heading("layout"));

    var grid = new Button("grid layout");
    var spring = new Button("spring layout");

    grid.addEventListener("selection", new SelectionGridLayoutCommand(graph), false);
    spring.addEventListener("selection", new SpringLayoutCommand(graph), false);

    menupane.add(grid);
    menupane.add(spring);
}

function createExchangeMenu (menupane, submenu_root)
{
    menupane.add(heading("exchange"));
    var btnSend = new Button("send graph");
    var btnGet = new Button("get graph");
    btnSend.addEventListener('click', new PostGraphCommand(graph), false);
    menupane.add(btnSend);
    menupane.add(btnGet);
};

function createUndoMenu (menupane, submenu_root)
{
    menupane.add(heading("undo / redo"));
    var btnUndo = new Button("undo");
    var btnRedo = new Button("redo");
    btnUndo.addEventListener("click", new UndoLastCommand(), false);
    btnRedo.addEventListener("click", new RedoLastCommand(), false);
    menupane.add(btnUndo);
    menupane.add(btnRedo);
}


/** navigator experiment **/
function createNavigatorMenu (menupane, submenu_root) 
{
    menupane.add(heading("navigator"));
    var mini_graph = graph.svgNode.cloneNode(true);
    mini_graph.setAttributeNS(null, "shape-rendering", "optimizeSpeed");
    mini_graph.setAttributeNS(null, "color-rendering", "optimizeSpeed");
    mini_graph.setAttributeNS(null, "text-rendering", "optimizeSpeed");
    mini_graph.setAttributeNS(null, "x", 0);
    mini_graph.setAttributeNS(null, "y", 0);
    mini_graph.setAttributeNS(null, "height", 200);
    mini_graph.setAttributeNS(null, "width", 200);
    menupane.add(mini_graph);

    var btnUpdate = new Button("update");
    btnUpdate.addEventListener("selection", updateNavigator, false);
    btnUpdate.navigator = mini_graph;
    menupane.add(btnUpdate);
}


function updateNavigator (evt)
{
    var node = evt.getTarget().navigator;

    for (var ch = node.firstChild; ch;) {
	var next = ch.nextSibling;
	node.removeChild(ch);
	ch = next;
    }
    var nodeselection = new Array();
    var iter = graph.nodeselection.iterator();
    iter.first();
    do {     	    
       var v = iter.currentItem();
       nodeselection.push(v);
    } while (iter.next());
    graph.nodeselectionhandler.setSelection([]);
    
    node.appendChild(graph.arc_group.cloneNode(true));
    node.appendChild(graph.node_group.cloneNode(true)); // add on july 11
    graph.nodeselectionhandler.addNodes(graph.view.getViewNodes(nodeselection));
};
/** end navigator experiment **/

function updateGraph(evt)
{

};


function createScriptEntry () 
{
    var rcl = new Container(null);
    txtEntry = new Textbox(null);
    txtEntry.addEventListener("action", executeCommand, false);

    var lbl = new Label("rcl command:");
    var btn = new Button("show commands");
    btn.addEventListener("selection", showRCLCommandWindow, false);

    rcl.add(lbl); 
    rcl.add(txtEntry);
    rcl.add(btn);
    rcl.layoutManager = new CellLayout(null, 3);
    rcl.layoutManager.FIT_TO_HEIGHT = false;
    rcl.layoutManager.VALIGN = CellLayout.VALIGN_CENTER;
    svgdoc.documentElement.appendChild(rcl.container);

    /*layout*/
    lbl.doPreferedLayout();
    txtEntry.doPreferedLayout();
    txtEntry.setBounds(null, null, null, 300);
    btn.doDeepPreferedLayout();
    rcl.doPreferedLayout();
    rcl.setBounds(254, 725);

};


/** execute a command **/
function executeCommand (evt) 
{
    var command = evt.getTarget().getText();
    try {
	var cmd = scriptengine.parse(command);
	cmd.execute();
    } catch (e) {
	alert(e.message);
    }
}

/************************************************************************
 *                                                                       *
 *                         Utility Objects                               *
 *                                                                       *
 *************************************************************************/

////////////////////////////////////
//	    ViewBox	
////////////////////////////////////

/**
 * Computes the transformation produced by the viewBox and preserveAspectRatio
 * attributes for an SVGElement
 *
 * Lots of code taken from Kevin Lindsey
 */ 

function ViewBox(svgNode) {
    if ( arguments.length > 0 ) {
	this.init(svgNode);
    }
}


/**
 *
 *
 */
ViewBox.prototype.init = function(svgNode) {
    var viewBox = svgNode.getAttributeNS(null, "viewBox");
    var preserveAspectRatio = svgNode.getAttributeNS(null, "preserveAspectRatio");

    this.svgNode = svgNode;
    if ( viewBox != null ) {
	var params = viewBox.split(/\s*,\s*|\s+/);

	this.x      = parseFloat( params[0] );
	this.y      = parseFloat( params[1] );
	this.width  = parseFloat( params[2] );
	this.height = parseFloat( params[3] );
    } else {
	// NOTE: Need to put an SVGResize event handler on the svgNode to keep
	// these values in sync with the window size or should add additional
	// logic (probably a flag) to getTM() so it will know to use the window
	// dimensions instead of this object's width and height properties
	this.x      = 0;
	this.y      = 0;
	this.width  = innerWidth;
	this.height = innerWidth;
    }

    if ( preserveAspectRatio != null ) {
	this._setPAR(preserveAspectRatio);
    } else {
	this.align = "xMidYMid";
	this.meetOrSlice = "meet";
    }
};


/*****
 *
 *   getTM
 *
 *****/
ViewBox.prototype.getTM = function() {
    var svgRoot = this.svgNode; 
    var matrix  = svgdoc.documentElement.createSVGMatrix();
    var windowWidth  = svgRoot.getAttributeNS(null, "width");
    var windowHeight = svgRoot.getAttributeNS(null, "height");

    // take into account % dimentions
    var result;
    if ((result = windowWidth.match(/(\d+[\.]?\d*)\%/))) {
	windowWidth = parseFloat(result[1])/100 * innerWidth;
    } else {
	windowWidth  = ( windowWidth != "" ) ? parseFloat(windowWidth)  : innerWidth;
    }

    if ((result = windowHeight.match(/(\d+[\.]?\d*)\%/))) {
	windowHeight = parseFloat(result[1])/100 * innerHeight;
    } else {
	windowHeight = ( windowHeight != "" ) ? parseFloat(windowHeight) : innerHeight;
    }

    var x_ratio = this.width / windowWidth;
    var y_ratio = this.height / windowHeight;

    matrix = matrix.translate(this.x, this.y);
    if ( this.alignX == "none" ) {
	matrix = matrix.scaleNonUniform( x_ratio, y_ratio );
    } else {
	if ( x_ratio < y_ratio && this.meetOrSlice == "meet" || 
		x_ratio > y_ratio && this.meetOrSlice == "slice"  ) {
	    var x_trans = 0;
	    var x_diff  = windowWidth*y_ratio - this.width;

	    if ( this.alignX == "Mid" )
		x_trans = -x_diff/2;
	    else if ( this.alignX == "Max" )
		x_trans = -x_diff;

	    matrix = matrix.translate(x_trans, 0);
	    matrix = matrix.scale( y_ratio );
	} else if ( x_ratio > y_ratio && this.meetOrSlice == "meet" 
		|| x_ratio < y_ratio && this.meetOrSlice == "slice" ) {
	    var y_trans = 0;
	    var y_diff  = windowHeight*x_ratio - this.height;

	    if ( this.alignY == "Mid" )
		y_trans = -y_diff/2;
	    else if ( this.alignY == "Max" )
		y_trans = -y_diff;

	    matrix = matrix.translate(0, y_trans);
	    matrix = matrix.scale( x_ratio );
	} else {
	    // x_ratio == y_ratio so, there is no need to translate
	    // We can scale by either value
	    matrix = matrix.scale( x_ratio );
	}
    }
    return matrix;
};

/*****
 *
 *   _setPAR
 *
 *****/
ViewBox.prototype._setPAR = function(PAR) {
    // NOTE: This function needs to use default values when encountering
    // unrecognized values
    this.PAR = PAR;
    if ( PAR ) {
	var params = PAR.split(/\s+/);
	var align  = params[0];

	if ( align == "none" ) {
	    this.alignX = "none";
	    this.alignY = "none";
	} else {
	    this.alignX = align.substring(1,4);
	    this.alignY = align.substring(5,9);
	}

	if ( params.length == 2 ) {
	    this.meetOrSlice = params[1];
	} else {
	    this.meetOrSlice = "meet";
	}
    } else {
	this.align  = "xMidYMid";
	this.alignX = "Mid";
	this.alignY = "Mid";
	this.meetOrSlice = "meet";
    }
};

// END CLASS: ViewBox 

//////////////////////////////////////////////
//	    ZoomAndPanViewBox 
//////////////////////////////////////////////

inherit (ViewBox, ZoomAndPanViewBox);

/**
 * ZoomAndPanViewBox extends the ViewBox object to allow zooming and panning
 * the viewBox attribute, and handle notifying any listeners of changes made 
 */

function ZoomAndPanViewBox ( svgnode ) 
{
    if (arguments.length > 0)  {
	this.init( svgnode );
    }
};

/**
 *
 */
ZoomAndPanViewBox.prototype.init = function ( svgnode )
{
    ZoomAndPanViewBox.superclass.init.call( this, svgnode );

    this.eventmanager = new EventListenerManager(this);
    this.eventmanager.registerType("viewBoxChanged", null);

    this.zoomfactor = 2;
};

ZoomAndPanViewBox.prototype.addEventListener = function (type, listener, capture) 
{
    this.eventmanager.addEventListener(type, listener, capture);
};
ZoomAndPanViewBox.prototype.removeEventListener = function (type, listener, capture) 
{
    this.eventmanager.removeEventListener(type, listener, capture);
};

ZoomAndPanViewBox.prototype.setViewbox = function ( x, y, h, w, PAR )
{
    this.x = x || this.x;
    this.y = y || this.y;
    this.height = h || this.height;
    this.width = w || this.width;
    this.PAR = PAR || this.PAR; 

    // TODO could do some nifty animation
    var viewBox = this.x + " " + this.y + " " + this.width + " " + this.height;
    this.svgNode.setAttributeNS(null, "viewBox", viewBox);
    this.svgNode.setAttributeNS(null, "preserveAspectRatio", this.PAR);

    // notify listeners
    var event = new GenericEvent("viewBoxChanged", this);
    this.eventmanager.dispatchEvent(event);
};


/**
 * zoom the viewbox while maintain the same center
 */
ZoomAndPanViewBox.prototype.zoom = function ( zoomfactor )
{
    var new_w	= this.width / zoomfactor;
    var new_h	= this.height / zoomfactor;
    var new_x	= (this.x + this.width / 2) - new_w / 2; 
    var new_y	= (this.y + this.height / 2) - new_h /2; 

    this.setViewbox(new_x, new_y, new_h, new_w);
};

ZoomAndPanViewBox.prototype.zoomIn = function () { this.zoom(this.zoomfactor) };
ZoomAndPanViewBox.prototype.zoomOut = function () { this.zoom(1/this.zoomfactor) };

/**
 * zoom the viewbox so that the new view is centered as specified
 */
ZoomAndPanViewBox.prototype.zoomTo = function ( center_x, center_y, zoomfactor )
{
    var new_w	= this.width / zoomfactor;
    var new_h	= this.height / zoomfactor;
    var new_x	= center_x - new_w / 2; 
    var new_y	= center_y - new_h / 2; 

    this.setViewbox (new_x, new_y, new_h, new_w);
};

ZoomAndPanViewBox.prototype.zoomInTo = function (x,y) { this.zoomTo(x,y,this.zoomfactor) };
ZoomAndPanViewBox.prototype.zoomOutTo = function (x,y) { this.zoomTo(x,y,1/this.zoomfactor) };

/** 
 * pan the viewbox by the specified offsets
 */
ZoomAndPanViewBox.prototype.panBy = function ( offset_x, offset_y )
{
    offset_x = offset_x || 0;
    offset_y = offset_y || 0;

    var new_x	= this.x + offset_x; 
    var new_y	= this.y + offset_y; 

    this.setViewbox(new_x, new_y, this.height, this.width);
};

/**
 * pan the viewbox so that the new view is centered as specified
 */
ZoomAndPanViewBox.prototype.panTo = function ( center_x, center_y )
{
    this.zoomTo ( center_x, center_y, 1);
};

////////////////////////////////////
//	    GESet
////////////////////////////////////

/**
 * Implements the Collection interface for a set of graph elements, each graph
 * element has a unique id property
 */
function GESet() 
{
    this._elements = new Object();
}


/**
 * adds element to GESet
 * returns true if element wasn't in set 
 */
GESet.prototype.add = function(element) 
{
    if (!this.contains(element)) {
	this._elements[element.id] = element;
	return true;
    }
    return false;
}

/* removes all of the elements from the GESet */ 
GESet.prototype.clear = function() 
{
    delete this._elements;
    this._elements = new Object();
};

/* true if object is contained in collection */ 
GESet.prototype.contains = function (element) 
{
    if (this._elements[element.id]) return true;
    return false;
};

/* true if this contains no elements */
GESet.prototype.isEmpty = function() 
{ 
    return (this.size() == 0);
};

/* return an iterator over the elements */
GESet.prototype.iterator = function() 
{
    return new PlainGESetIterator(this);
};

/* return true if the set contains the element */
GESet.prototype.remove = function(element) 
{
    if (this.contains(element)) {
	delete this._elements[element.id];
	return true;
    }
    return false;
};

/* return size */
GESet.prototype.size = function() 
{
    var size = 0;
    for (var i in this._elements) size++;
    return size;

};

/* return elements in collection as an array */
GESet.prototype.toArray = function() 
{ 
    var a = new Array();
    for (var i in this._elements) a.push(this._elements[i]);
    return a;
};

/* accepts an array of elements and adds them to the set */
GESet.prototype.addAll = function(array) 
{
    /* add if not already a member */
    for (var i in array) {
	this.add(array[i]);
    }
};


////////////////////////////////////
//        PlainGESetIterator
////////////////////////////////////
/**
 * Iterates over the elements of a set using a null filter.  This class also
 * demonstrates the Iterator interface
 *
 * This class can be extended to apply a custom filter by overriding the filter
 * function
 */

function PlainGESetIterator(set) 
{
    if (arguments.length > 0) {
	this.init(set);
    }
}

/**
 *	init
 */
PlainGESetIterator.prototype.init = function(set) 
{
    this.set = set.toArray();
    this._currIndex = -1;
    this._hasNext = false;
    this._nextIndex = null; 
};


/* move to first item. return false if empty */
PlainGESetIterator.prototype.first = function()
{
    this._currIndex = -1;

    if (!this._findNext()) return false; 
    this._currIndex = this._nextIndex;
    this._findNext();
    return true;
};

/* true if iteration has more elements */
PlainGESetIterator.prototype.hasNext = function() 
{
    return this._hasNext;
};

/* move to the next item in the iteration */
PlainGESetIterator.prototype.next = function()
{
    if (!this.hasNext()) return false; 
    this._currIndex = this._nextIndex;
    this._findNext();
    return true;
};

/* return the current element in the iteration */
PlainGESetIterator.prototype.currentItem = function() 
{
    return this.set[this._currIndex]; 
};

/**
 * set _nextIndex to next valid item index.  
 * set _hasNext appropriately and return */
PlainGESetIterator.prototype._findNext = function() 
{
    for (this._nextIndex = this._currIndex + 1 ; this._nextIndex <
	    this.set.length ; this._nextIndex++) {
	if ( !this.filter(this.set[this._nextIndex]) ) {
	    break;
	}
    }
    return this._hasNext = (this._nextIndex < this.set.length);
}

/* an abstract boolean function which operates an element in the set */
PlainGESetIterator.prototype.filter = function (element) 
{
    return false;
}; 

PlainGESetIterator.prototype.toArray = function ()
{
    var arr = new Array();
    for (var i in this.set) {
	if ( !this.filter(this.set[i]) ) {
	    arr[arr.length] = this.set[i];
	}
    }
    return arr;
};


function inherit (parent, child)
{
    child.prototype = new parent();
    child.prototype.constructor = child;
    child.superclass = parent.prototype;
}

/****
 *
 *	  GEAttrSetIterator
 *
 * Iterates over the elements of a set filled with graph elements. Takes an
 * attribute and regexp via the
 .  This class also
 * demonstrates the Iterator interface
 *
 * This class can be extended to apply a custom filter by overriding the filter
 * function
 *
 * returns null if first() has not been called after creation
 *
 ******/

inherit(PlainGESetIterator, GEAttrSetIterator);

/*****
 *
 *	constructor
 *
 *****/
function GEAttrSetIterator(set) 
{
    if (arguments.length > 0) {
	this.init(set);
    }
}

/*****
 *
 *	init
 *
 *****/
GEAttrSetIterator.prototype.init = function (set) 
{
    GEAttrSetIterator.superclass.init.call(this, set);    
    this.attr	 = "";
    this.pattern = "";
};

GEAttrSetIterator.prototype.setAttrPattern = function (attr, pattern) 
{
    this.attr = attr;
    this.pattern = pattern;
};

GEAttrSetIterator.prototype.filter = function (element) 
{
    //simple matching
    return (element[this.attr] != this.pattern);
};

/////////////////////
//    Status 
/////////////////////

function status (str) {
    var instr = svgdoc.getElementById("instruction");
    instr.firstChild.data = str;
}

function showCoord (viewport) {
    this.viewport = viewport;
    this.viewport.addEventListener("mousemove", this, false);
    this.viewport.addEventListener("mouseup", this, false);
}

showCoord.prototype.handleEvent = function (evt) 
{
    switch(evt.type) {
	case "mousemove":
	    status ("(" + evt.clientX + ", " + evt.clientY + ") " + Math.random());

	break;
	case "mouseup":
	    this.viewport.removeEventListener("mousemove", this, false);
	this.viewport.removeEventListener("mouseup", this, false);
	status (" --- ");
	break;
    }
};


////////////////////////
//    ModelGXLDecoder
////////////////////////

function ModelGXLDecoder () { 
};

/* accept a document fragment containing a GXL representation of a rigi model
 * and return a DefaultGraphModel */
ModelGXLDecoder.prototype.fromGXL = function (doc) 
{
    this.model = new DefaultGraphModel();
    var Node = this.model.getNodeHandler()
	var Arc = this.model.getArcHandler()

	var str ='';
    /* fetch all nodes */
    var nodes = doc.getElementsByTagName('node');
    for(var i = 0; i < nodes.length; i++)
    {
	node	= nodes.item(i);
	id	= node.getAttribute("id");
	type	= node.getElementsByTagName('type').item(0).getAttribute("xlink:href");

	this.model.createNode(id, type);

	//attrs
	attrs = node.getElementsByTagName('attr');
	for (var j = 0; j < attrs.length; j++){
	    attr    = attrs.item(j);
	    name    = attr.getAttribute("name");
	    value   = attr.getElementsByTagName('string').item(0).firstChild.data;

	    Node.setAttribute(this.model.nodes[id], name, value);
	}

    }

    /* fetch all arcs */
    var arcs = doc.getElementsByTagName('edge');
    for(var i = 0; i < arcs.length; i++)
    {
	arc	= arcs.item(i);
	id	= arc.getAttribute("id");
	from	= this.model.nodes[arc.getAttribute("from")]; 
	to	= this.model.nodes[arc.getAttribute("to")]; 
	type	= arc.getElementsByTagName('type').item(0).getAttribute("xlink:href");

	this.model.createArc(id, type, from, to);
    }

    return this.model;
};


////////////////////////
//    ModelGXLEncoder
////////////////////////


function ModelGXLEncoder () { 
};

ModelGXLEncoder.prototype.toGXL = function (model) 
{
    var str = "";
    str +='<?xml version="1.0"?>';
    str +='<!DOCTYPE gxl SYSTEM "http://www.gupro.de/GXL/gxl-1.0.dtd">';
    str +='<gxl xmlns:xlink="http://www.w3.org/1999/xlink">';
    str += '<graph id="test">';
    for (var i in model.nodes) {
	str += this.printNode(model.nodes[i]);
    }
    for (var i in model.arcs) {
	str += this.printArc(model.arcs[i]);
    }
    str += "</graph>";
    str += "</gxl>";
    alert (str);
    return str;
    
};

ModelGXLEncoder.prototype.printNode = function (node) 
{
    var str = "<node ";
    str += this.printXMLAttribute('id', 'n' + node.id);
    str += '>';
    str += this.printType(node.type);
    /* attributes */
    for (var attr in node.attribute) {
	str += this.printAttr(attr, node.attribute[attr]);
    }
    str += "</node>";
    return str;
};

ModelGXLEncoder.prototype.printType = function (type) 
{
    var str = '<type xlink:href="' + type +'" />';
    return str;
};

/* print GXL type attr */
ModelGXLEncoder.prototype.printAttr = function (name, value) 
{
    //TODO handle different data types
    var str ='<attr name="' + name + '">';
    str += '<string>'+ value  +'</string>';
    str += '</attr>';
    return str;
};


ModelGXLEncoder.prototype.printXMLAttribute = function (name, value) 
{
    var str = name + '="' + value + '" ';
    return str;
};

ModelGXLEncoder.prototype.printArc = function (arc) 
{
    var str = "<edge ";
    str += this.printXMLAttribute('id', 'e' + arc.id);
    str += this.printXMLAttribute('from', 'n' + arc.src.id);
    str += this.printXMLAttribute('to', 'n' + arc.dst.id);
    str += '>';
    str += this.printType(arc.type);
    var attrs = "";
    /* attributes */
    for (var attr in arc.attribute) {
	attrs += this.printAttr(attr, arc.attribute[attr]);
    }
    str += attrs;
    str += "</edge>";
    return str;
};

////////////////////////
//    RCLScriptEngine
////////////////////////

/* 
   Encapsultates a scripting interface to this application
 */

function RCLScriptEngine (graph) 
{
    if (arguments.length > 0) {
	this.init (graph)
    }
}

/*
   general form is
   <command name> [argument [argument ..] ]
   <graph> <
 */

RCLScriptEngine.prototype.init = function (graph)
{
    this.graph = graph;
    this.bindings = new Object();

    this.bindings['rcl_gridlayout'] = SelectionGridLayoutCommand;
    this.bindings['rcl_select_all']	 = SelectAllCommand;;
    this.bindings['SelectDeadCode']	= SelectDeadCodeCommand;
    this.bindings['SelectionClear']	= SelectionClearCommand;
    this.bindings['SelectComplement']	= SelectComplementCommand;
    this.bindings['rcl_select_nodes_attr']	= SelectNodesByAttributeCommand;
    this.bindings['rcl_select_type']	= SelectNodesByTypeCommand;
    this.bindings['rcl_select_rt']	= SelectReverseTreeCommand;
    this.bindings['rcl_select_fwt']	= SelectForwardTreeCommand;
    this.bindings['ToggleArcFilter']	= ToggleArcFilterCommand;
    this.bindings['rcl_filter_nodetype']	= ToggleNodeFilterCommand;
    this.bindings['SelectionSetSVGAttribute']	= SelectionSetSVGAttributeCommand;
    this.bindings['SelectionSetSVGStyle']	= SelectionSetSVGStyleCommand;
    this.bindings['rcl_zoom']	= ZoomCommand;
    this.bindings['rcl_zoomto']	= ZoomToCommand;
    this.bindings['rcl_panto']	= PanToCommand;
    this.bindings['rcl_panby']	= PanByCommand;
    this.bindings['rcl_spring_layout']	= SpringLayoutCommand;
    this.bindings['rcl_undo']	= UndoLastCommand;
    this.bindings['rcl_redo']	= RedoLastCommand;
    this.bindings['rcl_collapse'] = collapseCommand;
    this.bindings['rcl_expand']	= expandCommand;
    this.bindings['rcl_snapShot'] = snapShotCommand;
};

/* accept a string which is a scripting command 
   and return an object which honors the 'Command' interface and encapsulates
   this command.

NOTE: using this paradigm we could pass in multiple commands in a string and
return a MacroCommand object which runs several commands, in order etc.. 

 */

RCLScriptEngine.prototype.parse = function (str)
{
    // TODO should split into tokens in a more generic way
    var aTokens = str.split(/\s+/);
    var sCommandname = aTokens[0];

    var aArgs = new Array();
    for (var i = 1 ; i < aTokens.length ; i++) {
	var token = aTokens[i];
	var str;
	if ( (str = token.match(/^\"(.*)\"$/)) ) {
	    aArgs.push(str[1]);
    } else if ( token.search(/^[+|-]?\d+$/) != -1 ) {
	aArgs.push(parseInt(aTokens[i]));
    } else if ( token.search(/^[+|-]?\d+\.\d*$/) != -1 ) {
	aArgs.push(parseFloat(aTokens[i]));
    } else {
	//leave it as a string
	aArgs.push(aTokens[i]);
    }
    }
    return this.createCommand(sCommandname, aArgs);
};

RCLScriptEngine.prototype.createCommand = function (name, args)
{
    var cCommand = this.bindings[name]; // the class constructor
    if (!cCommand) {
	throw new GeneralError("no such command : " + name);
    }
    args.unshift(graph); // set graph as first element
    var command = new cCommand();
    command.init.apply(command, args);
    return command;
};

RCLScriptEngine.prototype.printFunctionDesc = function (name) 
{
    var desc = this.bindings[name].prototype.desc;
    return desc;
}

RCLScriptEngine.prototype.printFunctionSignature = function (name) 
{
    var func = this.bindings[name].prototype.init.toString();
    var match_arr = func.match(/function\s*\((.*)\)/);

    if (match_arr.length != 2) {
	return "oops length is" + match_arr.length;
    }
    var sig = match_arr[1].replace(/^graph\,*\s*/,"").replace(/,\s/g, "> <");
    if (sig.length > 0) sig = "<" + sig + ">";
    return sig;
}

////////////////////////////////////
//	EventListenerManager	
////////////////////////////////////


/**
 * Handles keeping track of event listeners, and listener notification
 * for the target 
 *
 * @param   target   the object to manager
 */ 
function EventListenerManager (target) 
{
    this.target = target;
    this.types = new Object(); // event types;
}
/**
 * Add event type to list of supported events
 * @param   type	name of event
 * @param   getEvent	target's method which returns event to dispatch
 */
EventListenerManager.prototype.registerType = function (type, getEvent) 
{
    this.types[type] = new Object();
    this.types[type].listeners = new Array();
    this.types[type].getEvent = getEvent;
}

EventListenerManager.prototype.addEventListener = function (type, listener) 
{
    if (this.types[type]) {
	//TODO check that listener is not already in the list
	this.types[type].listeners.push(listener);
	return this.types[type].listeners.length;
    } else {
	throw new GeneralError("EventListenerManager: unsupported event" +
		type);
    }
}

/**
 * remove listener and return the number of listeners
 */
EventListenerManager.prototype.removeEventListener = function (type, listener) 
{
    if (this.types[type]) {
	var listeners = this.types[type].listeners;
	for (var i = 0; i < listeners.length; i++) { 
	    if (listeners[i] == listener) {
		listeners.splice(i, 1);
		return listeners.length; 
	    }
	}
	return null;
    } else {
	throw new GeneralError("EventListenerManager: unsupported event" +
		type);
    }
}

/**
 *  gets appropriate event from Target and dispatches it 
 */
EventListenerManager.prototype.handleEvent = function (evt) 
{
    if (this.types[evt.type]) {
	var event = this.types[evt.type].getEvent.call(this.target, evt);
	this.dispatchEvent(event);
    } 
    // ignore unsupported events
};

/**
 * dispatches the event to the appropriate event handlers
 */
EventListenerManager.prototype.dispatchEvent = function (event) 
{
    type = event.type;

    var listeners = this.types[type].listeners;
    for (var i = 0; i < listeners.length; i++) { 
	var listener = listeners[i];
	switch (typeof listener) {
	    case "object" : listener.handleEvent(event); break;
	    case "function" : listener(event); break;
	    default: // remove from list ?
	    break;
	}

    }
};


////////////////////////////////////
//	Viewport	
////////////////////////////////////

/**
 * The viewport object encapsulates the entire SVG viewport.
 *
 */

function Viewport () {

    this.svgnode = svgdoc.documentElement;
    this.svgnode.addEventListener("SVGResize", this, false);
    this.svgnode.addEventListener("SVGZoom", this, false);
    this.svgnode.addEventListener("SVGScroll", this, false);

    this.viewbox = new ViewBox( this.svgnode );

    this.eventmanager = new EventListenerManager(this);
    this.eventmanager.registerType("mousemove", this._genericEvent);
    this.eventmanager.registerType("mouseup", this._genericEvent);

    this.eventpane = svgdoc.createElementNS(svgns, "rect");
    this.eventpane.setAttributeNS(null, "style",
	    "visibility:hidden;fill:white;pointer-events:all;");
    this._positionEventpane();
}

/**
 * @param   capture	do nothing with it
 */
Viewport.prototype.addEventListener = function (type, listener, capture) 
{
    switch (type) {
	case 'mousemove':
	    var nListeners =
	    this.eventmanager.addEventListener(type, listener);
	    if (nListeners == 1) {
		this._activateEventpane();
		this.eventpane.addEventListener(type, this.eventmanager, capture);
	    } // otherwise assume it's already been done or shouldn't be done
	    break;
	case 'mouseup':
	    this.eventmanager.addEventListener(type, listener);
	    this.eventpane.addEventListener(type, this.eventmanager, capture);
	    break;
	default:
	    throw new GeneralError("Viewport: unsupported event" + event);
    }
}

Viewport.prototype.removeEventListener = function (type, listener, capture) 
{
    switch (type) {
	case "mousemove":
	    var nListeners = 
		this.eventmanager.removeEventListener(type, listener);
	    if (nListeners == 0) {
		this.eventpane.removeEventListener(type, listener, capture); 
		this._deactivateEventpane();
	    }
	    break;
	case "mouseup": 
	    var nListeners =
		this.eventmanager.removeEventListener(type, listener);
	    if (nListeners == 0) {
		this.eventpane.removeEventListener(type, listener, capture); 
	    }
	    break;
	default: 
	    throw new GeneralError("Viewport: unsupported event" + type);
    }
}

/**
 *  event handler
 */
Viewport.prototype.handleEvent = function (evt) 
{
    var type = evt.type;
    var target = evt.getTarget();

    switch (type) {
	case "SVGResize":   // fallthrough
	case "SVGZoom":	    // fallthrough
	case "SVGScroll":
	this.viewbox = new ViewBox( this.svgnode );
	this._positionEventpane();
	break;
    }
}
Viewport.prototype._activateEventpane = function ()
{
    this.eventpane.style.setProperty("display", "inline");
    this.svgnode.appendChild(this.eventpane);
};

/**
 * make sure event pane is properly positioned and sized to receive events
 */
Viewport.prototype._positionEventpane = function ()
{
    // TODO properly position when pan and zoom 
    this.eventpane.setAttributeNS(null, "x", this.viewbox.x);
    this.eventpane.setAttributeNS(null, "y", this.viewbox.y);
    this.eventpane.setAttributeNS(null, "height", "100%");
    this.eventpane.setAttributeNS(null, "width", "100%");
}

Viewport.prototype._deactivateEventpane= function ()
{
    this.eventpane.style.setProperty("display", "none");
    this.svgnode.removeChild(this.eventpane);
};

/**
 *
 */ 
Viewport.prototype._genericEvent = function (evt)
{
    var event = new GenericEvent(evt.type, this);
    for (var i in evt) {
	switch (typeof i) {
	    case "function": // fallthrough
	    case "object": continue; break;
	    default: event[i] = evt[i];
	}
    }
    event.target = this;
    return event;
};

////////////////////////////////////
//	  GenericEvent
////////////////////////////////////

function GenericEvent (type, target) 
{
    this.type = type;
    this.target = target;
}

GenericEvent.prototype.getTarget = function () { return this.target; };

////////////////////////////////////
//	Error Objects
////////////////////////////////////
inherit(Error, GeneralError);
function GeneralError (message) {
    this.message = message;
};

/************************************************************************
 *                                                                       *
 *                        Utility Functions				*
 *                                                                       *
 *************************************************************************/

function getSVGDocument(node)	{
    // given any node of the tree, will obtain the SVGDocument node.
    // must be careful: a Document nodes ownerDocument is null!
    if (node.getNodeType() != 9)	// if not DOCUMENT_NODE
	return node.getOwnerDocument();
    else
	return node;
}

/**
 * returns the font height for the inherited font of the parent SVG node, 
 * font height is in units relative to the parent. 
 */
function getFontHeight (parent) 
{
    var dummy = svgdoc.createElementNS(svgns, "rect");
    dummy.setAttributeNS(null, "x", 0);
    dummy.setAttributeNS(null, "y", 0);
    dummy.setAttributeNS(null, "height", "1em");
    dummy.setAttributeNS(null, "width", "1em");
    parent.appendChild(dummy);
    var height = dummy.getBBox().height;
    parent.removeChild(dummy);
    return height;
};

/**
 * encode an object with string fields and values using the
 * application/x-www-form-urlencoded content-type to a string
 *
 * @param   obj	    object to be encoded
 * @return  string  encoding of the obj
 */
function encodeObject (obj) 
{
    var str = "";
    for (var i in obj) str += i + '=' + obj[i] + '&';
    return encodeURI(str.slice(0,str.length-1));
}

/**
 * decode an object with string fields and values using the
 * application/x-www-form-urlencoded content-type to a object 
 *
 * @param   str	    encoded object
 * @return  object  decoded object
 */
function decodeObject (str) 
{
    var o = new Object();
    var r = str.split("&");
    for ( var i in r ) {
	var f = r[i].split('=');
	o[decodeURI(f[0])] = decodeURI(f[1]);
    }
    return o;
}


/*****
 *
 *   transformFromScreenSpace
 *
 *  transform the given viewport space coordinates to the space
 *  of the target SVGElement
 *****/
function transformFromScreenSpace (x, y, target) 
{
    var iCTM = getTransformToViewportSpace(target).inverse();
    var svgRoot    = svgdoc.documentElement;
    var pan	     = svgRoot.getCurrentTranslate();
    var zoom	     = svgRoot.getCurrentScale();
    var worldPoint = svgdoc.documentElement.createSVGPoint();

    worldPoint.x = (x - pan.x) / zoom;
    worldPoint.y = (y - pan.y) / zoom;

    return worldPoint.matrixTransform(iCTM);
}

/*****
 *
 *   getTransformToViewportSpace
 *
 *   returns the cumulative transformation matrix for an SVG element to user
 *   space.  This takes into account multiple embedded svg elements with
 *   viewboxes.
 *   
 *****/
function getTransformToViewportSpace (node) 
{

    var CTM = svgdoc.documentElement.createSVGMatrix();
    do {
	//TODO do we need to handle containers that have viewBox other than <svg>???
	if (node.nodeName == "svg") {
	    var viewBox = new ViewBox(node);
	    CTM = viewBox.getTM().inverse().multiply(CTM);
	}

	CTM = node.getCTM().multiply(CTM);
    } while ( (node = node.parentNode) != svgdoc );
    return CTM;
}

/*****
 *
 *  getTransformToElement
 *
 *  returns the cumulative transformation matrix for an SVG element to
 *  another, (for the moment) ancestor element. This takes into account
 *  multiple embedded svg elements with viewboxes.
 *
 *  if 'ancestor' is not a true ancestor then this function acts like
 *  getTransformToUserSpace 
 *   
 *****/
function getTransformToElement (from, to) 
{
    to = to || svgdoc.documentElement;

    var fromVS_CTM  = getTransformToViewportSpace(from);
    var toVS_CTM    = getTransformToViewportSpace(to);

    return  fromVS_CTM.multiply(toVS_CTM.inverse());
}

/*****
 *
 *   SVGRect_multiply
 *
 *   returns the transformation of the givenSVGRect by the given matrix
 *   
 *****/
function SVGRect_multiply (rect, M) 
{
    var r = svgdoc.documentElement.createSVGRect();
    var point = svgdoc.documentElement.createSVGPoint();	

    // upper left corner
    point.x = rect.x; point.y = rect.y;
    point = point.matrixTransform(M);
    r.x = point.x;
    r.y = point.y;

    //width and height
    point.x = rect.x + rect.width;
    point.y = rect.y + rect.height;
    point = point.matrixTransform(M);
    r.width = point.x - r.x;
    r.height = point.y - r.y;
    return r;
}

/*****
 *
 *   Move the nodes inside the panel
 *   
 *****/
function moveRight(evt) {
    if(timerID == null) {
       var ou=svgdoc.getElementById("stop");
       ou.setAttribute("fill", "red");
       ou = evt.target;
       ou.setAttribute("fill", "limegreen");
       timerID = window.setInterval("right()",30);
    }
}

function right(){
    for(var i in graph.view.nodes){
       graph.view.nodeview.translate(graph.view.nodes[i], 2, 0);
    }
};

function moveLeft(evt) {
     if(timerID == null) {
            var ou=svgdoc.getElementById("stop");
            ou.setAttribute("fill", "red");
            ou = evt.target;
            ou.setAttribute("fill", "limegreen");
            timerID = window.setInterval("left()",10);
    }
};

function left() {
    for(var i in graph.view.nodes){
       graph.view.nodeview.translate(graph.view.nodes[i], -2, 0);
    }
};

function moveUp(evt) {
     if(timerID == null) {
            var ou=svgdoc.getElementById("stop");
            ou.setAttribute("fill", "red");
            ou = evt.target;
            ou.setAttribute("fill", "limegreen");
            timerID = window.setInterval("up()",30);
    }
}

function up() {
    for(var i in graph.view.nodes){
       graph.view.nodeview.translate(graph.view.nodes[i], 0, 2);
    }
};

function moveDown(evt) {
     if(timerID == null) {
            var ou=svgdoc.getElementById("stop");
            ou.setAttribute("fill", "red");
            ou = evt.target;
            ou.setAttribute("fill", "limegreen");
            timerID = window.setInterval("down()",30);
    }
}

function down() {
    for(var i in graph.view.nodes){
       graph.view.nodeview.translate(graph.view.nodes[i], 0, -2);
    }
};

function stopMove(evt){
    if(timerID!=null) {
       window.clearInterval(timerID);
       timerID=null;
       var ou= evt.target;
       ou.setAttribute("fill", "white");
       ou=svgdoc.getElementById("moveLeft");
       ou.setAttribute("fill", "black");
       ou=svgdoc.getElementById("moveRight");
       ou.setAttribute("fill", "black");
       ou=svgdoc.getElementById("moveUp");
       ou.setAttribute("fill", "black");
       ou=svgdoc.getElementById("moveDown");
       ou.setAttribute("fill", "black");
    } 
}       

/************************************************************************
 *                                                                       *
 *                         History Window			*
 *                                                                       *
 *************************************************************************/

function createHistoryWindow() 
{
    var root = svgdoc.documentElement;
    history_window = new Window("History");
    root.appendChild(history_window.container);
    history_window.contents = new Container(null);
    history_window.add(history_window.contents); 
    history_window.doDeepPreferedLayout();
    history_window.setVisible(false);

    history_window.background.style.setProperty('fill-opacity', 0.9);
    history_window.contents.background.style.setProperty('fill', 'none');
    history_window.contents.layoutManager = new CellLayout(null, 1, null, 10);
    history_window.container.setAttributeNS(null, 'transform', "translate(-90,0)"); 
};

function populateWindow(){
    history_window.setVisible(true);
};

function backToHistory(evt){
   var str = evt.target.parentNode.getAttribute('id');
   id = str.substring(8,str.length+1);
   new historyCommand().execute(id);
};

function redStroke(evt){
   var str = evt.target.parentNode.getAttribute('id');
   id = str.substring(8,str.length+1);
   var ou = svgdoc.getElementById("r"+id);
   if(ou!=null) ou.style.setProperty("stroke", "red"); 
};

function blueStroke(evt){
   var str = evt.target.parentNode.getAttribute('id');
   id = str.substring(8,str.length+1);
   var ou = svgdoc.getElementById("r"+id);
   if(ou != null) ou.style.setProperty("stroke", "lightblue"); 
};

function deleteHistory(evt){
   var node = evt.target;
   var str = node.getAttribute('id');
   id = str.substring(2,str.length+1);
   var temp = confirm("Delete history " + id + "?");
   if(temp == true) deleteH(id);
};

function newHistoryWindow(id){
       history_window.setVisible(false);
       createHistoryWindow();
       currentHID = id+1;
       
       for (var temp = MAX_History; temp >1; temp--){
          var v1 = svgdoc.getElementById("g" + ++id);
          history_window.contents.add(v1);
       }  
}

function scrollUp(){
   if(hID<=MAX_History || currentHID == 1)  return;
   history_window.setVisible(false);
   createHistoryWindow();
   var contents = history_window.contents;
   if((currentHID - MAX_History)>1) currentHID = currentHID - MAX_History; 
   else currentHID = 1;
   id = currentHID;
   
   for (var temp = MAX_History; temp >0; temp--){
       var v1 = svgdoc.getElementById("g" + id++);
       contents.add(v1);
   } 
   history_window.doDeepPreferedLayout();
   history_window.setVisible(true);	
}

function scrollDown(){
       if(currentHID > (hID-MAX_History-1))  return;
       if((currentHID + MAX_History + MAX_History) < hID) currentHID = currentHID + MAX_History; 
       else currentHID = hID - MAX_History;
       id = currentHID;
       history_window.setVisible(false);
       createHistoryWindow();
       var contents = history_window.contents;
       for (var temp = MAX_History; temp >0; temp--){
            var v1 = svgdoc.getElementById("g" + id++);
            contents.add(v1);
       } 

       history_window.doDeepPreferedLayout();
       history_window.setVisible(true);
}

function deleteH(id){
   var v1 = svgdoc.getElementById("g" + id);
   v1.parentNode.removeChild(v1);

   var model= graph.getModel();
   if(id!=(hID-1)){  
      var number = hID-1;
      while(number!=id){   
         number2 = number -1;
         
         g = svgdoc.getElementById("histroy " + number);
         g.setAttribute('id',"histroy " + number2);
      
         g = svgdoc.getElementById("r" + number);
         g.setAttribute('id',"r" + number2);
      
         g = svgdoc.getElementById("rr" + number);
         g.setAttribute('id',"rr" + number2);
         
         g = svgdoc.getElementById("text" + number);
         g.setAttribute('id',"text" + number2);
         g.firstChild.setData("History " +number2);
      
         g = svgdoc.getElementById("g" + number);
         g.setAttribute('id',"g" + number2);
         number--;
      }//while
      while(id!=(hID-1)){   
         model.nodesHistory[id] = model.nodesHistory[++id];
         model.arcsHistory[--id] = model.arcsHistory[++id];   
         model.selectionHistory[--id] = model.selectionHistory[++id]; 
      } 
   }//if
   
   if(currentHID < (hID-MAX_History)){
      var v1 = svgdoc.getElementById("g" + (currentHID+MAX_History-1));
      history_window.contents.add(v1);
   } else if(currentHID != 1){
      history_window.setVisible(false);
      createHistoryWindow();
      id = --currentHID;
      for (var temp = MAX_History; temp >0; temp--){
          var v1 = svgdoc.getElementById("g" + id++);
          history_window.contents.add(v1);
      } 
      history_window.doDeepPreferedLayout();
      history_window.setVisible(true);
   }
   
   hID--;
   history_window.doDeepPreferedLayout();
}

function changeColor1(evt){
   var n = evt.target;
   if(n!=null) {
      n.style.setProperty("fill", "red"); 
      var str = n.getAttribute('id');
      id = str.substring(2,str.length+1);
      g = svgdoc.getElementById("text" + id);
      g.firstChild.setData("Delete " +id);
   }
};

function changeColor2(evt){
   var n = evt.target;
   if(n!=null) {
      n.style.setProperty("fill", "lightgrey"); 
      var str = n.getAttribute('id');
      id = str.substring(2,str.length+1);
      g = svgdoc.getElementById("text" + id);
      g.firstChild.setData("History " +id);
   }
};

function createHistoryControlWindow() 
{
    var root = svgdoc.documentElement;
    control_window = new Window("Control Panel");
    root.appendChild(control_window.container);
    control_window.contents = new Container(null);
    control_window.add(control_window.contents); 
    control_window.setVisible(false);

    control_window.background.style.setProperty('fill-opacity', 0.9);
    control_window.contents.background.style.setProperty('fill', 'none');
    control_window.contents.layoutManager = new CellLayout(1, null, null, 10);
  
    var g = svgdoc.createElement("g");
    var r1 = svgdoc.createElement("polygon");
    r1.setAttribute( "points", "0,0 28,0 14,-28");
    r1.addEventListener('click', scrollUp, false);
    r1.addEventListener('mouseover', greenTri1, false); 
    r1.addEventListener('mouseout', blackFill, false); 
    
    var r2 = svgdoc.createElement("polygon");
    r2.setAttribute( "points", "0,0 28,0 14,-24 0,-24 0,-28 28,-28 28,-24 14,-24");
    r2.addEventListener('click', scrollTo1, false);
    r2.addEventListener('mouseover', greenTri1, false); 
    r2.addEventListener('mouseout', blackFill, false); 
           
    var c1 = svgdoc.createElement("circle");
    c1.setAttribute( "r", "12");
    c1.addEventListener('click', new snapShotCommand(), false); 
    c1.addEventListener('mouseover', greenCircle, false); 
    c1.addEventListener('mouseout', blackFill, false); 

    var r3 = svgdoc.createElement("polygon");
    r3.setAttribute( "points", "0,0 28,0 14,24 0,24 0,28 28,28 28,24 14,24");
    r3.addEventListener('click', scrollToEnd, false);
    r3.addEventListener('mouseover', greenTri2, false); 
    r3.addEventListener('mouseout', blackFill, false); 
    
    var r4 = svgdoc.createElement("polygon");
    r4.setAttribute( "points", "0,0 28,0 14,28");
    r4.addEventListener('click', scrollDown, false);
    r4.addEventListener('mouseover', greenTri2, false); 
    r4.addEventListener('mouseout', blackFill, false); 
    
    var g1 = svgdoc.createElement("g");
    var r5 = svgdoc.createElement("ellipse");
    r5.setAttribute( "rx", "40");
    r5.setAttribute( "ry", "28");
    r5.setAttribute( "fill", "green"); 
    r5.setAttribute( "fill-opacity", "0.1");
    r5.setAttribute( "stroke", "black");
    
    var t1 = svgdoc.createElement("polygon"); 
    t1.setAttribute( "points", "-32,0 -16,-8 -16,8");
    t1.addEventListener('click', moveLeft, false);
    t1.setAttribute( "id", "moveLeft");
    
    var t2 = svgdoc.createElement("polygon");
    t2.setAttribute( "points", "32,0 16,-8 16,8");
    t2.addEventListener('click', moveRight, false);
    t2.setAttribute( "id", "moveRight");
    
    var t3 = svgdoc.createElement("polygon");
    t3.setAttribute( "points", "-8,-10 8,-10 0,-26");
    t3.addEventListener('click', moveDown, false);
    t3.setAttribute( "id", "moveDown");
        
    var t4 = svgdoc.createElement("polygon");
    t4.setAttribute( "points", "-8,10 8,10 0,26");
    t4.addEventListener('click', moveUp, false);
    t4.setAttribute( "id", "moveUp");
    
    var t5 = svgdoc.createElement("rect");
    t5.setAttribute( "height", "10");
    t5.setAttribute( "width", "16");
    t5.setAttribute( "x", "-8");
    t5.setAttribute( "y", "-5");
    t5.setAttribute( "fill", "white");
    t5.setAttribute( "stroke", "black");
    t5.setAttribute( "id", "stop");
    t5.addEventListener('click', stopMove, false);
    
    g1.appendChild(r5);
    g1.appendChild(t1);
    g1.appendChild(t2);
    g1.appendChild(t3);
    g1.appendChild(t4);
    g1.appendChild(t5);
    
    /*var g2 = svgdoc.createElement("g");
    var g21 = svgdoc.createElement("ellipse");
    g21.setAttribute( "rx", "20");
    g21.setAttribute( "ry", "15");
    g21.setAttribute( "fill", "none"); 
    g21.setAttribute( "stroke", "black");
    
    var g22 = svgdoc.createElement("circle");
    g22.setAttribute( "r", "5");
    g22.setAttribute( "x", "10");
    g22.setAttribute( "y", "5");
    g22.setAttribute( "fill", "lightgreen"); 
    g21.setAttribute( "stroke", "black");
    
    var g23 = svgdoc.createElement("circle");
    g23.setAttribute( "r", "5");
    g23.setAttribute( "x", "10");
    g23.setAttribute( "y", "15");
    g23.setAttribute( "fill", "lightgreen"); 
    g23.setAttribute( "stroke", "black");
    
    g2.appendChild(g21);
    g2.appendChild(g22);
    g2.appendChild(g23);*/
    
    control_window.contents.add(r1);
    control_window.contents.add(r2);
    control_window.contents.add(c1);
    control_window.contents.add(r3);
    control_window.contents.add(r4);
    control_window.contents.add(g1);
    //control_window.contents.add(g2);
    control_window.doDeepPreferedLayout(); 
};

function populateControlWindow(){
    control_window.container.setAttributeNS(null, 'transform', "translate(495,0)"); 
    control_window.setVisible(true);
};

function scrollTo1(){
   if(hID<=MAX_History || currentHID == 1) {
            return;
   }
   history_window.setVisible(false);
   createHistoryWindow();
   var contents = history_window.contents;
   currentHID = 1;
   id = 1;
      
   for (var temp = MAX_History; temp >0; temp--){
       var v1 = svgdoc.getElementById("g" + id++);
       contents.add(v1);
   } 
   history_window.doDeepPreferedLayout();
   history_window.setVisible(true);	
}

function scrollToEnd(){
   if(currentHID > hID - MAX_History) {
        return;
   }
   history_window.setVisible(false);
   createHistoryWindow();
   var contents = history_window.contents;
   currentHID = hID - MAX_History;
   id = currentHID;
      
   for (var temp = MAX_History; temp >0; temp--){
       var v1 = svgdoc.getElementById("g" + id++);
       contents.add(v1);
   } 
   history_window.doDeepPreferedLayout();
   history_window.setVisible(true);	
}

function greenCircle(evt){
   ou = evt.target;
   if(ou!=null) ou.style.setProperty("fill", "lightgreen"); 
};

function blackFill(evt){
   ou = evt.target;
   if(ou != null) ou.style.setProperty("fill", "black"); 
};

function greenTri1(evt){
   ou = evt.target;
   if(ou!=null && currentHID>1) ou.style.setProperty("fill", "lightgreen"); 
};

function greenTri2(evt){
   ou = evt.target;
   if(ou!=null && currentHID < hID - MAX_History) ou.style.setProperty("fill", "lightgreen"); 
};

/************************************************************************
 *                                                                       *
 *                         Demo Window			*
 *                                                                       *
 *************************************************************************/


function createDemoWindow() 
{
    var root = svgdoc.documentElement;
    demoWindow = new Window("Demo: click the arrow to continute.");
    root.appendChild(demoWindow.container);
    demoWindow.contents = new Container(null);
    demoWindow.add(demoWindow.contents); 

    demoWindow.setVisible(false);
        demoWindow.background.style.setProperty('fill-opacity', 0.9);
        demoWindow.contents.background.style.setProperty('fill', 'none');
        demoWindow.contents.layoutManager = new CellLayout(1, null, null, 10);
        demoWindow.container.setAttributeNS(null, 'transform', "translate(100,500)"); 
        
        var g = svgdoc.createElement("g");
        
        var node=svgdoc.createElement('text');
        node.setAttribute('x','40');
        node.setAttribute('y','13');
        node.setAttribute('id','demoText');
        node.setAttribute('style','text-anchor:middle;font-size:15;font-family:Arial;fill:black');
        texte=svgdoc.createTextNode("Right click the mouse and choose \"Control Panel\". ");
        node.appendChild(texte); 
        

        var r = svgdoc.createElement("polygon");
        r.setAttribute( "points", "32,40 62,40 62,32 82,50 62,68 62,60 32,60 32,40");
        r.setAttribute( "fill", 'lightgreen');
        r.setAttribute('fill-opacity', 0.3);
        r.setAttribute('id','demo1');
        r.style.setProperty("stroke", "lightblue");
        r.style.setProperty("stroke-width", "2"); 
        r.addEventListener('click', demo, false);
        
        g.appendChild(node);
        g.appendChild(r);
    
        demoWindow.contents.add(g);

    demoWindow.doDeepPreferedLayout();
        new cloneCommand().execute(0); 
};

function populateDemoWindow(){
    new historyCommand().execute(0);
    demoWindow.setVisible(true);
};


function demo(evt){

   var node = evt.target;
   var str = node.getAttribute('id');
   id = str.substring(4,str.length+1);
   switch(id){		 
    case '1': 		
     	populateControlWindow();
     	g = svgdoc.getElementById("demoText");
	g.firstChild.setData("Click the black circle to take a snapshot.");
        break;
    case '2':
        new snapShotCommand(graph).execute();
        g = svgdoc.getElementById("demoText");
        g.firstChild.setData("Click on menu \"select dead node\".");
        break;
    case '3':
       new SelectDeadCodeCommand(graph).execute();
       g = svgdoc.getElementById("demoText");
       g.firstChild.setData("Collapse two dead nodes.");
       break;
    case '4': 		
      new collapseCommand(graph).execute();
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Click the black circle to take a snapshot.");
      break;
    case '5': 		
      new snapShotCommand(graph).execute();
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Select a node.");
      break;
    case '6': 		
      var nodeselection = new Array();
      if(graph.view.nodes[8]!=null) nodeselection.push(graph.view.nodes[8]); // "8" node exists in list_adt.svg
      else nodeselection.push(graph.view.nodes["v1961"]);  //"v1961" node exists in ray.svg
      graph.nodeselectionhandler.setSelection([]);
      graph.nodeselectionhandler.addNodes(nodeselection);
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Click on menu \"select outgoing nodes\".");
      break; 
    case '7':
      new selectOutgoingCommand(graph).execute();
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Collapse these nodes.");
      break;
    case '8':
      new collapseCommand(graph).execute();
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Click the black circle to take a snapshot.");
      break;
    case '9': 		
      new snapShotCommand(graph).execute();
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Double click a node to view the node properties.");
      break;
    case '10': 		
      new showInfoCommand(graph).execute();
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Close the \"node properties\" window.");
      break;
    case '11': 		
      node_window.setVisible(false);
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Click the frame of \"history 1\" to back to history 1.");
      break;
    
    case '12': 		
      new historyCommand().execute(1);
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Click the frame of \"history 3\" to back to history 3.");
      break;    
    case '13': 		
      new historyCommand().execute(3);
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Click the text of \"history 1\" to delete history 1.");
      break;  
    case '14': 		
      deleteH(1);
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("Click \"expand\" to expand the collapsed node.");
      break;  
    case '15': 			
      new expandCommand(graph).execute();
      g = svgdoc.getElementById("demoText");
      g.firstChild.setData("select the dead node.");
      break; 
    case '16':
       new SelectDeadCodeCommand(graph).execute();
       g = svgdoc.getElementById("demoText");
       g.firstChild.setData("Click \"expand\" to expand the collapsed node.");
       break; 
    case '17': 		
         new expandCommand(graph).execute();
         g = svgdoc.getElementById("demoText");
         g.firstChild.setData("end demo.");
      break;
    default: 		
         deleteH(1);
         deleteH(1);
         new historyCommand().execute(0);
         demoWindow.setVisible(false);
         g = svgdoc.getElementById("demoText");
         g.firstChild.setData("Right click the mouse and choose \"Control Panel\". ");
         history_window.setVisible(false);
         control_window.setVisible(false);
         id = 0;
    }
    node.setAttribute('id',"demo" + ++id);
}
