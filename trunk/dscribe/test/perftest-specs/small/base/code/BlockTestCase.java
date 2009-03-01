<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\test\BlockTestCase.java</file>
    <packageref>com.ideanest.dscribe.mixt.test</packageref>
    <import>com.ideanest.dscribe.mixt.test.Matchers.emptyCollectionOf</import>
    <import>org.junit.Assert.*</import>
    <import>java.util.*</import>
    <import>org.exist.fluent.*</import>
    <import>org.hamcrest.*</import>
    <import>org.jmock.*</import>
    <import>org.jmock.api.Action</import>
    <import>org.jmock.integration.junit4.*</import>
    <import>org.jmock.lib.legacy.ClassImposteriser</import>
    <import>org.junit.Before</import>
    <import>org.junit.runner.RunWith</import>
    <import>com.ideanest.dscribe.mixt.*</import>
    <import>com.ideanest.dscribe.mixt.Mod.Builder.*</import>
    <class fullName="com.ideanest.dscribe.mixt.test.BlockTestCase" implName="com.ideanest.dscribe.mixt.test.BlockTestCase" line="20" modifiers="abstract public" name="BlockTestCase" xml:id="j1-1065">
        <extends>
            <localType>DatabaseTestCase</localType>
            <type>org.exist.fluent.DatabaseTestCase</type>
        </extends>
        <field line="23" modifiers="protected" name="content" xml:id="j1-1066">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <field line="24" modifiers="final protected" name="mockery" xml:id="j1-1067">
            <localType>Mockery</localType>
            <type>org.jmock.Mockery</type>
        </field>
        <field line="27" modifiers="final protected" name="mod" xml:id="j1-1068">
            <localType>KeyMod</localType>
            <type>com.ideanest.dscribe.mixt.KeyMod</type>
        </field>
        <field line="28" modifiers="protected" name="modBuilder" xml:id="j1-1069">
            <localType>Mod.Builder</localType>
            <type>com.ideanest.dscribe.mixt.Mod.Builder</type>
        </field>
        <field line="29" modifiers="protected" name="keyModBuilder" xml:id="j1-1070">
            <localType>KeyMod.Builder</localType>
            <type>com.ideanest.dscribe.mixt.KeyMod.Builder</type>
        </field>
        <field line="31" modifiers="protected" name="modBuilderPriors" xml:id="j1-1071">
            <localType>List</localType>
            <type>java.util.List</type>
        </field>
        <field line="32" modifiers="private" name="supplementBuilder" xml:id="j1-1072">
            <localType>ElementBuilder</localType>
            <type>org.exist.fluent.ElementBuilder</type>
        </field>
        <field line="33" modifiers="private" name="counter" xml:id="j1-1073">
            <type>int</type>
        </field>
        <method line="35" modifiers="public" name="prepareDatabase" xml:id="j1-1074">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="66" modifiers="public" name="define" xml:id="j1-1075">
            <returns>
                <localType>T</localType>
                <typeNotResolved>not found</typeNotResolved>
            </returns>
            <param name="xml">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <method line="90" modifiers="public" name="setModBuilderOpenScope" xml:id="j1-1076">
            <returns>
                <type>void</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="96" modifiers="public" name="setModBuilderClosedScope" xml:id="j1-1077">
            <returns>
                <type>void</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="102" modifiers="public" name="setModBuilderCustomScope" xml:id="j1-1078">
            <returns>
                <type>void</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="108" modifiers="public" name="setModBuilderParent" xml:id="j1-1079">
            <returns>
                <type>void</type>
            </returns>
            <param name="mod">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </param>
        </method>
        <method line="114" modifiers="public" name="setModKey" xml:id="j1-1080">
            <returns>
                <type>void</type>
            </returns>
            <param name="key">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="120" modifiers="public" name="setModData" xml:id="j1-1081">
            <returns>
                <type>void</type>
            </returns>
            <param name="xml">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="128" modifiers="public" name="setModScope" xml:id="j1-1082">
            <returns>
                <type>void</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="141" modifiers="public" name="setModGlobalScope" xml:id="j1-1083">
            <returns>
                <type>void</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="147" modifiers="public" name="setModWorkspace" xml:id="j1-1084">
            <returns>
                <type>void</type>
            </returns>
            <param name="workspace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
        </method>
        <method line="153" modifiers="public" name="setModReferences" xml:id="j1-1085">
            <returns>
                <type>void</type>
            </returns>
            <param name="nodes">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="159" modifiers="public" name="setModAffectedIds" xml:id="j1-1086">
            <returns>
                <type>void</type>
            </returns>
            <param name="ids">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="165" modifiers="public" name="setModNearestAncestorImplementing" xml:id="j1-1087">
            <returns>
                <type>void</type>
            </returns>
            <param name="clazz">
                <localType>Class</localType>
                <type>java.lang.Class</type>
            </param>
            <param name="implementor">
                <localType>T</localType>
                <typeNotResolved>not found</typeNotResolved>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="171" modifiers="public" name="dependOnNearest" xml:id="j1-1088">
            <returns>
                <type>void</type>
            </returns>
            <param name="clazz">
                <localType>Class</localType>
                <type>java.lang.Class</type>
            </param>
            <param name="verified">
                <type>boolean</type>
            </param>
            <param name="implementor">
                <localType>T</localType>
                <typeNotResolved>not found</typeNotResolved>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="184" modifiers="public" name="reference" xml:id="j1-1089">
            <returns>
                <type>void</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="192" modifiers="public" name="referenceKey" xml:id="j1-1090">
            <returns>
                <type>void</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="200" modifiers="public" name="setKey" xml:id="j1-1091">
            <returns>
                <type>void</type>
            </returns>
            <param name="key">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="208" modifiers="public" name="dependOnDocument" xml:id="j1-1092">
            <returns>
                <type>void</type>
            </returns>
            <param name="doc">
                <localType>XMLDocument</localType>
                <type>org.exist.fluent.XMLDocument</type>
            </param>
        </method>
        <method line="217" modifiers="public" name="dependOnVariables" xml:id="j1-1093">
            <returns>
                <type>void</type>
            </returns>
            <param name="varNames">
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="221" modifiers="public" name="dependOnUnverifiedVariables" xml:id="j1-1094">
            <returns>
                <type>void</type>
            </returns>
            <param name="varNames">
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="225" modifiers="private" name="internalDependOnVariables" xml:id="j1-1095">
            <returns>
                <type>void</type>
            </returns>
            <param name="unverified">
                <type>boolean</type>
            </param>
            <param name="varNames">
                <localType arrayDim="1">QName</localType>
                <type arrayDim="1">org.exist.fluent.QName</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="240" modifiers="public" name="supplement" xml:id="j1-1096">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="251" modifiers="public" name="checkSupplement" xml:id="j1-1097">
            <returns>
                <type>void</type>
            </returns>
            <param name="expected">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="262" modifiers="public" name="generateIdsAndAffect" xml:id="j1-1098">
            <returns>
                <type>void</type>
            </returns>
            <param name="base">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="count">
                <type>int</type>
            </param>
            <param name="inOrder">
                <type>boolean</type>
            </param>
        </method>
        <method line="280" modifiers="public" name="order" xml:id="j1-1099">
            <returns>
                <type>void</type>
            </returns>
            <param name="nodeId">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="288" modifiers="public" name="thenCommit" xml:id="j1-1100">
            <returns>
                <type>void</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="295" modifiers="public" name="bindVariable" xml:id="j1-1101">
            <returns>
                <type>void</type>
            </returns>
            <param name="name">
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </param>
            <param name="value">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.test.BlockTestCase.NodeIdMatcher" implName="com.ideanest.dscribe.mixt.test.BlockTestCase$NodeIdMatcher" line="301" modifiers="static private" name="NodeIdMatcher" xml:id="j1-1102">
            <extends>
                <localType>BaseMatcher</localType>
                <type>org.hamcrest.BaseMatcher</type>
            </extends>
            <field line="302" modifiers="final private" name="id" xml:id="j1-1103">
                <localType>String</localType>
                <type>java.lang.String</type>
            </field>
            <constructor line="303" modifiers="public" xml:id="j1-1104">
                <param name="id">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </constructor>
            <method line="304" modifiers="public" name="matches" xml:id="j1-1105">
                <returns>
                    <type>boolean</type>
                </returns>
                <param name="item">
                    <localType>Object</localType>
                    <type>java.lang.Object</type>
                </param>
            </method>
            <method line="307" modifiers="public" name="describeTo" xml:id="j1-1106">
                <returns>
                    <type>void</type>
                </returns>
                <param name="description">
                    <localType>Description</localType>
                    <type>org.hamcrest.Description</type>
                </param>
            </method>
        </class>
    </class>
</unit>