<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\KeyMod.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.*</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.jmock.Expectations</import>
    <import>org.junit.*</import>
    <class fullName="com.ideanest.dscribe.mixt.KeyMod" implName="com.ideanest.dscribe.mixt.KeyMod" line="13" modifiers="public" name="KeyMod" xml:id="j1-232">
        <extends>
            <localType>Mod</localType>
            <type>com.ideanest.dscribe.mixt.Mod</type>
        </extends>
        <field line="15" modifiers="final private" name="variableBindings" xml:id="j1-233">
            <localType>Map</localType>
            <type>java.util.Map</type>
        </field>
        <field line="16" modifiers="private" name="key" xml:id="j1-234">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <constructor line="18" modifiers="" xml:id="j1-235">
            <param name="rule">
                <localType>Rule</localType>
                <type>com.ideanest.dscribe.mixt.Rule</type>
            </param>
        </constructor>
        <constructor line="24" modifiers="" xml:id="j1-236">
            <param name="parent">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </param>
            <param name="key">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </constructor>
        <method line="31" modifiers="" name="variableBindings" xml:id="j1-237">
            <returns>
                <localType>Map</localType>
                <type>java.util.Map</type>
            </returns>
        </method>
        <method line="35" modifiers="public" name="key" xml:id="j1-238">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="39" modifiers="public" name="toString" xml:id="j1-239">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.KeyMod.Builder" implName="com.ideanest.dscribe.mixt.KeyMod$Builder" line="43" modifiers="static public" name="Builder" xml:id="j1-240">
            <extends>
                <localType>Mod.Builder</localType>
                <type>com.ideanest.dscribe.mixt.Mod.Builder</type>
            </extends>
            <field line="44" modifiers="" name="keySuffix" xml:id="j1-241">
                <localType>String</localType>
                <type>java.lang.String</type>
            </field>
            <constructor line="46" modifiers="" xml:id="j1-242">
                <param name="parent">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
                <param name="block">
                    <localType>Block</localType>
                    <type>com.ideanest.dscribe.mixt.Block</type>
                </param>
                <param name="lastBlock">
                    <type>boolean</type>
                </param>
                <param name="scope">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
            </constructor>
            <method line="50" modifiers="public" name="referenceKey" xml:id="j1-243">
                <returns>
                    <type>void</type>
                </returns>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
            </method>
            <method line="56" modifiers="public" name="setKey" xml:id="j1-244">
                <returns>
                    <type>void</type>
                </returns>
                <param name="rawKey">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="61" modifiers="private" name="sanitizeKey" xml:id="j1-245">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
                <param name="rawKey">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="78" modifiers="" name="checkChildCount" xml:id="j1-246">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="82" modifiers="" name="key" xml:id="j1-247">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
            </method>
            <method line="87" modifiers="" name="reset" xml:id="j1-248">
                <returns>
                    <type>void</type>
                </returns>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.KeyMod._BuilderTest" implName="com.ideanest.dscribe.mixt.KeyMod$_BuilderTest" line="94" modifiers="static public" name="_BuilderTest" xml:id="j1-249">
            <extends>
                <localType>Mod._Test</localType>
                <type>com.ideanest.dscribe.mixt.Mod._Test</type>
            </extends>
            <field line="95" modifiers="private" name="builder" xml:id="j1-250">
                <localType>Builder</localType>
                <typeNotResolved>not found</typeNotResolved>
            </field>
            <method line="97" modifiers="public" name="setupBuilder" xml:id="j1-251">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>IllegalAccessException</localType>
                    <type>java.lang.IllegalAccessException</type>
                </throws>
            </method>
            <method line="101" modifiers="public" name="resetsParameterFields" xml:id="j1-252">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="106" modifiers="public" name="keyNoKeys" xml:id="j1-253">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="111" modifiers="public" name="keyRefKey" xml:id="j1-254">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="117" modifiers="public" name="referenceKey" xml:id="j1-255">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="122" modifiers="public" name="referenceKeyTwice" xml:id="j1-256">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="128" modifiers="public" name="sanitizeKeyWithoutSpecialChars" xml:id="j1-257">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="132" modifiers="public" name="sanitizeKeyWithSpecialChars" xml:id="j1-258">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="136" modifiers="public" name="sanitizeKeyWithAstralChars" xml:id="j1-259">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="140" modifiers="public" name="commitTwice" xml:id="j1-260">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.KeyMod._ModTest" implName="com.ideanest.dscribe.mixt.KeyMod$_ModTest" line="152" modifiers="static public" name="_ModTest" xml:id="j1-261">
            <extends>
                <localType>Mod._Test</localType>
                <type>com.ideanest.dscribe.mixt.Mod._Test</type>
            </extends>
            <method line="153" modifiers="public" name="constructorFromRule" xml:id="j1-262">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="162" modifiers="public" name="constructorFromParentMod" xml:id="j1-263">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="173" modifiers="public" name="constructorFromParentNullKey" xml:id="j1-264">
                <returns>
                    <type>void</type>
                </returns>
            </method>
        </class>
    </class>
</unit>