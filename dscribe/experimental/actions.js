function accessors-to-attributes-by-prefix() {
	var getMatcher = "";
	for each (var prefix in this.getterPrefixes.split()) {
		if (!getMatcher.isEmpty()) getMatcher += " and ";
		getMatcher += "starts-with(" + prefix + ", name/text())";
	}  
	for each (var getter in base.all("//operation[not(mod/@rule=$_1)][$2][not(param) and type]", this.id, getMatcher)) {
		var name = getter.single("name/text()").value();
		if (name.startsWith("get")) name = name.substring(3);
		else if (name.startsWith("is")) name = name.substring(2);
		else assert(false);
		var type = getter.single("type");
		var setters = getter.all("sibling::operation[name/text() eq $_1][not(type) and count(param)=1 and deep-equal(param/type, $_2)", "set"+name, type);
		switch(setters.size()) {
			case 0:
	}
}

transform(source1, source2, ...)
	.append(target1, function(builder) {builder.elem(...);})
	.remove(target2)
	.commit();

Action:
	applicable (XMLList, Manager) -> Command

Manager:
	activate(Command)
	add(Rule)
	remove(Rule)
	replace(Rule, Rule)
	
Command:
	menu item form
	details form
	amplify form
	do
	undo

Rule:
	scope -> id
	cancels -> rule id list
	apply -> code

how to select targets?
how to avoid selecting previously processed targets?
how to avoid endlessly stacking up conflicting/redundant rules?
