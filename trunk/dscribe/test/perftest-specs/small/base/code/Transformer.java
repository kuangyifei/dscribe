<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Transformer.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.*</import>
    <import>java.io.*</import>
    <import>java.text.ParseException</import>
    <import>java.util.*</import>
    <import>org.apache.log4j.Logger</import>
    <import>org.exist.fluent.*</import>
    <import>org.junit.*</import>
    <class fullName="com.ideanest.dscribe.mixt.Transformer" implName="com.ideanest.dscribe.mixt.Transformer" line="13" modifiers="public" name="Transformer" xml:id="j1-735">
        <field line="14" modifiers="final static private" name="LOG" xml:id="j1-736">
            <localType>Logger</localType>
            <type>org.apache.log4j.Logger</type>
        </field>
        <field line="16" modifiers="static private" name="recordsRootPath" xml:id="j1-737">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <method line="18" modifiers="static public" name="recordsRootPath" xml:id="j1-738">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="19" modifiers="static public" name="setRecordsRootPath" xml:id="j1-739">
            <returns>
                <type>void</type>
            </returns>
            <param name="path">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <field line="21" modifiers="final private" name="workspace" xml:id="j1-740">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <field line="21" modifiers="final private" name="rulespace" xml:id="j1-741">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <field line="22" modifiers="private" name="recordspace" xml:id="j1-742">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <constructor line="24" modifiers="public" xml:id="j1-743">
            <param name="workspace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
            <param name="ruleRepository">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
        </constructor>
        <constructor line="34" modifiers="private" xml:id="j1-744">
            <param name="workspace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
            <param name="rulespace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
            <param name="recordspace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
        </constructor>
        <method line="40" modifiers="private" name="createRecordspace" xml:id="j1-745">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="47" modifiers="public" name="executeOnce" xml:id="j1-746">
            <returns>
                <localType>Engine.Stats</localType>
                <type>com.ideanest.dscribe.mixt.Engine.Stats</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
            <throws>
                <localType>InterruptedException</localType>
                <type>java.lang.InterruptedException</type>
            </throws>
        </method>
        <method line="65" modifiers="private" name="findXmlDocsModifiedSince" xml:id="j1-747">
            <returns>
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </returns>
            <param name="lastRunDate">
                <localType>Date</localType>
                <type>java.util.Date</type>
            </param>
        </method>
        <method line="73" modifiers="private" name="wipeRecords" xml:id="j1-748">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="80" modifiers="public" name="loadCompactRules" xml:id="j1-749">
            <returns>
                <type>void</type>
            </returns>
            <param name="ruleDefinitionsFile">
                <localType>File</localType>
                <type>java.io.File</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
            <throws>
                <localType>ParseException</localType>
                <type>java.text.ParseException</type>
            </throws>
        </method>
        <method line="89" modifiers="private" name="initModStore" xml:id="j1-750">
            <returns>
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </returns>
        </method>
        <method line="100" modifiers="private" name="recordRun" xml:id="j1-751">
            <returns>
                <type>void</type>
            </returns>
            <param name="lastRunDate">
                <localType>Date</localType>
                <type>java.util.Date</type>
            </param>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.Transformer._Test" implName="com.ideanest.dscribe.mixt.Transformer$_Test" line="116" modifiers="static public" name="_Test" xml:id="j1-752">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="118" modifiers="private" name="workspace" xml:id="j1-753">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <field line="118" modifiers="private" name="rulespace" xml:id="j1-754">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <field line="118" modifiers="private" name="recordspace" xml:id="j1-755">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <field line="119" modifiers="private" name="transformer" xml:id="j1-756">
                <localType>Transformer</localType>
                <type>com.ideanest.dscribe.mixt.Transformer</type>
            </field>
            <method line="121" modifiers="public" name="setupTransformer" xml:id="j1-757">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="132" modifiers="public" name="create" xml:id="j1-758">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="141" modifiers="public" name="createWithBadWorkspace" xml:id="j1-759">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="146" modifiers="public" name="loadCompactRules" xml:id="j1-760">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IOException</localType>
                    <type>java.io.IOException</type>
                </throws>
                <throws>
                    <localType>ParseException</localType>
                    <type>java.text.ParseException</type>
                </throws>
            </method>
            <method line="159" modifiers="public" name="createRecordspace" xml:id="j1-761">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="166" modifiers="public" name="initModStoreFresh" xml:id="j1-762">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="173" modifiers="public" name="initModStoreExisting" xml:id="j1-763">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="182" modifiers="public" name="recordRun" xml:id="j1-764">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="210" modifiers="public" name="findDocsModifiedSince" xml:id="j1-765">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>InterruptedException</localType>
                    <type>java.lang.InterruptedException</type>
                </throws>
            </method>
            <method line="222" modifiers="public" name="wipeRecords" xml:id="j1-766">
                <returns>
                    <type>void</type>
                </returns>
            </method>
        </class>
    </class>
</unit>