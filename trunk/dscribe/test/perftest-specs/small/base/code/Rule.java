<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Rule.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.hamcrest.Matchers.*</import>
    <import>org.junit.Assert.*</import>
    <import>java.lang.reflect.*</import>
    <import>java.text.MessageFormat</import>
    <import>java.util.*</import>
    <import>org.apache.log4j.Logger</import>
    <import>org.exist.fluent.*</import>
    <import>org.exist.storage.DBBroker</import>
    <import>org.hamcrest.*</import>
    <import>org.jmock.*</import>
    <import>org.jmock.integration.junit4.*</import>
    <import>org.jmock.lib.legacy.ClassImposteriser</import>
    <import>org.junit.*</import>
    <import>org.junit.runner.RunWith</import>
    <import>com.ideanest.dscribe.mixt.BlockType.AllowAttributes</import>
    <import>com.ideanest.dscribe.mixt.blocks.*</import>
    <class fullName="com.ideanest.dscribe.mixt.Rule" implName="com.ideanest.dscribe.mixt.Rule" line="23" modifiers="public" name="Rule" xml:id="j1-516">
        <field line="25" modifiers="final static private" name="LOG" xml:id="j1-517">
            <localType>Logger</localType>
            <type>org.apache.log4j.Logger</type>
        </field>
        <field line="28" modifiers="final static private" name="BLOCK_CLASSES" xml:id="j1-518">
            <localType arrayDim="1">Class</localType>
            <type arrayDim="1">java.lang.Class</type>
        </field>
        <field line="33" modifiers="final static private" name="BLOCK_TYPE_DICTIONARY" xml:id="j1-519">
            <localType>Map</localType>
            <type>java.util.Map</type>
        </field>
        <method line="54" modifiers="static" name="writeBlockTypeVersions" xml:id="j1-520">
            <returns>
                <type>void</type>
            </returns>
            <comment>Write out the versions of all defined block types, in a format that can be used in a later run to
