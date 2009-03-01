<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\blocks\InsertionTarget.java</file>
    <packageref>com.ideanest.dscribe.mixt.blocks</packageref>
    <import>org.exist.fluent.Node</import>
    <import>com.ideanest.dscribe.mixt.TransformException</import>
    <interface fullName="com.ideanest.dscribe.mixt.blocks.InsertionTarget" implName="com.ideanest.dscribe.mixt.blocks.InsertionTarget" line="7" modifiers="public" name="InsertionTarget" xml:id="j1-919">
        <method line="9" modifiers="" name="insert" xml:id="j1-920">
            <returns>
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="10" modifiers="" name="canInsertMultiple" xml:id="j1-921">
            <returns>
                <type>boolean</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
    </interface>
</unit>