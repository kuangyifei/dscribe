<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\BlockType.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>java.lang.annotation.*</import>
    <import>org.exist.fluent.*</import>
    <interface fullName="com.ideanest.dscribe.mixt.BlockType" implName="com.ideanest.dscribe.mixt.BlockType" line="7" modifiers="public" name="BlockType" xml:id="j1-27">
        <method line="9" modifiers="" name="xmlName" xml:id="j1-28">
            <returns>
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </returns>
        </method>
        <method line="11" modifiers="" name="version" xml:id="j1-29">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="13" modifiers="" name="define" xml:id="j1-30">
            <returns>
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </returns>
            <param name="def">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <annotationinterface fullName="com.ideanest.dscribe.mixt.BlockType.AllowAttributes" implName="com.ideanest.dscribe.mixt.BlockType$AllowAttributes" line="15" modifiers="public" name="AllowAttributes" xml:id="j1-31">
            <method line="17" modifiers="" name="value" xml:id="j1-32">
                <returns>
                    <localType arrayDim="1">String</localType>
                    <type arrayDim="1">java.lang.String</type>
                </returns>
            </method>
        </annotationinterface>
    </interface>
</unit>