detect changed versions. The builder should be writing to (some descendant of) the resource that
will later be provided as the &lt;code&gt;prevrulespace&lt;/code&gt;.</comment>
            <tag name="param">builder an element builder on the desired target resource</tag>
            <param name="builder">
                <localType>ElementBuilder</localType>
                <type>org.exist.fluent.ElementBuilder</type>
            </param>
        </method>
        <method line="64" modifiers="static" name="verifyBlockTypeVersions" xml:id="j1-521">
            <returns>
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </returns>
            <param name="record">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
        </method>
        <field line="77" modifiers="final public" name="engine" xml:id="j1-522">
            <localType>Engine</localType>
            <type>com.ideanest.dscribe.mixt.Engine</type>
        </field>
        <field line="78" modifiers="final" name="id" xml:id="j1-523">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <field line="79" modifiers="final private" name="toString" xml:id="j1-524">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <field line="80" modifiers="final private" name="blocks" xml:id="j1-525">
            <localType>List</localType>
            <type>java.util.List</type>
        </field>
        <field line="81" modifiers="private" name="touched" xml:id="j1-526">
            <localType>Set</localType>
            <type>java.util.Set</type>
        </field>
        <field line="82" modifiers="final private" name="modifiedDocsLocator" xml:id="j1-527">
            <localType>Accumulator.Locator</localType>
            <type>com.ideanest.dscribe.mixt.Accumulator.Locator</type>
        </field>
        <field line="83" modifiers="private" name="firstDifferentStage" xml:id="j1-528">
            <type>int</type>
        </field>
        <field line="84" modifiers="final private" name="rootModNode" xml:id="j1-529">
            <localType>Node</localType>
            <type>org.exist.fluent.Node</type>
        </field>
        <field line="85" modifiers="final private" name="globalScope" xml:id="j1-530">
            <localType>QueryService</localType>
            <type>org.exist.fluent.QueryService</type>
        </field>
        <field line="87" modifiers="private" name="self" xml:id="j1-531">
            <localType>Shim</localType>
            <typeNotResolved>not found</typeNotResolved>
        </field>
        <constructor line="89" modifiers="" xml:id="j1-532">
            <param name="def">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="prevDef">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="engine">
                <localType>Engine</localType>
                <type>com.ideanest.dscribe.mixt.Engine</type>
            </param>
            <param name="modifiedDocsLocator">
                <localType>Accumulator.Locator</localType>
                <type>com.ideanest.dscribe.mixt.Accumulator.Locator</type>
            </param>
            <param name="module">
                <localType>Engine.Module</localType>
                <type>com.ideanest.dscribe.mixt.Engine.Module</type>
            </param>
            <param name="prevModule">
                <localType>Engine.Module</localType>
                <type>com.ideanest.dscribe.mixt.Engine.Module</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </constructor>
        <constructor line="119" modifiers="private" xml:id="j1-533">
            <param name="engine">
                <localType>Engine</localType>
                <type>com.ideanest.dscribe.mixt.Engine</type>
            </param>
            <param name="modifiedDocsLocator">
                <localType>Accumulator.Locator</localType>
                <type>com.ideanest.dscribe.mixt.Accumulator.Locator</type>
            </param>
        </constructor>
        <interface fullName="com.ideanest.dscribe.mixt.Rule.Shim" implName="com.ideanest.dscribe.mixt.Rule$Shim" line="129" modifiers="private" name="Shim" xml:id="j1-534">
            <method line="130" modifiers="" name="rootMod" xml:id="j1-535">
                <returns>
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </returns>
            </method>
            <method line="131" modifiers="" name="processStage" xml:id="j1-536">
                <returns>
                    <type>int</type>
                </returns>
                <param name="stage">
                    <type>int</type>
                </param>
                <param name="touchedScope">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="132" modifiers="" name="restoreMod" xml:id="j1-537">
                <returns>
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </returns>
                <param name="modNode">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <param name="prevMod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="133" modifiers="" name="verifyMods" xml:id="j1-538">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modifiedDocs">
                    <localType>Set</localType>
                    <type>java.util.Set</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="134" modifiers="" name="verifyModTree" xml:id="j1-539">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modsToVerify">
                    <localType>ItemList</localType>
                    <type>org.exist.fluent.ItemList</type>
                </param>
                <param name="modifiedDocsNames">
                    <localType>Collection</localType>
                    <type>java.util.Collection</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
        </interface>
        <method line="137" modifiers="private" name="initDefaultShim" xml:id="j1-540">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="157" modifiers="" name="globalScope" xml:id="j1-541">
            <returns>
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </returns>
        </method>
        <method line="175" modifiers="private" name="parseBlocks" xml:id="j1-542">
            <returns>
                <type>int</type>
            </returns>
            <comment>Parse all the blocks in the given rule definition, comparing it to a previous definition of the rule.
Fill the &lt;code&gt;blocks&lt;/code&gt; list with the blocks and analyze each one. Return the index of the
first different block -- but note that this doesn't take implementation changes into account!</comment>
            <tag name="param">def this rule's definition node, of which the block definitions are the children</tag>
            <tag name="param">prevDef the previous version of the rule's definition block; cannot be &lt;code&gt;null&lt;/code&gt;, but
can be an inexistent node</tag>
            <tag name="param">module the module holding the function definitions for this rule</tag>
            <tag name="param">prevModule the matching previous module or &lt;code&gt;null&lt;/code&gt;</tag>
            <tag name="return">the index of the first block whose definition differs from the previous version, or
