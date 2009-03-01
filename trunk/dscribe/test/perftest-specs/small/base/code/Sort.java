<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\blocks\Sort.java</file>
    <packageref>com.ideanest.dscribe.mixt.blocks</packageref>
    <import>com.ideanest.dscribe.mixt.test.Matchers.collection</import>
    <import>org.hamcrest.Matchers.is</import>
    <import>org.junit.Assert.*</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.exist.fluent.QueryService.QueryAnalysis</import>
    <import>org.jmock.*</import>
    <import>org.junit.Test</import>
    <import>com.ideanest.dscribe.mixt.*</import>
    <import>com.ideanest.dscribe.mixt.Mod.Builder</import>
    <import>com.ideanest.dscribe.mixt.test.BlockTestCase</import>
    <class fullName="com.ideanest.dscribe.mixt.blocks.Sort" implName="com.ideanest.dscribe.mixt.blocks.Sort" line="17" modifiers="public" name="Sort" xml:id="j1-924">
        <implements>
            <localType>BlockType</localType>
            <type>com.ideanest.dscribe.mixt.BlockType</type>
        </implements>
        <method line="19" modifiers="public" name="version" xml:id="j1-925">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="23" modifiers="public" name="xmlName" xml:id="j1-926">
            <returns>
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </returns>
        </method>
        <method line="27" modifiers="public" name="define" xml:id="j1-927">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortBlock" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortBlock" line="37" modifiers="abstract static private" name="SortBlock" xml:id="j1-928">
            <implements>
                <localType>LinearBlock</localType>
                <type>com.ideanest.dscribe.mixt.LinearBlock</type>
            </implements>
            <field line="38" modifiers="final" name="priority" xml:id="j1-929">
                <type>int</type>
            </field>
            <field line="39" modifiers="final" name="query" xml:id="j1-930">
                <localType>Query.Items</localType>
                <type>com.ideanest.dscribe.mixt.Query.Items</type>
            </field>
            <field line="40" modifiers="private" name="requiredVariables" xml:id="j1-931">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </field>
            <constructor line="42" modifiers="" xml:id="j1-932">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="52" modifiers="public" name="resolve" xml:id="j1-933">
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
            <method line="62" modifiers="abstract" name="resolveOrder" xml:id="j1-934">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modBuilder">
                    <localType>Mod.Builder</localType>
                    <type>com.ideanest.dscribe.mixt.Mod.Builder</type>
                </param>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="64" modifiers="protected" name="totalOrder" xml:id="j1-935">
                <returns>
                    <type>void</type>
                </returns>
                <param name="items">
                    <localType>List</localType>
                    <type>java.util.List</type>
                </param>
                <param name="comparator">
                    <localType>Comparator</localType>
                    <type>java.util.Comparator</type>
                </param>
                <param name="graph">
                    <localType>OrderGraph</localType>
                    <type>com.ideanest.dscribe.mixt.OrderGraph</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortBlock.SortSeg" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortBlock$SortSeg" line="78" modifiers="private" name="SortSeg" xml:id="j1-936">
                <extends>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </extends>
                <constructor line="79" modifiers="" xml:id="j1-937">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="80" modifiers="public" name="analyze" xml:id="j1-938">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortByValueBlock" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortByValueBlock" line="88" modifiers="static private" name="SortByValueBlock" xml:id="j1-939">
            <extends>
                <localType>SortBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>SortingBlock</localType>
                <type>com.ideanest.dscribe.mixt.SortingBlock</type>
            </implements>
            <field line="89" modifiers="final private" name="ascending" xml:id="j1-940">
                <type>boolean</type>
            </field>
            <constructor line="91" modifiers="" xml:id="j1-941">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="100" modifiers="" name="resolveOrder" xml:id="j1-942">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modBuilder">
                    <localType>Mod.Builder</localType>
                    <type>com.ideanest.dscribe.mixt.Mod.Builder</type>
                </param>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <field line="109" modifiers="final static private" name="ASCENDING_VALUE_COMPARATOR" xml:id="j1-943">
                <localType>Comparator</localType>
                <type>java.util.Comparator</type>
            </field>
            <field line="115" modifiers="final static private" name="DESCENDING_VALUE_COMPARATOR" xml:id="j1-944">
                <localType>Comparator</localType>
                <type>java.util.Comparator</type>
            </field>
            <method line="121" modifiers="public" name="sort" xml:id="j1-945">
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
            <method line="127" modifiers="public" name="createSeg" xml:id="j1-946">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortByValueBlock.SortByValueSeg" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortByValueBlock$SortByValueSeg" line="131" modifiers="private" name="SortByValueSeg" xml:id="j1-947">
                <extends>
                    <localType>SortBlock.SortSeg</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <field line="132" modifiers="private" name="values" xml:id="j1-948">
                    <localType>List</localType>
                    <type>java.util.List</type>
                </field>
                <constructor line="133" modifiers="" xml:id="j1-949">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="135" modifiers="public" name="restore" xml:id="j1-950">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="145" modifiers="public" name="verify" xml:id="j1-951">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortByProxyBlock" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortByProxyBlock" line="156" modifiers="static private" name="SortByProxyBlock" xml:id="j1-952">
            <extends>
                <localType>SortBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>SortingBlock</localType>
                <type>com.ideanest.dscribe.mixt.SortingBlock</type>
            </implements>
            <constructor line="157" modifiers="" xml:id="j1-953">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="163" modifiers="" name="resolveOrder" xml:id="j1-954">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modBuilder">
                    <localType>Mod.Builder</localType>
                    <type>com.ideanest.dscribe.mixt.Mod.Builder</type>
                </param>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <field line="175" modifiers="final static private" name="NODE_ORDER_COMPARATOR" xml:id="j1-955">
                <localType>Comparator</localType>
                <type>java.util.Comparator</type>
            </field>
            <method line="181" modifiers="public" name="sort" xml:id="j1-956">
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
            <method line="199" modifiers="public" name="createSeg" xml:id="j1-957">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortByProxyBlock.SortByProxySeg" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortByProxyBlock$SortByProxySeg" line="203" modifiers="private" name="SortByProxySeg" xml:id="j1-958">
                <extends>
                    <localType>SortBlock.SortSeg</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <field line="204" modifiers="private" name="proxies" xml:id="j1-959">
                    <localType>List</localType>
                    <type>java.util.List</type>
                </field>
                <constructor line="205" modifiers="" xml:id="j1-960">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="207" modifiers="public" name="restore" xml:id="j1-961">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="216" modifiers="public" name="verify" xml:id="j1-962">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortBySiblingBlock" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortBySiblingBlock" line="236" modifiers="static private" name="SortBySiblingBlock" xml:id="j1-963">
            <extends>
                <localType>SortBlock</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <implements>
                <localType>SortingBlock</localType>
                <type>com.ideanest.dscribe.mixt.SortingBlock</type>
            </implements>
            <field line="237" modifiers="final private" name="before" xml:id="j1-964">
                <type>boolean</type>
            </field>
            <constructor line="238" modifiers="" xml:id="j1-965">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="246" modifiers="" name="resolveOrder" xml:id="j1-966">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modBuilder">
                    <localType>Mod.Builder</localType>
                    <type>com.ideanest.dscribe.mixt.Mod.Builder</type>
                </param>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="256" modifiers="public" name="sort" xml:id="j1-967">
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
            <method line="271" modifiers="public" name="createSeg" xml:id="j1-968">
                <returns>
                    <localType>Seg</localType>
                    <type>com.ideanest.dscribe.mixt.Seg</type>
                </returns>
                <param name="mod">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.blocks.Sort.SortBySiblingBlock.SortBySiblingSeg" implName="com.ideanest.dscribe.mixt.blocks.Sort$SortBySiblingBlock$SortBySiblingSeg" line="275" modifiers="private" name="SortBySiblingSeg" xml:id="j1-969">
                <extends>
                    <localType>SortBlock.SortSeg</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <field line="276" modifiers="private" name="siblingsByTarget" xml:id="j1-970">
                    <localType>List</localType>
                    <type>java.util.List</type>
                </field>
                <constructor line="277" modifiers="" xml:id="j1-971">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                </constructor>
                <method line="279" modifiers="public" name="restore" xml:id="j1-972">
                    <returns>
                        <type>void</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
                <method line="290" modifiers="public" name="verify" xml:id="j1-973">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort._SortBlockTest" implName="com.ideanest.dscribe.mixt.blocks.Sort$_SortBlockTest" line="307" modifiers="static public" name="_SortBlockTest" xml:id="j1-974">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="308" modifiers="public" name="parse1" xml:id="j1-975">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="313" modifiers="public" name="parse2" xml:id="j1-976">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="318" modifiers="public" name="parse3" xml:id="j1-977">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="323" modifiers="public" name="parse4" xml:id="j1-978">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="328" modifiers="public" name="totalOrder" xml:id="j1-979">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="351" modifiers="public" name="resolve" xml:id="j1-980">
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
            <method line="386" modifiers="public" name="analyze" xml:id="j1-981">
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
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort._SortByValueTest" implName="com.ideanest.dscribe.mixt.blocks.Sort$_SortByValueTest" line="395" modifiers="static public" name="_SortByValueTest" xml:id="j1-982">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="396" modifiers="public" name="parse1" xml:id="j1-983">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="402" modifiers="public" name="parse2" xml:id="j1-984">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="408" modifiers="public" name="parse3" xml:id="j1-985">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="413" modifiers="public" name="parse4" xml:id="j1-986">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="418" modifiers="public" name="resolveOrder" xml:id="j1-987">
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
            <method line="428" modifiers="public" name="resolveOrderBadQuery" xml:id="j1-988">
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
            <method line="436" modifiers="public" name="restore" xml:id="j1-989">
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
            <method line="453" modifiers="public" name="restoreBadQuery" xml:id="j1-990">
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
            <method line="466" modifiers="public" name="verify" xml:id="j1-991">
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
            <method line="476" modifiers="public" name="verifyBad" xml:id="j1-992">
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
            <method line="487" modifiers="public" name="sortAscending" xml:id="j1-993">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="502" modifiers="public" name="sortDescending" xml:id="j1-994">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort._SortByProxyTest" implName="com.ideanest.dscribe.mixt.blocks.Sort$_SortByProxyTest" line="518" modifiers="static public" name="_SortByProxyTest" xml:id="j1-995">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="519" modifiers="public" name="parse1" xml:id="j1-996">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="524" modifiers="public" name="parse2" xml:id="j1-997">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="529" modifiers="public" name="resolveOrder" xml:id="j1-998">
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
            <method line="541" modifiers="public" name="resolveOrderBadQuery" xml:id="j1-999">
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
            <method line="549" modifiers="public" name="restore" xml:id="j1-1000">
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
            <method line="567" modifiers="private" name="runVerifyScenario" xml:id="j1-1001">
                <returns>
                    <type>void</type>
                </returns>
                <param name="proxyid">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <param name="modData">
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
            <method line="585" modifiers="public" name="verify" xml:id="j1-1002">
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
            <method line="589" modifiers="public" name="verifyBadProxy" xml:id="j1-1003">
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
            <method line="594" modifiers="public" name="verifyBadPosition" xml:id="j1-1004">
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
            <method line="599" modifiers="public" name="sort" xml:id="j1-1005">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.blocks.Sort._SortBySiblingTest" implName="com.ideanest.dscribe.mixt.blocks.Sort$_SortBySiblingTest" line="618" modifiers="static public" name="_SortBySiblingTest" xml:id="j1-1006">
            <extends>
                <localType>BlockTestCase</localType>
                <type>com.ideanest.dscribe.mixt.test.BlockTestCase</type>
            </extends>
            <method line="619" modifiers="public" name="parse1" xml:id="j1-1007">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="624" modifiers="public" name="parse2" xml:id="j1-1008">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="629" modifiers="public" name="parse3" xml:id="j1-1009">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="634" modifiers="public" name="resolveOrder1" xml:id="j1-1010">
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
            <method line="645" modifiers="public" name="resolveOrder2" xml:id="j1-1011">
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
            <method line="657" modifiers="public" name="resolveOrderEmpty" xml:id="j1-1012">
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
            <method line="667" modifiers="public" name="resolveOrderNotSibling" xml:id="j1-1013">
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
            <method line="676" modifiers="public" name="restore" xml:id="j1-1014">
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
            <method line="697" modifiers="public" name="verify" xml:id="j1-1015">
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
            <method line="712" modifiers="public" name="verifyMismatch" xml:id="j1-1016">
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
            <method line="728" modifiers="public" name="verifyNotSibling" xml:id="j1-1017">
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
            <method line="742" modifiers="public" name="sortBefore" xml:id="j1-1018">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="765" modifiers="public" name="sortAfter" xml:id="j1-1019">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
        </class>
    </class>
</unit>