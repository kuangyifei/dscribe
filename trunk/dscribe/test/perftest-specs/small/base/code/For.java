<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\blocks\For.java</file>
    <packageref>com.ideanest.dscribe.mixt.blocks</packageref>
    <import>com.ideanest.dscribe.mixt.test.Matchers.collection</import>
    <import>org.hamcrest.Matchers.is</import>
    <import>org.junit.Assert.*</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.exist.fluent.QueryService.QueryAnalysis</import>
    <import>org.junit.Test</import>
    <import>com.ideanest.dscribe.mixt.*</import>
    <import>com.ideanest.dscribe.mixt.test.BlockTestCase</import>
    <class fullName="com.ideanest.dscribe.mixt.blocks.For" implName="com.ideanest.dscribe.mixt.blocks.For" line="16" modifiers="public" name="For" xml:id="j1-803">
        <implements>
            <localType>BlockType</localType>
            <type>com.ideanest.dscribe.mixt.BlockType</type>
        </implements>
        <method line="18" modifiers="public" name="xmlName" xml:id="j1-804">
            <returns>
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </returns>
        </method>
        <method line="22" modifiers="public" name="version" xml:id="j1-805">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="26" modifiers="public" name="define" xml:id="j1-806">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.For.ForEachBlock" implName="com.ideanest.dscribe.mixt.blocks.For$ForEachBlock" line="37" modifiers="static private" name="ForEachBlock" xml:id="j1-807">
            <extends>
                <localType>ForBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>KeyBlock</localType>
                <type>com.ideanest.dscribe.mixt.KeyBlock</type>
            </implements>
            <constructor line="38" modifiers="" xml:id="j1-808">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="41" modifiers="public" name="resolve" xml:id="j1-809">
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
            <method line="48" modifiers="public" name="createSeg" xml:id="j1-810">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.For.ForOneBlock" implName="com.ideanest.dscribe.mixt.blocks.For$ForOneBlock" line="53" modifiers="static private" name="ForOneBlock" xml:id="j1-811">
            <extends>
                <localType>ForBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>LinearBlock</localType>
                <type>com.ideanest.dscribe.mixt.LinearBlock</type>
            </implements>
            <constructor line="54" modifiers="" xml:id="j1-812">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="57" modifiers="public" name="resolve" xml:id="j1-813">
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
            <method line="65" modifiers="public" name="createSeg" xml:id="j1-814">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.For.ForAllBlock" implName="com.ideanest.dscribe.mixt.blocks.For$ForAllBlock" line="70" modifiers="static private" name="ForAllBlock" xml:id="j1-815">
            <extends>
                <localType>ForBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>LinearBlock</localType>
                <type>com.ideanest.dscribe.mixt.LinearBlock</type>
            </implements>
            <field line="71" modifiers="final static private" name="XML_ID_COMPARATOR" xml:id="j1-816">
                <localType>Comparator</localType>
                <type>java.util.Comparator</type>
            </field>
            <constructor line="77" modifiers="" xml:id="j1-817">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="82" modifiers="static private" name="nodesToCanonicalArray" xml:id="j1-818">
                <returns>
                    <localType arrayDim="1">Node</localType>
                    <type arrayDim="1">org.exist.fluent.Node</type>
                </returns>
                <param name="items">
                    <localType>ItemList</localType>
                    <type>org.exist.fluent.ItemList</type>
                </param>
            </method>
            <method line="88" modifiers="public" name="resolve" xml:id="j1-819">
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
            <method line="95" modifiers="public" name="createSeg" xml:id="j1-820">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.For.ForAllBlock.ForAllSeg" implName="com.ideanest.dscribe.mixt.blocks.For$ForAllBlock$ForAllSeg" line="99" modifiers="private" name="ForAllSeg" xml:id="j1-821">
                <extends>
                    <localType>ForSeg</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <constructor line="100" modifiers="" xml:id="j1-822">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="102" modifiers="public" name="restore" xml:id="j1-823">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="106" modifiers="public" name="verify" xml:id="j1-824">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.For.ForBlock" implName="com.ideanest.dscribe.mixt.blocks.For$ForBlock" line="114" modifiers="abstract static private" name="ForBlock" xml:id="j1-825">
            <implements>
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </implements>
            <field line="115" modifiers="final private" name="variableName" xml:id="j1-826">
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </field>
            <field line="116" modifiers="final" name="query" xml:id="j1-827">
                <localType>Query.Items</localType>
                <type>com.ideanest.dscribe.mixt.Query.Items</type>
            </field>
            <field line="117" modifiers="final" name="target" xml:id="j1-828">
                <type>boolean</type>
            </field>
            <field line="118" modifiers="" name="requiredVariables" xml:id="j1-829">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </field>
            <constructor line="120" modifiers="" xml:id="j1-830">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <param name="varAttrName">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <class fullName="com.ideanest.dscribe.mixt.blocks.For.ForBlock.ForSeg" implName="com.ideanest.dscribe.mixt.blocks.For$ForBlock$ForSeg" line="134" modifiers="" name="ForSeg" xml:id="j1-831">
                <extends>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </extends>
                <constructor line="135" modifiers="" xml:id="j1-832">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="137" modifiers="" name="bindVariable" xml:id="j1-833">
                    <returns>
                        <type>void</type>
                    </returns>
                    <param name="value">
                        <localType>Resource</localType>
                        <type>org.exist.fluent.Resource</type>
                    </param>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="141" modifiers="public" name="analyze" xml:id="j1-834">
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
            <class fullName="com.ideanest.dscribe.mixt.blocks.For.ForBlock.ForEachSeg" implName="com.ideanest.dscribe.mixt.blocks.For$ForBlock$ForEachSeg" line="150" modifiers="" name="ForEachSeg" xml:id="j1-835">
                <extends>
                    <localType>ForSeg</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <implements>
                    <localType>InsertionTarget</localType>
                    <type>com.ideanest.dscribe.mixt.blocks.InsertionTarget</type>
                </implements>
                <implements>
                    <localType>NodeTarget</localType>
                    <type>com.ideanest.dscribe.mixt.blocks.NodeTarget</type>
                </implements>
                <constructor line="151" modifiers="" xml:id="j1-836">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="153" modifiers="public" name="restore" xml:id="j1-837">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="157" modifiers="public" name="verify" xml:id="j1-838">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="164" modifiers="public" name="insert" xml:id="j1-839">
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
                <method line="169" modifiers="public" name="canInsertMultiple" xml:id="j1-840">
                    <returns>
                        <type>boolean</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="173" modifiers="public" name="targets" xml:id="j1-841">
                    <returns>
                        <localType>ItemList</localType>
                        <type>org.exist.fluent.ItemList</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
            </class>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.For._Test" implName="com.ideanest.dscribe.mixt.blocks.For$_Test" line="180" modifiers="static public" name="_Test" xml:id="j1-842">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="183" modifiers="public" name="parseNoVariableAttribute" xml:id="j1-843">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="188" modifiers="public" name="parseMultipleVariableAttributes" xml:id="j1-844">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="193" modifiers="public" name="parseForEachBlock" xml:id="j1-845">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="201" modifiers="public" name="parseForOneBlock" xml:id="j1-846">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="209" modifiers="public" name="parseForAllBlock" xml:id="j1-847">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="217" modifiers="public" name="parseTargetKeyword" xml:id="j1-848">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="225" modifiers="public" name="parseForAllTargetKeywordFails" xml:id="j1-849">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="230" modifiers="public" name="parseBadKeyword" xml:id="j1-850">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="235" modifiers="public" name="resolveOneFailsOnMultipleResults" xml:id="j1-851">
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
            <method line="242" modifiers="public" name="resolveOneDoesNothingOnNoResult" xml:id="j1-852">
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
            <method line="249" modifiers="public" name="resolveOneWorks" xml:id="j1-853">
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
            <method line="261" modifiers="public" name="resolveEachWorks" xml:id="j1-854">
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
            <method line="275" modifiers="public" name="resolveAllDoesNothingOnNoResult" xml:id="j1-855">
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
            <method line="282" modifiers="public" name="analyzeWorksWithVariable" xml:id="j1-856">
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
            <method line="292" modifiers="public" name="analyzeWorksWithKeyword" xml:id="j1-857">
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
            <method line="301" modifiers="public" name="restoreWorksWithVariable" xml:id="j1-858">
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
            <method line="310" modifiers="public" name="restoreWorksWithKeyword" xml:id="j1-859">
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
            <method line="317" modifiers="public" name="restoreForAllWorks" xml:id="j1-860">
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
            <method line="327" modifiers="public" name="verifyWorks" xml:id="j1-861">
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
            <method line="336" modifiers="public" name="verifyFails" xml:id="j1-862">
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
            <method line="345" modifiers="public" name="verifyForAllWorks" xml:id="j1-863">
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
            <method line="354" modifiers="public" name="verifyForAllFails" xml:id="j1-864">
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
            <method line="363" modifiers="public" name="insertWithTarget" xml:id="j1-865">
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
            <method line="374" modifiers="public" name="insertNotTarget" xml:id="j1-866">
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
            <method line="393" modifiers="public" name="targetNodesWithTarget" xml:id="j1-867">
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
            <method line="401" modifiers="public" name="targetNodesNotTarget" xml:id="j1-868">
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