&lt;code&gt;Integer.MAX_VALUE&lt;/code&gt; if both versions of the rule are exactly the same</tag>
            <tag name="throws">RuleBaseException if unable to instantiate a block from its definition at any point</tag>
            <param name="def">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="prevDef">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="module">
                <localType>Engine.Module</localType>
                <type>com.ideanest.dscribe.mixt.Engine.Module</type>
            </param>
            <param name="prevModule">
                <localType>Engine.Module</localType>
                <type>com.ideanest.dscribe.mixt.Engine.Module</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <method line="222" modifiers="private" name="equalBlocks" xml:id="j1-543">
            <returns>
                <type>boolean</type>
            </returns>
            <comment>Compare two block definitions for equality. To be equal, the two block definitions must be equal at the XML structural
and content level, and have the same in-scope namespaces assigned to the same prefixes. This last
is important since the namespace bindings will be used by XQuery expressions embedded in the content.</comment>
            <tag name="param">block1 one block's definition</tag>
            <tag name="param">block2 the other block's definition</tag>
            <tag name="return">&lt;code&gt;true&lt;/code&gt; if the two blocks are equal, &lt;code&gt;false&lt;/code&gt; otherwise</tag>
            <param name="block1">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="block2">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="237" modifiers="private" name="defineBlock" xml:id="j1-544">
            <returns>
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </returns>
            <comment>Instantiate the block defined by the given node and validate the implementation.</comment>
            <tag name="param">blockDef the block's definition</tag>
            <tag name="return">a new block</tag>
            <tag name="throws">RuleBaseException if the block type is unknown, or the block's implementation is invalid</tag>
            <param name="blockDef">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <method line="248" modifiers="private" name="validateBlockAttributes" xml:id="j1-545">
            <returns>
                <type>void</type>
            </returns>
            <param name="blockDef">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="blockType">
                <localType>BlockType</localType>
                <type>com.ideanest.dscribe.mixt.BlockType</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <method line="260" modifiers="private" name="validateAttributes" xml:id="j1-546">
            <returns>
                <type>void</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="allowedAttributes">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <method line="269" modifiers="" name="addTouched" xml:id="j1-547">
            <returns>
                <type>void</type>
            </returns>
            <param name="docs">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </param>
        </method>
        <method line="273" modifiers="" name="sortBlock" xml:id="j1-548">
            <returns>
                <type>void</type>
            </returns>
            <param name="stage">
                <type>int</type>
            </param>
            <param name="segs">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </param>
            <param name="graph">
                <localType>OrderGraph</localType>
                <type>com.ideanest.dscribe.mixt.OrderGraph</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="284" modifiers="" name="process" xml:id="j1-549">
            <returns>
                <type>void</type>
            </returns>
            <param name="doGlobalProcessing">
                <type>boolean</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="312" modifiers="private" name="processStage" xml:id="j1-550">
            <returns>
                <type>int</type>
            </returns>
            <param name="stage">
                <type>int</type>
            </param>
            <param name="touchedScope">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="343" modifiers="" name="restoreMod" xml:id="j1-551">
            <returns>
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="prevMod">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="367" modifiers="private" name="bootstrapMod" xml:id="j1-552">
            <returns>
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </returns>
        </method>
        <method line="384" modifiers="private" name="verifyMods" xml:id="j1-553">
            <returns>
                <type>void</type>
            </returns>
            <comment>Verify all mods that depend on documents that have been modified.
