lz.uml_diagram.addProperty('gatherPossibleActions', function(box, targets) {
	box.setTitle("Diagram");
	box.addLink({icon: 'iconDelete', text: "Do something", actionCreator: function() {}});
});

lz.uml_attribute.addProperty('gatherPossibleActions', function(box, targets) {
	if (targets.length != 1) return;
	var target = targets[0];
	box.setTitle("Attribute");
	box.addLink({icon: "iconDelete", text: "Delete", actionCreator: function(parent) {
		return new lz.a_delete_attribute(parent, {
			scope: 'diagram', targetSelector: 'by-id', location: 'in-className',
			diagram: target.query('ancestor::uml:diagram/@xml:id')[0].atomized(),
			selection: target.xml_id,
			elemName: target.query('uml:var/uml:name')[0].atomized(),
			elemType: target.query('uml:var/uml:type')[0].atomized(),
			className: target.query('ancestor::uml:class[1]/uml:blockname')[0].atomized()
		});
	}});
});
