<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Counter.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>java.text.MessageFormat</import>
    <class fullName="com.ideanest.dscribe.mixt.Counter" implName="com.ideanest.dscribe.mixt.Counter" line="5" modifiers="public" name="Counter" xml:id="j1-86">
        <field line="6" modifiers="private" name="value" xml:id="j1-87">
            <type>long</type>
        </field>
        <field line="7" modifiers="final private" name="messageFormat" xml:id="j1-88">
            <localType>MessageFormat</localType>
            <type>java.text.MessageFormat</type>
        </field>
        <constructor line="9" modifiers="public" xml:id="j1-89">
            <param name="pattern">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </constructor>
        <method line="13" modifiers="public" name="value" xml:id="j1-90">
            <returns>
                <type>long</type>
            </returns>
        </method>
        <method line="15" modifiers="public" name="increment" xml:id="j1-91">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="16" modifiers="public" name="increment" xml:id="j1-92">
            <returns>
                <type>void</type>
            </returns>
            <param name="increment">
                <type>long</type>
            </param>
        </method>
        <method line="18" modifiers="public" name="toString" xml:id="j1-93">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="22" modifiers="public" name="format" xml:id="j1-94">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="count">
                <type>long</type>
            </param>
        </method>
        <method line="26" modifiers="static public" name="english" xml:id="j1-95">
            <returns>
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </returns>
            <param name="singular">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="plural">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="verb">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
    </class>
</unit>