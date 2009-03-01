<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\test\SystemTest.java</file>
    <packageref>com.ideanest.dscribe.mixt.test</packageref>
    <import>org.junit.Assert.*</import>
    <import>java.io.*</import>
    <import>java.text.*</import>
    <import>java.util.*</import>
    <import>java.util.regex.*</import>
    <import>org.apache.log4j.*</import>
    <import>org.custommonkey.xmlunit.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.junit.*</import>
    <import>org.junit.runner.RunWith</import>
    <import>org.w3c.dom.Element</import>
    <import>org.xml.sax.SAXException</import>
    <import>com.ideanest.dscribe.mixt.*</import>
    <import>com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs.Parameters</import>
    <class fullName="com.ideanest.dscribe.mixt.test.SystemTest" implName="com.ideanest.dscribe.mixt.test.SystemTest" line="21" modifiers="public" name="SystemTest" xml:id="j1-1139">
        <extends>
            <localType>DatabaseTestCase</localType>
            <type>org.exist.fluent.DatabaseTestCase</type>
        </extends>
        <field line="24" modifiers="final static private" name="TEST_SPEC_DIR" xml:id="j1-1140">
            <localType>File</localType>
            <type>java.io.File</type>
        </field>
        <method line="26" modifiers="static public" name="main" xml:id="j1-1141">
            <returns>
                <type>void</type>
            </returns>
            <param name="args">
                <localType arrayDim="1">String</localType>
                <type arrayDim="1">java.lang.String</type>
            </param>
            <throws>
                <localType>Exception</localType>
                <type>java.lang.Exception</type>
            </throws>
        </method>
        <method line="45" modifiers="static public" name="findSpecFiles" xml:id="j1-1142">
            <returns>
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </returns>
        </method>
        <method line="53" modifiers="static private" name="stripExtension" xml:id="j1-1143">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="filename">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <field line="58" modifiers="static private" name="previousLoggerLevel" xml:id="j1-1144">
            <localType>Level</localType>
            <type>org.apache.log4j.Level</type>
        </field>
        <field line="59" modifiers="static private" name="previousAppenderThreshold" xml:id="j1-1145">
            <localType>Priority</localType>
            <type>org.apache.log4j.Priority</type>
        </field>
        <method line="60" modifiers="static public" name="configureLogging" xml:id="j1-1146">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="68" modifiers="static public" name="unconfigureLogging" xml:id="j1-1147">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <field line="73" modifiers="final static private" name="LOG" xml:id="j1-1148">
            <localType>Logger</localType>
            <type>org.apache.log4j.Logger</type>
        </field>
        <field line="75" modifiers="final static private" name="STAGE_RE" xml:id="j1-1149">
            <localType>Pattern</localType>
            <type>java.util.regex.Pattern</type>
        </field>
        <field line="76" modifiers="final static private" name="INSTRUCTION_RE" xml:id="j1-1150">
            <localType>Pattern</localType>
            <type>java.util.regex.Pattern</type>
        </field>
        <field line="77" modifiers="final static private" name="STATS_RE" xml:id="j1-1151">
            <localType>Pattern</localType>
            <type>java.util.regex.Pattern</type>
        </field>
        <field line="79" modifiers="private" name="workspace" xml:id="j1-1152">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <field line="79" modifiers="private" name="rulespace" xml:id="j1-1153">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <field line="80" modifiers="private" name="transformer" xml:id="j1-1154">
            <localType>Transformer</localType>
            <type>com.ideanest.dscribe.mixt.Transformer</type>
        </field>
        <field line="81" modifiers="private" name="run" xml:id="j1-1155">
            <type>int</type>
        </field>
        <field line="82" modifiers="private" name="specFile" xml:id="j1-1156">
            <localType>File</localType>
            <type>java.io.File</type>
        </field>
        <field line="83" modifiers="private" name="stats" xml:id="j1-1157">
            <localType>Engine.Stats</localType>
            <type>com.ideanest.dscribe.mixt.Engine.Stats</type>
        </field>
        <constructor line="85" modifiers="public" xml:id="j1-1158">
            <param name="name">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="specFile">
                <localType>File</localType>
                <type>java.io.File</type>
            </param>
        </constructor>
        <method line="89" modifiers="public" name="setUp" xml:id="j1-1159">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="96" modifiers="public" name="run" xml:id="j1-1160">
            <returns>
                <type>void</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
            <throws>
                <localType>IllegalArgumentException</localType>
                <type>java.lang.IllegalArgumentException</type>
            </throws>
            <throws>
                <localType>SecurityException</localType>
                <type>java.lang.SecurityException</type>
            </throws>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
            <throws>
                <localType>SAXException</localType>
                <type>org.xml.sax.SAXException</type>
            </throws>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
            <throws>
                <localType>NoSuchFieldException</localType>
                <type>java.lang.NoSuchFieldException</type>
            </throws>
            <throws>
                <localType>ParseException</localType>
                <type>java.text.ParseException</type>
            </throws>
            <throws>
                <localType>InterruptedException</localType>
                <type>java.lang.InterruptedException</type>
            </throws>
            <throws>
                <localType>IllegalAccessException</localType>
                <type>java.lang.IllegalAccessException</type>
            </throws>
        </method>
        <method line="144" modifiers="private" name="doLoadRules" xml:id="j1-1161">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <param name="path">
                <localType>String</localType>
                <type>java.lang.String</type>
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
        <method line="163" modifiers="private" name="doReloadRules" xml:id="j1-1162">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
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
        <method line="178" modifiers="private" name="doLoadFile" xml:id="j1-1163">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <param name="line">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="path">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
        </method>
        <method line="186" modifiers="private" name="doModifyFile" xml:id="j1-1164">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <param name="line">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="path">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
        </method>
        <method line="194" modifiers="private" name="doLoadMods" xml:id="j1-1165">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
        </method>
        <method line="204" modifiers="private" name="doCheckFile" xml:id="j1-1166">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <param name="line">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="path">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
            <throws>
                <localType>SAXException</localType>
                <type>org.xml.sax.SAXException</type>
            </throws>
        </method>
        <method line="224" modifiers="private" name="listWorkspaceDocuments" xml:id="j1-1167">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="236" modifiers="private" name="doCheckMods" xml:id="j1-1168">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
            <throws>
                <localType>SAXException</localType>
                <type>org.xml.sax.SAXException</type>
            </throws>
        </method>
        <method line="247" modifiers="private" name="assertXMLMatches" xml:id="j1-1169">
            <returns>
                <type>void</type>
            </returns>
            <param name="what">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="expected">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="actual">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
            <throws>
                <localType>SAXException</localType>
                <type>org.xml.sax.SAXException</type>
            </throws>
        </method>
        <method line="280" modifiers="private" name="doCheckStats" xml:id="j1-1170">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <throws>
                <localType>IllegalArgumentException</localType>
                <type>java.lang.IllegalArgumentException</type>
            </throws>
            <throws>
                <localType>SecurityException</localType>
                <type>java.lang.SecurityException</type>
            </throws>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
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
        <method line="295" modifiers="private" name="readUntilNextInstruction" xml:id="j1-1171">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="reader">
                <localType>BufferedReader</localType>
                <type>java.io.BufferedReader</type>
            </param>
            <param name="buf">
                <localType>StringBuilder</localType>
                <type>java.lang.StringBuilder</type>
            </param>
            <throws>
                <localType>IOException</localType>
                <type>java.io.IOException</type>
            </throws>
        </method>
    </class>
</unit>