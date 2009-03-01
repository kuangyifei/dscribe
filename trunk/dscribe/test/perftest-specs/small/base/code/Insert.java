<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\blocks\Insert.java</file>
    <packageref>com.ideanest.dscribe.mixt.blocks</packageref>
    <import>com.ideanest.dscribe.mixt.test.Matchers.collection</import>
    <import>org.hamcrest.Matchers.is</import>
    <import>org.junit.Assert.*</import>
    <import>java.io.UnsupportedEncodingException</import>
    <import>java.security.*</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.exist.fluent.QueryService.QueryAnalysis</import>
    <import>org.jmock.Expectations</import>
    <import>org.junit.Test</import>
    <import>com.ideanest.dscribe.mixt.*</import>
    <import>com.ideanest.dscribe.mixt.test.BlockTestCase</import>
    <class fullName="com.ideanest.dscribe.mixt.blocks.Insert" implName="com.ideanest.dscribe.mixt.blocks.Insert" line="19" modifiers="public" name="Insert" xml:id="j1-869">
        <implements>
            <localType>BlockType</localType>
            <type>com.ideanest.dscribe.mixt.BlockType</type>
        </implements>
        <method line="21" modifiers="public" name="xmlName" xml:id="j1-870">
            <returns>
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </returns>
        </method>
        <method line="25" modifiers="public" name="version" xml:id="j1-871">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="29" modifiers="public" name="define" xml:id="j1-872">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.Insert.InsertBlock" implName="com.ideanest.dscribe.mixt.blocks.Insert$InsertBlock" line="34" modifiers="static private" name="InsertBlock" xml:id="j1-873">
            <implements>
                <localType>LinearBlock</localType>
                <type>com.ideanest.dscribe.mixt.LinearBlock</type>
            </implements>
            <implements>
                <localType>SortingBlock</localType>
                <type>com.ideanest.dscribe.mixt.SortingBlock</type>
            </implements>
            <field line="35" modifiers="final static private" name="DIGEST_TYPE" xml:id="j1-874">
                <localType>String</localType>
                <type>java.lang.String</type>
            </field>
            <field line="37" modifiers="final private" name="query" xml:id="j1-875">
                <localType>Query.Items</localType>
                <type>com.ideanest.dscribe.mixt.Query.Items</type>
            </field>
            <field line="38" modifiers="private" name="requiredVariables" xml:id="j1-876">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </field>
            <field line="39" modifiers="final private" name="inOrder" xml:id="j1-877">
                <type>boolean</type>
            </field>
            <field line="40" modifiers="final private" name="priority" xml:id="j1-878">
                <type>int</type>
            </field>
            <constructor line="42" modifiers="private" xml:id="j1-879">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="61" modifiers="public" name="resolve" xml:id="j1-880">
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
            <method line="96" modifiers="private" name="calculateDigest" xml:id="j1-881">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
                <param name="digestType">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="nodesToInsert">
                    <localType>ItemList</localType>
                    <type>org.exist.fluent.ItemList</type>
                </param>
                <throws>
                    <localType>NoSuchAlgorithmException</localType>
                    <type>java.security.NoSuchAlgorithmException</type>
                </throws>
            </method>
            <method line="106" modifiers="public" name="sort" xml:id="j1-882">
                <returns>
                    <type>void</type>
                </returns>
                <param name="segs">
                    <localType>Collection</localType>
                    <type>java.util.Collection</type>
                </param>
                <param name="graph">
                    <localType>OrderGraph</localType>
                    <type>com.ideanest.dscribe.mixt.OrderGraph</type>
                </param>
            </method>
            <method line="117" modifiers="public" name="createSeg" xml:id="j1-883">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.Insert.InsertBlock.InsertSeg" implName="com.ideanest.dscribe.mixt.blocks.Insert$InsertBlock$InsertSeg" line="121" modifiers="private" name="InsertSeg" xml:id="j1-884">
                <extends>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </extends>
                <implements>
                    <localType>NodeTarget</localType>
                    <type>com.ideanest.dscribe.mixt.blocks.NodeTarget</type>
                </implements>
                <field line="122" modifiers="private" name="digestType" xml:id="j1-885">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </field>
                <field line="123" modifiers="private" name="checksum" xml:id="j1-886">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </field>
                <field line="124" modifiers="private" name="inserted" xml:id="j1-887">
                    <localType>List</localType>
                    <type>java.util.List</type>
                </field>
                <constructor line="126" modifiers="" xml:id="j1-888">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="128" modifiers="public" name="analyze" xml:id="j1-889">
                    <returns>
                        <localType>QueryAnalysis</localType>
                        <typeNotResolved>not found</typeNotResolved>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="134" modifiers="public" name="restore" xml:id="j1-890">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="141" modifiers="public" name="verify" xml:id="j1-891">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="152" modifiers="public" name="targets" xml:id="j1-892">
                    <returns>
                        <localType>ItemList</localType>
                        <type>org.exist.fluent.ItemList</type>
                    </returns>
                </method>
            </class>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.Insert._Test" implName="com.ideanest.dscribe.mixt.blocks.Insert$_Test" line="158" modifiers="static public" name="_Test" xml:id="j1-893">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="159" modifiers="public" name="parse1" xml:id="j1-894">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="163" modifiers="public" name="parse2" xml:id="j1-895">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="167" modifiers="public" name="parse3" xml:id="j1-896">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="171" modifiers="public" name="parse4" xml:id="j1-897">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="176" modifiers="public" name="parse5" xml:id="j1-898">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="181" modifiers="public" name="parse6" xml:id="j1-899">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="186" modifiers="public" name="parse7" xml:id="j1-900">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="191" modifiers="public" name="calculateDigestMD5" xml:id="j1-901">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>NoSuchAlgorithmException</localType>
                    <type>java.security.NoSuchAlgorithmException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="202" modifiers="public" name="calculateDigestUnknownAlgorithm" xml:id="j1-902">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>NoSuchAlgorithmException</localType>
                    <type>java.security.NoSuchAlgorithmException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="208" modifiers="public" name="resolveNoNodes" xml:id="j1-903">
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
            <method line="217" modifiers="private" name="testResolve" xml:id="j1-904">
                <returns>
                    <type>void</type>
                </returns>
                <param name="rule">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="count">
                    <type>int</type>
                </param>
                <param name="inOrder">
                    <type>boolean</type>
                </param>
                <param name="multipleOK">
                    <type>boolean</type>
                </param>
                <param name="result">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="checksum">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="245" modifiers="public" name="resolveOneNode" xml:id="j1-905">
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
            <method line="252" modifiers="public" name="resolveOneOrderedNode" xml:id="j1-906">
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
            <method line="259" modifiers="public" name="resolveTwoNodes" xml:id="j1-907">
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
            <method line="266" modifiers="public" name="resolveTwoNodesNotAllowed" xml:id="j1-908">
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
            <method line="274" modifiers="public" name="resolveTwoOrderedNodes" xml:id="j1-909">
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
            <method line="281" modifiers="public" name="resolveComplexNodes" xml:id="j1-910">
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
            <method line="288" modifiers="public" name="sortOneSegNoPriority" xml:id="j1-911">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="301" modifiers="public" name="sortTwoSegsWithPriority" xml:id="j1-912">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="315" modifiers="public" name="analyze" xml:id="j1-913">
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
            <method line="323" modifiers="public" name="restore" xml:id="j1-914">
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
            <method line="334" modifiers="public" name="verifyWorks" xml:id="j1-915">
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
            <method line="343" modifiers="public" name="verifyFailsBadAlgorithm" xml:id="j1-916">
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
            <method line="353" modifiers="public" name="verifyFailsBadChecksum1" xml:id="j1-917">
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
            <method line="363" modifiers="public" name="verifyFailsBadChecksum2" xml:id="j1-918">
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