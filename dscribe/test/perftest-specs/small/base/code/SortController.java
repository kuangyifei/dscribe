<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\SortController.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.*</import>
    <import>java.lang.reflect.Field</import>
    <import>java.util.*</import>
    <import>org.apache.log4j.Logger</import>
    <import>org.exist.fluent.*</import>
    <import>org.jmock.*</import>
    <import>org.jmock.integration.junit4.*</import>
    <import>org.jmock.lib.legacy.ClassImposteriser</import>
    <import>org.junit.*</import>
    <import>org.junit.runner.RunWith</import>
    <class fullName="com.ideanest.dscribe.mixt.SortController" implName="com.ideanest.dscribe.mixt.SortController" line="17" modifiers="public" name="SortController" xml:id="j1-678">
        <field line="18" modifiers="final static private" name="LOG" xml:id="j1-679">
            <localType>Logger</localType>
            <type>org.apache.log4j.Logger</type>
        </field>
        <field line="19" modifiers="final private" name="engine" xml:id="j1-680">
            <localType>Engine</localType>
            <type>com.ideanest.dscribe.mixt.Engine</type>
        </field>
        <field line="20" modifiers="final private" name="modifiedDocsLocator" xml:id="j1-681">
            <localType>Accumulator.Locator</localType>
            <type>com.ideanest.dscribe.mixt.Accumulator.Locator</type>
        </field>
        <field line="21" modifiers="private" name="docsPending" xml:id="j1-682">
            <localType>Set</localType>
            <type>java.util.Set</type>
        </field>
        <field line="22" modifiers="private" name="nodesPending" xml:id="j1-683">
            <localType>Map</localType>
            <type>java.util.Map</type>
        </field>
        <field line="24" modifiers="private" name="self" xml:id="j1-684">
            <localType>Shim</localType>
            <typeNotResolved>not found</typeNotResolved>
        </field>
        <constructor line="26" modifiers="" xml:id="j1-685">
            <param name="engine">
                <localType>Engine</localType>
                <type>com.ideanest.dscribe.mixt.Engine</type>
            </param>
            <param name="modifiedDocsLocator">
                <localType>Accumulator.Locator</localType>
                <type>com.ideanest.dscribe.mixt.Accumulator.Locator</type>
            </param>
        </constructor>
        <interface fullName="com.ideanest.dscribe.mixt.SortController.Shim" implName="com.ideanest.dscribe.mixt.SortController$Shim" line="32" modifiers="private" name="Shim" xml:id="j1-686">
            <method line="33" modifiers="" name="sort" xml:id="j1-687">
                <returns>
                    <type>void</type>
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
            <method line="34" modifiers="" name="createGraph" xml:id="j1-688">
                <returns>
                    <localType>OrderGraph</localType>
                    <type>com.ideanest.dscribe.mixt.OrderGraph</type>
                </returns>
                <param name="target">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
            </method>
        </interface>
        <method line="37" modifiers="private" name="initDefaultShim" xml:id="j1-689">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="48" modifiers="" name="eventuallySort" xml:id="j1-690">
            <returns>
                <type>void</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="52" modifiers="" name="done" xml:id="j1-691">
            <returns>
                <type>boolean</type>
            </returns>
        </method>
        <method line="56" modifiers="" name="executeEndOfCycle" xml:id="j1-692">
            <returns>
                <type>void</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="66" modifiers="private" name="processNodes" xml:id="j1-693">
            <returns>
                <type>void</type>
            </returns>
            <param name="documentsChangedLastCycle">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="78" modifiers="private" name="processDocuments" xml:id="j1-694">
            <returns>
                <type>void</type>
            </returns>
            <param name="documentsChangedLastCycle">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="89" modifiers="private" name="recomputeDocsPending" xml:id="j1-695">
            <returns>
                <type>void</type>
            </returns>
            <param name="documentsChangedLastCycle">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </param>
        </method>
        <method line="98" modifiers="private" name="sort" xml:id="j1-696">
            <returns>
                <type>void</type>
            </returns>
            <param name="nodeId">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="102" modifiers="private" name="sort" xml:id="j1-697">
            <returns>
                <type>void</type>
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
        <class fullName="com.ideanest.dscribe.mixt.SortController._Test" implName="com.ideanest.dscribe.mixt.SortController$_Test" line="129" modifiers="static public" name="_Test" xml:id="j1-698">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="131" modifiers="final private" name="mockery" xml:id="j1-699">
                <localType>Mockery</localType>
                <type>org.jmock.Mockery</type>
            </field>
            <field line="134" modifiers="private" name="sortController" xml:id="j1-700">
                <localType>SortController</localType>
                <type>com.ideanest.dscribe.mixt.SortController</type>
            </field>
            <field line="135" modifiers="private" name="engine" xml:id="j1-701">
                <localType>Engine</localType>
                <type>com.ideanest.dscribe.mixt.Engine</type>
            </field>
            <field line="136" modifiers="private" name="locator" xml:id="j1-702">
                <localType>Accumulator.Locator</localType>
                <type>com.ideanest.dscribe.mixt.Accumulator.Locator</type>
            </field>
            <field line="137" modifiers="private" name="workspace" xml:id="j1-703">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <field line="138" modifiers="private" name="doc1" xml:id="j1-704">
                <localType>XMLDocument</localType>
                <type>org.exist.fluent.XMLDocument</type>
            </field>
            <field line="138" modifiers="private" name="doc2" xml:id="j1-705">
                <localType>XMLDocument</localType>
                <type>org.exist.fluent.XMLDocument</type>
            </field>
            <field line="138" modifiers="private" name="doc3" xml:id="j1-706">
                <localType>XMLDocument</localType>
                <type>org.exist.fluent.XMLDocument</type>
            </field>
            <field line="139" modifiers="private" name="modStore" xml:id="j1-707">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="139" modifiers="private" name="e1" xml:id="j1-708">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="139" modifiers="private" name="e2" xml:id="j1-709">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="139" modifiers="private" name="f1" xml:id="j1-710">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="139" modifiers="private" name="g1" xml:id="j1-711">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <method line="141" modifiers="public" name="setUp" xml:id="j1-712">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="178" modifiers="private" name="injectEngineCounter" xml:id="j1-713">
                <returns>
                    <localType>Counter</localType>
                    <type>com.ideanest.dscribe.mixt.Counter</type>
                </returns>
                <param name="fieldName">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="201" modifiers="public" name="done" xml:id="j1-714">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="207" modifiers="public" name="eventuallySort" xml:id="j1-715">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="213" modifiers="public" name="eventuallySortDocAlreadyPending" xml:id="j1-716">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="219" modifiers="public" name="sortDelegation" xml:id="j1-717">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="226" modifiers="public" name="executeEndOfCycleDocsOnly1" xml:id="j1-718">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="249" modifiers="public" name="executeEndOfCycleDocsOnly2" xml:id="j1-719">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="272" modifiers="public" name="executeEndOfCycleNodes" xml:id="j1-720">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="288" modifiers="public" name="sort" xml:id="j1-721">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="332" modifiers="public" name="sortNoMods" xml:id="j1-722">
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