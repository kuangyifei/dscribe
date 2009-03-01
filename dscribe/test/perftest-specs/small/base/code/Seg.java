<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Seg.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.exist.fluent.QueryService</import>
    <class fullName="com.ideanest.dscribe.mixt.Seg" implName="com.ideanest.dscribe.mixt.Seg" line="6" modifiers="abstract public" name="Seg" xml:id="j1-672">
        <field line="8" modifiers="final public" name="mod" xml:id="j1-673">
            <localType>Mod</localType>
            <type>com.ideanest.dscribe.mixt.Mod</type>
        </field>
        <constructor line="10" modifiers="public" xml:id="j1-674">
            <param name="mod">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </param>
        </constructor>
        <method line="14" modifiers="public" name="analyze" xml:id="j1-675">
            <returns>
                <localType>QueryService.QueryAnalysis</localType>
                <type>org.exist.fluent.QueryService.QueryAnalysis</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="15" modifiers="public" name="restore" xml:id="j1-676">
            <returns>
                <type>void</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="16" modifiers="public" name="verify" xml:id="j1-677">
            <returns>
                <type>void</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
    </class>
</unit>