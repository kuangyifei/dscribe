module("http://ideanest.com/reef/js/globalrules.js", function() {
	var r = module("http://ideanest.com/reef/js/rules.js");
	var db = Packages.com.ideanest.reef.db;
	
	r.rule("createDiagram", function() {
		method("execute", function() {
			if (space.query().exists("uml:diagram[@xml:id=$_1/@diagram]", def)) return false;
			this.space.children().get("diagrams").documents().build(db.Name.adjust(def.query().single("@diagram").value()))
				.elem("uml:diagram").attr("kind", def.query().single("@kind")).end("uml:diagram").commit();
			return true;
		});
	});
});
