<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\test\Matchers.java</file>
    <packageref>com.ideanest.dscribe.mixt.test</packageref>
    <import>java.util.Collection</import>
    <import>org.hamcrest.*</import>
    <class fullName="com.ideanest.dscribe.mixt.test.Matchers" implName="com.ideanest.dscribe.mixt.test.Matchers" line="7" modifiers="public" name="Matchers" xml:id="j1-1107">
        <method line="9" modifiers="static public" name="collection" xml:id="j1-1108">
            <returns>
                <localType>Matcher</localType>
                <type>org.hamcrest.Matcher</type>
            </returns>
            <param name="contents">
                <localType>T</localType>
                <typeNotResolved>not found</typeNotResolved>
            </param>
        </method>
        <method line="13" modifiers="static public" name="emptyCollectionOf" xml:id="j1-1109">
            <returns>
                <localType>Matcher</localType>
                <type>org.hamcrest.Matcher</type>
            </returns>
            <param name="clazz">
                <localType>Class</localType>
                <type>java.lang.Class</type>
            </param>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.test.Matchers.CollectionMatcher" implName="com.ideanest.dscribe.mixt.test.Matchers$CollectionMatcher" line="17" modifiers="static public" name="CollectionMatcher" xml:id="j1-1110">
            <extends>
                <localType>BaseMatcher</localType>
                <type>org.hamcrest.BaseMatcher</type>
            </extends>
            <field line="18" modifiers="final private" name="contents" xml:id="j1-1111">
                <localType arrayDim="1">Object</localType>
                <type arrayDim="1">java.lang.Object</type>
            </field>
            <constructor line="19" modifiers="public" xml:id="j1-1112">
                <param name="contents">
                    <localType arrayDim="1">Object</localType>
                    <type arrayDim="1">java.lang.Object</type>
                </param>
            </constructor>
            <method line="22" modifiers="public" name="matches" xml:id="j1-1113">
                <returns>
                    <type>boolean</type>
                </returns>
                <param name="o">
                    <localType>Object</localType>
                    <type>java.lang.Object</type>
                </param>
            </method>
            <method line="29" modifiers="public" name="describeTo" xml:id="j1-1114">
                <returns>
                    <type>void</type>
                </returns>
                <param name="description">
                    <localType>Description</localType>
                    <type>org.hamcrest.Description</type>
                </param>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.test.Matchers.EmptyCollectionOfMatcher" implName="com.ideanest.dscribe.mixt.test.Matchers$EmptyCollectionOfMatcher" line="34" modifiers="static public" name="EmptyCollectionOfMatcher" xml:id="j1-1115">
            <extends>
                <localType>BaseMatcher</localType>
                <type>org.hamcrest.BaseMatcher</type>
            </extends>
            <field line="35" modifiers="final private" name="clazz" xml:id="j1-1116">
                <localType>Class</localType>
                <type>java.lang.Class</type>
            </field>
            <constructor line="36" modifiers="public" xml:id="j1-1117">
                <param name="clazz">
                    <localType>Class</localType>
                    <type>java.lang.Class</type>
                </param>
            </constructor>
            <method line="39" modifiers="public" name="matches" xml:id="j1-1118">
                <returns>
                    <type>boolean</type>
                </returns>
                <param name="o">
                    <localType>Object</localType>
                    <type>java.lang.Object</type>
                </param>
            </method>
            <method line="46" modifiers="public" name="describeTo" xml:id="j1-1119">
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