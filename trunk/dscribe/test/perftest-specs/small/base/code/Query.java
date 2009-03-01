<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Query.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.assertEquals</import>
    <import>org.exist.fluent.*</import>
    <import>org.junit.Test</import>
    <class fullName="com.ideanest.dscribe.mixt.Query" implName="com.ideanest.dscribe.mixt.Query" line="9" modifiers="abstract public" name="Query" xml:id="j1-489">
        <field line="10" modifiers="final private" name="query" xml:id="j1-490">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <field line="11" modifiers="final private" name="namespaceMap" xml:id="j1-491">
            <localType>NamespaceMap</localType>
            <type>org.exist.fluent.NamespaceMap</type>
        </field>
        <constructor line="13" modifiers="private" xml:id="j1-492">
            <param name="def">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <param name="query">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </constructor>
        <method line="19" modifiers="public" name="parseQName" xml:id="j1-493">
            <returns>
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </returns>
            <param name="qname">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="23" modifiers="public" name="inScopeNamespaces" xml:id="j1-494">
            <returns>
                <localType>NamespaceMap</localType>
                <type>org.exist.fluent.NamespaceMap</type>
            </returns>
        </method>
        <method line="27" modifiers="public" name="contentsAsString" xml:id="j1-495">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="31" modifiers="final protected" name="prep" xml:id="j1-496">
            <returns>
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="36" modifiers="private" name="execute" xml:id="j1-497">
            <returns>
                <localType>ItemList</localType>
                <type>org.exist.fluent.ItemList</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="40" modifiers="private" name="exists" xml:id="j1-498">
            <returns>
                <type>boolean</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="44" modifiers="public" name="analyze" xml:id="j1-499">
            <returns>
                <localType>QueryService.QueryAnalysis</localType>
                <type>org.exist.fluent.QueryService.QueryAnalysis</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="48" modifiers="static private" name="serializeContents" xml:id="j1-500">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="54" modifiers="public" name="equals" xml:id="j1-501">
            <returns>
                <type>boolean</type>
            </returns>
            <param name="o">
                <localType>Object</localType>
                <type>java.lang.Object</type>
            </param>
        </method>
        <method line="60" modifiers="public" name="hashCode" xml:id="j1-502">
            <returns>
                <type>int</type>
            </returns>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.Query.Items" implName="com.ideanest.dscribe.mixt.Query$Items" line="64" modifiers="static public" name="Items" xml:id="j1-503">
            <extends>
                <localType>Query</localType>
                <type>com.ideanest.dscribe.mixt.Query</type>
            </extends>
            <constructor line="65" modifiers="public" xml:id="j1-504">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="69" modifiers="public" name="runOn" xml:id="j1-505">
                <returns>
                    <localType>ItemList</localType>
                    <type>org.exist.fluent.ItemList</type>
                </returns>
                <param name="qs">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
            </method>
            <method line="73" modifiers="public" name="runExists" xml:id="j1-506">
                <returns>
                    <type>boolean</type>
                </returns>
                <param name="qs">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Query.Text" implName="com.ideanest.dscribe.mixt.Query$Text" line="78" modifiers="static public" name="Text" xml:id="j1-507">
            <extends>
                <localType>Query</localType>
                <type>com.ideanest.dscribe.mixt.Query</type>
            </extends>
            <constructor line="79" modifiers="public" xml:id="j1-508">
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </constructor>
            <method line="83" modifiers="public" name="runOn" xml:id="j1-509">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
                <param name="qs">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Query._Test" implName="com.ideanest.dscribe.mixt.Query$_Test" line="88" modifiers="static public" name="_Test" xml:id="j1-510">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <method line="90" modifiers="public" name="serializeContents1" xml:id="j1-511">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="93" modifiers="public" name="serializeContents2" xml:id="j1-512">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="96" modifiers="public" name="serializeContents3" xml:id="j1-513">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="100" modifiers="public" name="defineItemsQuery" xml:id="j1-514">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="105" modifiers="public" name="defineItemsQueryWithNamespaces" xml:id="j1-515">
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