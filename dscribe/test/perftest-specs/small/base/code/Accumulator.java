<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Accumulator.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.assertEquals</import>
    <import>org.junit.Assert.assertTrue</import>
    <import>java.util.*</import>
    <import>org.junit.Before</import>
    <import>org.junit.Test</import>
    <class fullName="com.ideanest.dscribe.mixt.Accumulator" implName="com.ideanest.dscribe.mixt.Accumulator" line="11" modifiers="" name="Accumulator" xml:id="j1-1">
        <class fullName="com.ideanest.dscribe.mixt.Accumulator.Cell" implName="com.ideanest.dscribe.mixt.Accumulator$Cell" line="13" modifiers="static private" name="Cell" xml:id="j1-2">
            <field line="14" modifiers="private" name="contents" xml:id="j1-3">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </field>
            <field line="15" modifiers="private" name="next" xml:id="j1-4">
                <localType>Cell</localType>
                <type>com.ideanest.dscribe.mixt.Accumulator.Cell</type>
            </field>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Accumulator.Locator" implName="com.ideanest.dscribe.mixt.Accumulator$Locator" line="18" modifiers="static" name="Locator" xml:id="j1-5">
            <field line="19" modifiers="private" name="ref" xml:id="j1-6">
                <localType>Cell</localType>
                <typeNotResolved>not found</typeNotResolved>
            </field>
            <constructor line="20" modifiers="private" xml:id="j1-7">
                <param name="ref">
                    <localType>Cell</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </param>
            </constructor>
            <method line="21" modifiers="public" name="catchUp" xml:id="j1-8">
                <returns>
                    <localType>Set</localType>
                    <type>java.util.Set</type>
                </returns>
            </method>
        </class>
        <field line="40" modifiers="private" name="head" xml:id="j1-9">
            <localType>Cell</localType>
            <typeNotResolved>not found</typeNotResolved>
        </field>
        <constructor line="42" modifiers="public" xml:id="j1-10"/>
        <constructor line="46" modifiers="private" xml:id="j1-11">
            <param name="head">
                <localType>Cell</localType>
                <typeNotResolved>not found</typeNotResolved>
            </param>
        </constructor>
        <method line="50" modifiers="private" name="update" xml:id="j1-12">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="54" modifiers="public" name="add" xml:id="j1-13">
            <returns>
                <type>void</type>
            </returns>
            <param name="element">
                <localType>E</localType>
                <typeNotResolved>not found</typeNotResolved>
            </param>
        </method>
        <method line="59" modifiers="public" name="addAll" xml:id="j1-14">
            <returns>
                <type>void</type>
            </returns>
            <param name="elements">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </param>
        </method>
        <method line="64" modifiers="public" name="anchor" xml:id="j1-15">
            <returns>
                <localType>Locator</localType>
                <typeNotResolved>not found</typeNotResolved>
            </returns>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.Accumulator._Test" implName="com.ideanest.dscribe.mixt.Accumulator$_Test" line="73" modifiers="static public" name="_Test" xml:id="j1-16">
            <field line="74" modifiers="private" name="acc" xml:id="j1-17">
                <localType>Accumulator</localType>
                <type>com.ideanest.dscribe.mixt.Accumulator</type>
            </field>
            <method line="75" modifiers="public" name="setUp" xml:id="j1-18">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="78" modifiers="public" name="test1" xml:id="j1-19">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="84" modifiers="public" name="test2" xml:id="j1-20">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="89" modifiers="public" name="test3" xml:id="j1-21">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="95" modifiers="public" name="test4" xml:id="j1-22">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="103" modifiers="public" name="test5" xml:id="j1-23">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="112" modifiers="public" name="test6" xml:id="j1-24">
                <returns>
                    <type>void</type>
                </returns>
            </method>
        </class>
    </class>
</unit>