<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\CompactFormTranslator.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.fail</import>
    <import>java.io.*</import>
    <import>java.text.ParseException</import>
    <import>java.util.*</import>
    <import>java.util.regex.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.junit.Test</import>
    <import>com.ideanest.dscribe.Namespace</import>
    <class fullName="com.ideanest.dscribe.mixt.CompactFormTranslator" implName="com.ideanest.dscribe.mixt.CompactFormTranslator" line="15" modifiers="public" name="CompactFormTranslator" xml:id="j1-33">
        <field line="17" modifiers="final static private" name="INDENT_PATTERN" xml:id="j1-34">
            <localType>Pattern</localType>
            <type>java.util.regex.Pattern</type>
        </field>
        <field line="18" modifiers="final static private" name="RULE_PATTERN" xml:id="j1-35">
            <localType>Pattern</localType>
            <type>java.util.regex.Pattern</type>
        </field>
        <field line="19" modifiers="final static private" name="FUNCTION_PATTERN" xml:id="j1-36">
            <localType>Pattern</localType>
            <type>java.util.regex.Pattern</type>
        </field>
        <constructor line="21" modifiers="private" xml:id="j1-37"/>
        <method line="23" modifiers="static public" name="compactToXml" xml:id="j1-38">
            <returns>
                <localType>Source.XML</localType>
                <type>org.exist.fluent.Source.XML</type>
            </returns>
            <param name="compactFormTextReader">
                <localType>Reader</localType>
                <type>java.io.Reader</type>
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
        <method line="144" modifiers="static private" name="escapeXMLChars" xml:id="j1-39">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="raw">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.CompactFormTranslator._Test" implName="com.ideanest.dscribe.mixt.CompactFormTranslator$_Test" line="148" modifiers="static public" name="_Test" xml:id="j1-40">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="150" modifiers="final private" name="compactText" xml:id="j1-41">
                <localType>StringBuilder</localType>
                <type>java.lang.StringBuilder</type>
            </field>
            <field line="150" modifiers="final private" name="xml" xml:id="j1-42">
                <localType>StringBuilder</localType>
                <type>java.lang.StringBuilder</type>
            </field>
            <field line="151" modifiers="private" name="captureTarget" xml:id="j1-43">
                <localType>StringBuilder</localType>
                <type>java.lang.StringBuilder</type>
            </field>
            <method line="153" modifiers="private" name="captureInput" xml:id="j1-44">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="157" modifiers="private" name="captureOutput" xml:id="j1-45">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="161" modifiers="private" name="_" xml:id="j1-46">
                <returns>
                    <type>void</type>
                </returns>
                <param name="line">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="165" modifiers="private" name="translateAndCheck" xml:id="j1-47">
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
            <method line="175" modifiers="private" name="translateBadInput" xml:id="j1-48">
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
            <method line="181" modifiers="public" name="empty" xml:id="j1-49">
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
            <method line="187" modifiers="public" name="namespaceDeclarations" xml:id="j1-50">
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
            <method line="196" modifiers="public" name="namespaceDeclarationsAndRule" xml:id="j1-51">
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
            <method line="208" modifiers="public" name="functionDeclaration" xml:id="j1-52">
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
            <method line="218" modifiers="public" name="functionDeclarationQName" xml:id="j1-53">
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
            <method line="228" modifiers="public" name="functionDeclarationNoArgs" xml:id="j1-54">
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
            <method line="238" modifiers="public" name="functionDeclarationMultiLine" xml:id="j1-55">
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
            <method line="252" modifiers="public" name="oneRuleWithoutBlocks" xml:id="j1-56">
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
            <method line="262" modifiers="public" name="oneRuleWithoutId" xml:id="j1-57">
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
            <method line="272" modifiers="public" name="twoRulesWithoutBlocks1" xml:id="j1-58">
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
            <method line="284" modifiers="public" name="twoRulesWithoutBlocks2" xml:id="j1-59">
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
            <method line="298" modifiers="public" name="oneRuleWithEmptyBlocks" xml:id="j1-60">
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
            <method line="312" modifiers="public" name="blockWithAttributes" xml:id="j1-61">
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
            <method line="325" modifiers="public" name="blockWithInlineText" xml:id="j1-62">
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
            <method line="338" modifiers="public" name="blockWithInlineElements" xml:id="j1-63">
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
            <method line="351" modifiers="public" name="blockWithAttributesAndInlineText" xml:id="j1-64">
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
            <method line="364" modifiers="public" name="blockWithInlineTextBetweenOtherBlocks" xml:id="j1-65">
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
            <method line="381" modifiers="public" name="blockWithIndentedText" xml:id="j1-66">
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
            <method line="402" modifiers="public" name="nestedBlocks" xml:id="j1-67">
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
            <method line="422" modifiers="public" name="ruleWithAlias" xml:id="j1-68">
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
            <method line="435" modifiers="public" name="ruleWithTwoAliasesAndBlocks" xml:id="j1-69">
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
            <method line="453" modifiers="public" name="namespaceAfterPreamble" xml:id="j1-70">
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
            <method line="461" modifiers="public" name="badNamespace1" xml:id="j1-71">
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
            <method line="468" modifiers="public" name="badNamespace2" xml:id="j1-72">
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
            <method line="475" modifiers="public" name="badIndent1" xml:id="j1-73">
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
            <method line="484" modifiers="public" name="badIndent2" xml:id="j1-74">
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
            <method line="493" modifiers="public" name="badIndent3" xml:id="j1-75">
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
            <method line="503" modifiers="public" name="badIndent4" xml:id="j1-76">
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
            <method line="511" modifiers="public" name="ruleNotAtTopLevel" xml:id="j1-77">
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
            <method line="519" modifiers="public" name="badRuleDeclaration" xml:id="j1-78">
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
            <method line="526" modifiers="public" name="unpairedAttribute" xml:id="j1-79">
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
            <method line="534" modifiers="public" name="aliasOutermost" xml:id="j1-80">
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
            <method line="541" modifiers="public" name="aliasNestedWithoutRule" xml:id="j1-81">
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
            <method line="548" modifiers="public" name="aliasAfterBlocks" xml:id="j1-82">
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
            <method line="557" modifiers="public" name="functionNested" xml:id="j1-83">
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
            <method line="564" modifiers="public" name="functionWithoutText" xml:id="j1-84">
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
            <method line="571" modifiers="public" name="functionMalformed" xml:id="j1-85">
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
        </class>
    </class>
</unit>