<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\blocks\With.java</file>
    <packageref>com.ideanest.dscribe.mixt.blocks</packageref>
    <import>org.junit.Assert.*</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.exist.fluent.QueryService.QueryAnalysis</import>
    <import>org.junit.Test</import>
    <import>com.ideanest.dscribe.mixt.*</import>
    <import>com.ideanest.dscribe.mixt.test.BlockTestCase</import>
    <class fullName="com.ideanest.dscribe.mixt.blocks.With" implName="com.ideanest.dscribe.mixt.blocks.With" line="14" modifiers="public" name="With" xml:id="j1-1020">
        <implements>
            <localType>BlockType</localType>
            <type>com.ideanest.dscribe.mixt.BlockType</type>
        </implements>
        <method line="16" modifiers="public" name="xmlName" xml:id="j1-1021">
            <returns>
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </returns>
        </method>
        <method line="20" modifiers="public" name="version" xml:id="j1-1022">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="24" modifiers="public" name="define" xml:id="j1-1023">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.With.WithBlock" implName="com.ideanest.dscribe.mixt.blocks.With$WithBlock" line="33" modifiers="abstract static private" name="WithBlock" xml:id="j1-1024">
            <implements>
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </implements>
            <field line="34" modifiers="final" name="variableName" xml:id="j1-1025">
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </field>
            <field line="35" modifiers="final" name="query" xml:id="j1-1026">
                <localType>Query.Items</localType>
                <type>com.ideanest.dscribe.mixt.Query.Items</type>
            </field>
            <field line="36" modifiers="" name="requiredVariables" xml:id="j1-1027">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </field>
            <constructor line="38" modifiers="" xml:id="j1-1028">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <class fullName="com.ideanest.dscribe.mixt.blocks.With.WithBlock.WithSeg" implName="com.ideanest.dscribe.mixt.blocks.With$WithBlock$WithSeg" line="47" modifiers="private" name="WithSeg" xml:id="j1-1029">
                <extends>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </extends>
                <constructor line="48" modifiers="" xml:id="j1-1030">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="50" modifiers="public" name="analyze" xml:id="j1-1031">
                    <returns>
                        <localType>QueryAnalysis</localType>
                        <typeNotResolved>not found</typeNotResolved>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
            </class>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.With.WithAnySomeBlock" implName="com.ideanest.dscribe.mixt.blocks.With$WithAnySomeBlock" line="59" modifiers="static private" name="WithAnySomeBlock" xml:id="j1-1032">
            <extends>
                <localType>WithBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>LinearBlock</localType>
                <type>com.ideanest.dscribe.mixt.LinearBlock</type>
            </implements>
            <field line="60" modifiers="final private" name="optional" xml:id="j1-1033">
                <type>boolean</type>
            </field>
            <constructor line="62" modifiers="private" xml:id="j1-1034">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="67" modifiers="public" name="resolve" xml:id="j1-1035">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modBuilder">
                    <localType>Mod.Builder</localType>
                    <type>com.ideanest.dscribe.mixt.Mod.Builder</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="74" modifiers="public" name="createSeg" xml:id="j1-1036">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.With.WithAnySomeBlock.WithAnySomeSeg" implName="com.ideanest.dscribe.mixt.blocks.With$WithAnySomeBlock$WithAnySomeSeg" line="76" modifiers="private" name="WithAnySomeSeg" xml:id="j1-1037">
                <extends>
                    <localType>WithBlock.WithSeg</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <constructor line="77" modifiers="" xml:id="j1-1038">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="79" modifiers="public" name="restore" xml:id="j1-1039">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
            </class>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.With.WithDistinctBlock" implName="com.ideanest.dscribe.mixt.blocks.With$WithDistinctBlock" line="88" modifiers="static private" name="WithDistinctBlock" xml:id="j1-1040">
            <extends>
                <localType>WithBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>KeyBlock</localType>
                <type>com.ideanest.dscribe.mixt.KeyBlock</type>
            </implements>
            <constructor line="89" modifiers="private" xml:id="j1-1041">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="93" modifiers="public" name="resolve" xml:id="j1-1042">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modBuilder">
                    <localType>KeyMod.Builder</localType>
                    <type>com.ideanest.dscribe.mixt.KeyMod.Builder</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="106" modifiers="public" name="createSeg" xml:id="j1-1043">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.With.WithDistinctBlock.WithDistinctSeg" implName="com.ideanest.dscribe.mixt.blocks.With$WithDistinctBlock$WithDistinctSeg" line="108" modifiers="private" name="WithDistinctSeg" xml:id="j1-1044">
                <extends>
                    <localType>WithBlock.WithSeg</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <field line="109" modifiers="private" name="value" xml:id="j1-1045">
                    <localType>Item</localType>
                    <type>org.exist.fluent.Item</type>
                </field>
                <constructor line="111" modifiers="" xml:id="j1-1046">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="113" modifiers="public" name="restore" xml:id="j1-1047">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="123" modifiers="public" name="verify" xml:id="j1-1048">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
            </class>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.With._Test" implName="com.ideanest.dscribe.mixt.blocks.With$_Test" line="137" modifiers="static public" name="_Test" xml:id="j1-1049">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="138" modifiers="public" name="parseNoVariableAttribute" xml:id="j1-1050">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="143" modifiers="public" name="parseMultipleVariableAttributes" xml:id="j1-1051">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="148" modifiers="public" name="parseAny" xml:id="j1-1052">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="155" modifiers="public" name="parseSome" xml:id="j1-1053">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="162" modifiers="public" name="parseDistinct" xml:id="j1-1054">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="168" modifiers="public" name="parseBadVariableName" xml:id="j1-1055">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="173" modifiers="public" name="resolveAny" xml:id="j1-1056">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="182" modifiers="public" name="resolveSome" xml:id="j1-1057">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="192" modifiers="public" name="resolveDistinct" xml:id="j1-1058">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="213" modifiers="public" name="analyze" xml:id="j1-1059">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="221" modifiers="public" name="restoreAny" xml:id="j1-1060">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="229" modifiers="public" name="restoreSomeMissing" xml:id="j1-1061">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="236" modifiers="public" name="restoreDistinct" xml:id="j1-1062">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="244" modifiers="public" name="verifyDistinctSuccess" xml:id="j1-1063">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="254" modifiers="public" name="verifyDistinctFailure" xml:id="j1-1064">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
        </class>
    </class>
</unit>