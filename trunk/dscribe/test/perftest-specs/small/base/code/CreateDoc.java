<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\blocks\CreateDoc.java</file>
    <packageref>com.ideanest.dscribe.mixt.blocks</packageref>
    <import>com.ideanest.dscribe.mixt.test.Matchers.collection</import>
    <import>org.hamcrest.Matchers.is</import>
    <import>org.junit.Assert.*</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.junit.Test</import>
    <import>com.ideanest.dscribe.mixt.*</import>
    <import>com.ideanest.dscribe.mixt.test.BlockTestCase</import>
    <class fullName="com.ideanest.dscribe.mixt.blocks.CreateDoc" implName="com.ideanest.dscribe.mixt.blocks.CreateDoc" line="15" modifiers="public" name="CreateDoc" xml:id="j1-767">
        <implements>
            <localType>BlockType</localType>
            <type>com.ideanest.dscribe.mixt.BlockType</type>
        </implements>
        <method line="17" modifiers="public" name="xmlName" xml:id="j1-768">
            <returns>
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </returns>
        </method>
        <method line="21" modifiers="public" name="version" xml:id="j1-769">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="25" modifiers="public" name="define" xml:id="j1-770">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.CreateDoc.CreateDocBlock" implName="com.ideanest.dscribe.mixt.blocks.CreateDoc$CreateDocBlock" line="29" modifiers="static private" name="CreateDocBlock" xml:id="j1-771">
            <implements>
                <localType>LinearBlock</localType>
                <type>com.ideanest.dscribe.mixt.LinearBlock</type>
            </implements>
            <field line="30" modifiers="final private" name="query" xml:id="j1-772">
                <localType>Query.Text</localType>
                <type>com.ideanest.dscribe.mixt.Query.Text</type>
            </field>
            <field line="31" modifiers="private" name="requiredVariables" xml:id="j1-773">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </field>
            <constructor line="33" modifiers="" xml:id="j1-774">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="37" modifiers="public" name="resolve" xml:id="j1-775">
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
            <method line="45" modifiers="private" name="resolveName" xml:id="j1-776">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
                <param name="keyMod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
                <param name="scope">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="54" modifiers="public" name="createSeg" xml:id="j1-777">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.CreateDoc.CreateDocBlock.CreateDocSeg" implName="com.ideanest.dscribe.mixt.blocks.CreateDoc$CreateDocBlock$CreateDocSeg" line="56" modifiers="private" name="CreateDocSeg" xml:id="j1-778">
                <extends>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </extends>
                <implements>
                    <localType>InsertionTarget</localType>
                    <type>com.ideanest.dscribe.mixt.blocks.InsertionTarget</type>
                </implements>
                <field line="57" modifiers="private" name="name" xml:id="j1-779">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </field>
                <constructor line="59" modifiers="" xml:id="j1-780">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="61" modifiers="public" name="restore" xml:id="j1-781">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="65" modifiers="public" name="analyze" xml:id="j1-782">
                    <returns>
                        <localType>QueryService.QueryAnalysis</localType>
                        <type>org.exist.fluent.QueryService.QueryAnalysis</type>
                    </returns>
                </method>
                <method line="71" modifiers="public" name="verify" xml:id="j1-783">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="77" modifiers="public" name="insert" xml:id="j1-784">
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
                <method line="90" modifiers="public" name="canInsertMultiple" xml:id="j1-785">
                    <returns>
                        <type>boolean</type>
                    </returns>
                </method>
            </class>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.CreateDoc._Test" implName="com.ideanest.dscribe.mixt.blocks.CreateDoc$_Test" line="97" modifiers="static public" name="_Test" xml:id="j1-786">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="98" modifiers="public" name="parseWithName" xml:id="j1-787">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="103" modifiers="public" name="parseNoName" xml:id="j1-788">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="108" modifiers="public" name="resolveNameConstant" xml:id="j1-789">
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
            <method line="113" modifiers="public" name="resolveNameVariable" xml:id="j1-790">
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
            <method line="118" modifiers="public" name="resolveNameNull" xml:id="j1-791">
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
            <method line="124" modifiers="public" name="resolveBadName1" xml:id="j1-792">
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
            <method line="130" modifiers="public" name="resolveBadName2" xml:id="j1-793">
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
            <method line="136" modifiers="public" name="resolveBadName3" xml:id="j1-794">
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
            <method line="142" modifiers="public" name="resolve" xml:id="j1-795">
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
            <method line="153" modifiers="public" name="restore" xml:id="j1-796">
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
            <method line="161" modifiers="public" name="analyze" xml:id="j1-797">
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
            <method line="168" modifiers="public" name="verifyWorks" xml:id="j1-798">
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
            <method line="176" modifiers="public" name="verifyFails" xml:id="j1-799">
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
            <method line="185" modifiers="public" name="insert1" xml:id="j1-800">
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
            <method line="195" modifiers="public" name="insert2" xml:id="j1-801">
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
            <method line="205" modifiers="public" name="insert3" xml:id="j1-802">
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