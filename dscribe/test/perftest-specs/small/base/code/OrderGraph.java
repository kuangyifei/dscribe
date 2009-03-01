<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\OrderGraph.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.assertEquals</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.junit.*</import>
    <class fullName="com.ideanest.dscribe.mixt.OrderGraph" implName="com.ideanest.dscribe.mixt.OrderGraph" line="10" modifiers="public" name="OrderGraph" xml:id="j1-444">
        <field line="11" modifiers="final private" name="target" xml:id="j1-445">
            <localType>Node</localType>
            <type>org.exist.fluent.Node</type>
        </field>
        <field line="12" modifiers="final private" name="numNodes" xml:id="j1-446">
            <type>int</type>
        </field>
        <field line="13" modifiers="final private" name="nodeIndex" xml:id="j1-447">
            <localType>Map</localType>
            <type>java.util.Map</type>
        </field>
        <field line="14" modifiers="final private" name="naturalNodeOrder" xml:id="j1-448">
            <type arrayDim="1">int</type>
        </field>
        <field line="15" modifiers="final private" name="edges" xml:id="j1-449">
            <type arrayDim="1">int</type>
        </field>
        <field line="16" modifiers="final private" name="maxes" xml:id="j1-450">
            <type arrayDim="1">int</type>
        </field>
        <field line="17" modifiers="final private" name="placed" xml:id="j1-451">
            <type arrayDim="1">boolean</type>
        </field>
        <constructor line="19" modifiers="" xml:id="j1-452">
            <param name="target">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </constructor>
        <method line="36" modifiers="public" name="order" xml:id="j1-453">
            <returns>
                <type>void</type>
            </returns>
            <param name="first">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="second">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="priority">
                <type>int</type>
            </param>
        </method>
        <method line="40" modifiers="public" name="order" xml:id="j1-454">
            <returns>
                <type>void</type>
            </returns>
            <param name="firstNodeId">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="secondNodeId">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="priority">
                <type>int</type>
            </param>
        </method>
        <method line="48" modifiers="" name="applyOrder" xml:id="j1-455">
            <returns>
                <type>int</type>
            </returns>
        </method>
        <method line="80" modifiers="private" name="minMax" xml:id="j1-456">
            <returns>
                <type>int</type>
            </returns>
        </method>
        <method line="86" modifiers="private" name="calculateMaxes" xml:id="j1-457">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="90" modifiers="private" name="calculateMaxIncomingPriority" xml:id="j1-458">
            <returns>
                <type>void</type>
            </returns>
            <param name="toIndex">
                <type>int</type>
            </param>
        </method>
        <method line="100" modifiers="private" name="edge" xml:id="j1-459">
            <returns>
                <type>int</type>
            </returns>
            <param name="fromIndex">
                <type>int</type>
            </param>
            <param name="toIndex">
                <type>int</type>
            </param>
        </method>
        <method line="104" modifiers="private" name="setEdge" xml:id="j1-460">
            <returns>
                <type>void</type>
            </returns>
            <param name="fromIndex">
                <type>int</type>
            </param>
            <param name="toIndex">
                <type>int</type>
            </param>
            <param name="priority">
                <type>int</type>
            </param>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.OrderGraph._Test" implName="com.ideanest.dscribe.mixt.OrderGraph$_Test" line="108" modifiers="static public" name="_Test" xml:id="j1-461">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="110" modifiers="private" name="target" xml:id="j1-462">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="111" modifiers="private" name="graph" xml:id="j1-463">
                <localType>OrderGraph</localType>
                <type>com.ideanest.dscribe.mixt.OrderGraph</type>
            </field>
            <method line="113" modifiers="public" name="setup" xml:id="j1-464">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="119" modifiers="private" name="assertOrder" xml:id="j1-465">
                <returns>
                    <type>void</type>
                </returns>
                <param name="numMoves">
                    <type>int</type>
                </param>
                <param name="expectedIds">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="130" modifiers="public" name="orderNodes1" xml:id="j1-466">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="136" modifiers="public" name="orderNodes2" xml:id="j1-467">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="143" modifiers="public" name="orderNodes3" xml:id="j1-468">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="150" modifiers="public" name="calculateMaxIncomingPriority1" xml:id="j1-469">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="157" modifiers="public" name="calculateMaxIncomingPriority2" xml:id="j1-470">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="165" modifiers="public" name="calculateMaxes1" xml:id="j1-471">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="177" modifiers="public" name="calculateMaxes2" xml:id="j1-472">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="190" modifiers="public" name="calculateMaxes3" xml:id="j1-473">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="203" modifiers="public" name="applyOrderEmptyDoesNothing" xml:id="j1-474">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="207" modifiers="public" name="applyOrderConsistentDoesNothing" xml:id="j1-475">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="213" modifiers="public" name="applyOrderSwitchOne" xml:id="j1-476">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="218" modifiers="public" name="applyOrderSwitchTwo" xml:id="j1-477">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="224" modifiers="public" name="applyOrderResolveConflict" xml:id="j1-478">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="230" modifiers="public" name="applyOrderBreakCycle" xml:id="j1-479">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="237" modifiers="public" name="applyOrderBreakTie" xml:id="j1-480">
                <returns>
                    <type>void</type>
                </returns>
            </method>
        </class>
    </class>
</unit>