Mods form a tree, so if we were to verify in random order we would re-verify parts
of the tree more than once. Hence, we do a pre-order traversal of the connected
subset of the tree that contains all the mods that we need to verify. As soon as a
mod fails to verify, we can cut off that entire branch. If a mod passes, we recursively
verify its descendants, saving on the expense of restoring the node again.</comment>
            <tag name="param">modifiedDocs the set of modified documents</tag>
            <tag name="throws">TransformException if an internally inconsistent state was detected during the verification</tag>
            <param name="modifiedDocs">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="408" modifiers="private" name="convertDocsToNames" xml:id="j1-554">
            <returns>
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </returns>
            <comment>Convert a set of documents to a sequence of their relative paths.</comment>
            <tag name="param">docs the set of documents to convert</tag>
            <tag name="return">the paths of the given documents, relative to the engine's workspace</tag>
            <param name="docs">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </param>
        </method>
        <method line="414" modifiers="private" name="verifyModTree" xml:id="j1-555">
            <returns>
                <type>void</type>
            </returns>
            <param name="modsToVerify">
                <localType>ItemList</localType>
                <type>org.exist.fluent.ItemList</type>
            </param>
            <param name="modifiedDocsNames">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="452" modifiers="private" name="buildToString" xml:id="j1-556">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="primaryName">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="460" modifiers="public" name="toString" xml:id="j1-557">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.Rule._ConstructorTest" implName="com.ideanest.dscribe.mixt.Rule$_ConstructorTest" line="464" modifiers="static public" name="_ConstructorTest" xml:id="j1-558">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="466" modifiers="private" name="engine" xml:id="j1-559">
                <localType>Engine</localType>
                <type>com.ideanest.dscribe.mixt.Engine</type>
            </field>
            <field line="467" modifiers="private" name="modStore" xml:id="j1-560">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="468" modifiers="private" name="ruleDoc" xml:id="j1-561">
                <localType>XMLDocument</localType>
                <type>org.exist.fluent.XMLDocument</type>
            </field>
            <field line="469" modifiers="final protected" name="mockery" xml:id="j1-562">
                <localType>Mockery</localType>
                <type>org.jmock.Mockery</type>
            </field>
            <method line="473" modifiers="public" name="setUp" xml:id="j1-563">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="505" modifiers="private" name="makeRule" xml:id="j1-564">
                <returns>
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </returns>
                <param name="attributes">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="xml">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="510" modifiers="public" name="sameDefsWithName" xml:id="j1-565">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="519" modifiers="public" name="sameDefsWithoutName" xml:id="j1-566">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="527" modifiers="public" name="diffDefsStage0" xml:id="j1-567">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="537" modifiers="public" name="diffDefsStage2" xml:id="j1-568">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Rule._RuleTest" implName="com.ideanest.dscribe.mixt.Rule$_RuleTest" line="548" modifiers="abstract static public" name="_RuleTest" xml:id="j1-569">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="550" modifiers="protected" name="rule" xml:id="j1-570">
                <localType>Rule</localType>
                <type>com.ideanest.dscribe.mixt.Rule</type>
            </field>
            <field line="551" modifiers="protected" name="modStore" xml:id="j1-571">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="552" modifiers="final protected" name="mockery" xml:id="j1-572">
                <localType>Mockery</localType>
                <type>org.jmock.Mockery</type>
            </field>
            <method line="556" modifiers="public" name="setUpEngineAndCreateRule" xml:id="j1-573">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="568" modifiers="protected" name="initEmptyModStore" xml:id="j1-574">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="572" modifiers="protected" name="initLiteralModStore" xml:id="j1-575">
                <returns>
                    <type>void</type>
                </returns>
                <param name="xml">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="594" modifiers="protected" name="modNodeAt" xml:id="j1-576">
                <returns>
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </returns>
                <param name="stage">
                    <type>int</type>
                </param>
            </method>
            <method line="598" modifiers="protected" name="modNode" xml:id="j1-577">
                <returns>
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </returns>
                <param name="id">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="602" modifiers="protected" name="mockMod" xml:id="j1-578">
                <returns>
                    <localType>T</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <param name="clazz">
                    <localType>Class</localType>
                    <type>java.lang.Class</type>
                </param>
                <param name="id">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="stage">
                    <type>int</type>
                </param>
            </method>
            <method line="606" modifiers="protected" name="mockMod" xml:id="j1-579">
                <returns>
                    <localType>T</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <param name="clazz">
                    <localType>Class</localType>
                    <type>java.lang.Class</type>
                </param>
                <param name="id">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="stage">
                    <type>int</type>
                </param>
                <param name="parent">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <method line="638" modifiers="protected" name="injectEngineCounter" xml:id="j1-580">
                <returns>
                    <localType>Counter</localType>
                    <type>com.ideanest.dscribe.mixt.Counter</type>
                </returns>
                <param name="fieldName">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Rule._UtilityTest" implName="com.ideanest.dscribe.mixt.Rule$_UtilityTest" line="663" modifiers="static public" name="_UtilityTest" xml:id="j1-581">
            <extends>
                <localType>_RuleTest</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <method line="665" modifiers="private" name="makeBlock" xml:id="j1-582">
                <returns>
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </returns>
                <param name="xml">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="670" modifiers="public" name="defineBlockWorks" xml:id="j1-583">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="675" modifiers="public" name="defineBlockFailsOnUnknownBlockType" xml:id="j1-584">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="680" modifiers="public" name="defineBlockFailsOnIllegalAttribute" xml:id="j1-585">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="685" modifiers="public" name="defineBlockAcceptsNamespacedAttribute" xml:id="j1-586">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="689" modifiers="public" name="compareBlocksEqual" xml:id="j1-587">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="695" modifiers="public" name="compareBlocksDifferentElement" xml:id="j1-588">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="701" modifiers="public" name="compareBlocksDifferentAttribute" xml:id="j1-589">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="707" modifiers="public" name="compareBlocksDifferentContent" xml:id="j1-590">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="713" modifiers="public" name="compareBlocksDifferentNumNamespaces" xml:id="j1-591">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="719" modifiers="public" name="compareBlocksDifferentPrefixes" xml:id="j1-592">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="725" modifiers="public" name="compareBlocksDifferentNamespaces" xml:id="j1-593">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="731" modifiers="public" name="convertDocsToNames" xml:id="j1-594">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="745" modifiers="public" name="writeBlockTypeVersions" xml:id="j1-595">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="756" modifiers="public" name="verifyBlockTypeVersions" xml:id="j1-596">
                <returns>
                    <type>void</type>
                </returns>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Rule._ParseBlocksTest" implName="com.ideanest.dscribe.mixt.Rule$_ParseBlocksTest" line="770" modifiers="static public" name="_ParseBlocksTest" xml:id="j1-597">
            <extends>
                <localType>_RuleTest</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <field line="772" modifiers="final" name="module" xml:id="j1-598">
                <localType>Engine.Module</localType>
                <type>com.ideanest.dscribe.mixt.Engine.Module</type>
            </field>
            <field line="773" modifiers="final" name="prevModule" xml:id="j1-599">
                <localType>Engine.Module</localType>
                <type>com.ideanest.dscribe.mixt.Engine.Module</type>
            </field>
            <method line="775" modifiers="public" name="allowEmptyFunctionsModifiedCalls" xml:id="j1-600">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="782" modifiers="private" name="makeRule" xml:id="j1-601">
                <returns>
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </returns>
                <param name="xml">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="787" modifiers="public" name="overrideMockShim" xml:id="j1-602">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>SecurityException</localType>
                    <type>java.lang.SecurityException</type>
                </throws>
                <throws>
                    <localType>NoSuchFieldException</localType>
                    <type>java.lang.NoSuchFieldException</type>
                </throws>
                <throws>
                    <localType>IllegalAccessException</localType>
                    <type>java.lang.IllegalAccessException</type>
                </throws>
            </method>
            <method line="792" modifiers="public" name="badBlock" xml:id="j1-603">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="797" modifiers="public" name="noPrevDef1" xml:id="j1-604">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="806" modifiers="public" name="noPrevDef2" xml:id="j1-605">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="815" modifiers="public" name="prevUnknownBlock" xml:id="j1-606">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="824" modifiers="public" name="samePrevDef1" xml:id="j1-607">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="833" modifiers="public" name="samePrevDef2" xml:id="j1-608">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="842" modifiers="public" name="lastBlockDiff" xml:id="j1-609">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="851" modifiers="public" name="middleBlockDiff" xml:id="j1-610">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="860" modifiers="public" name="twoBlocksDiff" xml:id="j1-611">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="869" modifiers="public" name="prevDefLonger" xml:id="j1-612">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="878" modifiers="public" name="prevDefShorter" xml:id="j1-613">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="887" modifiers="public" name="noPrevDefModifiedFunction" xml:id="j1-614">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="896" modifiers="public" name="samePrevDefModifiedFunction" xml:id="j1-615">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="911" modifiers="public" name="samePrevDefUnmodifiedFunction" xml:id="j1-616">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="926" modifiers="public" name="middleBlockDiffLastBlockModifiedFunction" xml:id="j1-617">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Rule._ModTest" implName="com.ideanest.dscribe.mixt.Rule$_ModTest" line="938" modifiers="static public" name="_ModTest" xml:id="j1-618">
            <extends>
                <localType>_RuleTest</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <field line="940" modifiers="private" name="blocks" xml:id="j1-619">
                <localType>List</localType>
                <type>java.util.List</type>
            </field>
            <method line="942" modifiers="public" name="setUp" xml:id="j1-620">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="969" modifiers="private" name="mockModRestoreSequence" xml:id="j1-621">
                <returns>
                    <localType>List</localType>
                    <type>java.util.List</type>
                </returns>
                <param name="lastStage">
                    <type>int</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="987" modifiers="public" name="restoreMod1" xml:id="j1-622">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="994" modifiers="public" name="restoreMod2" xml:id="j1-623">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1001" modifiers="public" name="restoreMod3" xml:id="j1-624">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1019" modifiers="public" name="processStageAtRootStageNoKeyHasMod" xml:id="j1-625">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1027" modifiers="public" name="processStageAtRootStageIsKeyHasMod" xml:id="j1-626">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1040" modifiers="public" name="processStageAtRootStageNoKeyNoMod" xml:id="j1-627">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1052" modifiers="public" name="processStageAtRootStageIsKeyNoMod" xml:id="j1-628">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1066" modifiers="public" name="processStageAtPreKeyStage" xml:id="j1-629">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1079" modifiers="public" name="processStageAtPreLinearStageHasMod" xml:id="j1-630">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1087" modifiers="public" name="processStageAtPreLinearStageNoMod" xml:id="j1-631">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1101" modifiers="public" name="processStageUsesPrevMod" xml:id="j1-632">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Rule._VerifyModTreeTest" implName="com.ideanest.dscribe.mixt.Rule$_VerifyModTreeTest" line="1121" modifiers="static public" name="_VerifyModTreeTest" xml:id="j1-633">
            <extends>
                <localType>_RuleTest</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <field line="1123" modifiers="private" name="modifiedDocsNames" xml:id="j1-634">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </field>
            <field line="1124" modifiers="private" name="mods" xml:id="j1-635">
                <localType>Map</localType>
                <type>java.util.Map</type>
            </field>
            <field line="1125" modifiers="private" name="rootMod" xml:id="j1-636">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </field>
            <method line="1127" modifiers="public" name="setUp" xml:id="j1-637">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>SecurityException</localType>
                    <type>java.lang.SecurityException</type>
                </throws>
                <throws>
                    <localType>NoSuchFieldException</localType>
                    <type>java.lang.NoSuchFieldException</type>
                </throws>
                <throws>
                    <localType>IllegalAccessException</localType>
                    <type>java.lang.IllegalAccessException</type>
                </throws>
            </method>
            <method line="1133" modifiers="private" name="createMods" xml:id="j1-638">
                <returns>
                    <type>void</type>
                </returns>
                <param name="ids">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1137" modifiers="private" name="createMod" xml:id="j1-639">
                <returns>
                    <type>void</type>
                </returns>
                <param name="id">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1152" modifiers="private" name="expectRestoreAndVerify" xml:id="j1-640">
                <returns>
                    <type>void</type>
                </returns>
                <param name="nodeId">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="succeed">
                    <type>boolean</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1161" modifiers="private" name="expectRestore" xml:id="j1-641">
                <returns>
                    <type>void</type>
                </returns>
                <param name="nodeId">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="succeed">
                    <type>boolean</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1169" modifiers="private" name="modNodes" xml:id="j1-642">
                <returns>
                    <localType>ItemList</localType>
                    <type>org.exist.fluent.ItemList</type>
                </returns>
                <param name="ids">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="1175" modifiers="public" name="singleVerified" xml:id="j1-643">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1182" modifiers="public" name="singleVerifiedFailure" xml:id="j1-644">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1192" modifiers="public" name="linearChain" xml:id="j1-645">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1200" modifiers="public" name="deepLinearChain" xml:id="j1-646">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1209" modifiers="public" name="split" xml:id="j1-647">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1217" modifiers="public" name="multipleSplit" xml:id="j1-648">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1228" modifiers="public" name="skipOver" xml:id="j1-649">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1236" modifiers="public" name="commonUnchangedAncestor" xml:id="j1-650">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1245" modifiers="public" name="ignoresUnchangedBranches" xml:id="j1-651">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="1253" modifiers="public" name="restoreErrorWithdrawsRule" xml:id="j1-652">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Rule._ProcessTest" implName="com.ideanest.dscribe.mixt.Rule$_ProcessTest" line="1264" modifiers="static public" name="_ProcessTest" xml:id="j1-653">
            <extends>
                <localType>_RuleTest</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <field line="1266" modifiers="static private" name="queryServiceDocs" xml:id="j1-654">
                <localType>Field</localType>
                <type>java.lang.reflect.Field</type>
            </field>
            <field line="1267" modifiers="static private" name="queryServicePrepareContext" xml:id="j1-655">
                <localType>Method</localType>
                <type>java.lang.reflect.Method</type>
            </field>
            <method line="1269" modifiers="static public" name="setUpReflectionAccessors" xml:id="j1-656">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>SecurityException</localType>
                    <type>java.lang.SecurityException</type>
                </throws>
                <throws>
                    <localType>NoSuchMethodException</localType>
                    <type>java.lang.NoSuchMethodException</type>
                </throws>
                <throws>
                    <localType>NoSuchFieldException</localType>
                    <type>java.lang.NoSuchFieldException</type>
                </throws>
            </method>
            <field line="1276" modifiers="private" name="customScope" xml:id="j1-657">
                <localType>Matcher</localType>
                <type>org.hamcrest.Matcher</type>
            </field>
            <field line="1277" modifiers="private" name="seq" xml:id="j1-658">
                <localType>Sequence</localType>
                <type>org.jmock.Sequence</type>
            </field>
            <method line="1279" modifiers="public" name="setUp" xml:id="j1-659">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1291" modifiers="private" name="prepScenario" xml:id="j1-660">
                <returns>
                    <type>void</type>
                </returns>
                <param name="doGlobalProcessing">
                    <type>boolean</type>
                </param>
                <param name="verifySuccessful">
                    <type>boolean</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1307" modifiers="private" name="prepIteration" xml:id="j1-661">
                <returns>
                    <type>void</type>
                </returns>
                <param name="blockType">
                    <localType>Class</localType>
                    <type>java.lang.Class</type>
                </param>
                <param name="numModsToResolve">
                    <type>int</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1317" modifiers="private" name="processRuleExpectingCompleted" xml:id="j1-662">
                <returns>
                    <type>void</type>
                </returns>
                <param name="doGlobalProcessing">
                    <type>boolean</type>
                </param>
                <param name="expectedNumModsCompleted">
                    <type>int</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1322" modifiers="public" name="emptyRun" xml:id="j1-663">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1328" modifiers="public" name="simpleRun" xml:id="j1-664">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1336" modifiers="public" name="verifyFailed" xml:id="j1-665">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1344" modifiers="public" name="globalProcessing" xml:id="j1-666">
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
</unit>