<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\test\ParameterizedShowingArgs.java</file>
    <packageref>com.ideanest.dscribe.mixt.test</packageref>
    <import>java.lang.annotation.*</import>
    <import>java.lang.reflect.*</import>
    <import>java.util.*</import>
    <import>org.junit.Assert</import>
    <import>org.junit.internal.runners.*</import>
    <import>org.junit.runner.Description</import>
    <import>org.junit.runner.notification.RunNotifier</import>
    <class fullName="com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs" implName="com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs" line="12" modifiers="public" name="ParameterizedShowingArgs" xml:id="j1-1120">
        <extends>
            <localType>CompositeRunner</localType>
            <type>org.junit.internal.runners.CompositeRunner</type>
        </extends>
        <class fullName="com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs.TestClassRunnerForParameters" implName="com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs$TestClassRunnerForParameters" line="13" modifiers="static" name="TestClassRunnerForParameters" xml:id="j1-1121">
            <extends>
                <localType>JUnit4ClassRunner</localType>
                <type>org.junit.internal.runners.JUnit4ClassRunner</type>
            </extends>
            <field line="14" modifiers="final private" name="parameters" xml:id="j1-1122">
                <localType arrayDim="1">Object</localType>
                <type arrayDim="1">java.lang.Object</type>
            </field>
            <field line="15" modifiers="final private" name="constructor" xml:id="j1-1123">
                <localType>Constructor</localType>
                <type>java.lang.reflect.Constructor</type>
            </field>
            <field line="16" modifiers="final private" name="runMethod" xml:id="j1-1124">
                <localType>Method</localType>
                <type>java.lang.reflect.Method</type>
            </field>
            <constructor line="18" modifiers="" xml:id="j1-1125">
                <param name="testClass">
                    <localType>TestClass</localType>
                    <type>org.junit.internal.runners.TestClass</type>
                </param>
                <param name="runMethod">
                    <localType>Method</localType>
                    <type>java.lang.reflect.Method</type>
                </param>
                <param name="parameters">
                    <localType arrayDim="1">Object</localType>
                    <type arrayDim="1">java.lang.Object</type>
                </param>
                <throws>
                    <localType>InitializationError</localType>
                    <type>org.junit.internal.runners.InitializationError</type>
                </throws>
            </constructor>
            <method line="25" modifiers="protected" name="createTest" xml:id="j1-1126">
                <returns>
                    <localType>Object</localType>
                    <type>java.lang.Object</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="29" modifiers="protected" name="getName" xml:id="j1-1127">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
            </method>
            <method line="33" modifiers="protected" name="testName" xml:id="j1-1128">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
                <param name="method">
                    <localType>Method</localType>
                    <type>java.lang.reflect.Method</type>
                </param>
            </method>
            <method line="37" modifiers="public" name="getDescription" xml:id="j1-1129">
                <returns>
                    <localType>Description</localType>
                    <type>org.junit.runner.Description</type>
                </returns>
            </method>
            <method line="41" modifiers="private" name="getOnlyConstructor" xml:id="j1-1130">
                <returns>
                    <localType>Constructor</localType>
                    <type>java.lang.reflect.Constructor</type>
                </returns>
            </method>
            <method line="47" modifiers="protected" name="validate" xml:id="j1-1131">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>InitializationError</localType>
                    <type>org.junit.internal.runners.InitializationError</type>
                </throws>
            </method>
            <method line="51" modifiers="public" name="run" xml:id="j1-1132">
                <returns>
                    <type>void</type>
                </returns>
                <param name="notifier">
                    <localType>RunNotifier</localType>
                    <type>org.junit.runner.notification.RunNotifier</type>
                </param>
            </method>
        </class>
        <annotationinterface fullName="com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs.Parameters" implName="com.ideanest.dscribe.mixt.test.ParameterizedShowingArgs$Parameters" line="56" modifiers="static public" name="Parameters" xml:id="j1-1133"/>
        <field line="61" modifiers="final private" name="testClass" xml:id="j1-1134">
            <localType>TestClass</localType>
            <type>org.junit.internal.runners.TestClass</type>
        </field>
        <constructor line="63" modifiers="public" xml:id="j1-1135">
            <param name="klass">
                <localType>Class</localType>
                <type>java.lang.Class</type>
            </param>
            <throws>
                <localType>Exception</localType>
                <type>java.lang.Exception</type>
            </throws>
        </constructor>
        <method line="83" modifiers="public" name="run" xml:id="j1-1136">
            <returns>
                <type>void</type>
            </returns>
            <param name="notifier">
                <localType>RunNotifier</localType>
                <type>org.junit.runner.notification.RunNotifier</type>
            </param>
        </method>
        <method line="91" modifiers="private" name="getParametersList" xml:id="j1-1137">
            <returns>
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </returns>
            <throws>
                <localType>Exception</localType>
                <type>java.lang.Exception</type>
            </throws>
            <throws>
                <localType>InvocationTargetException</localType>
                <type>java.lang.reflect.InvocationTargetException</type>
            </throws>
            <throws>
                <localType>IllegalAccessException</localType>
                <type>java.lang.IllegalAccessException</type>
            </throws>
        </method>
        <method line="95" modifiers="private" name="getParametersMethod" xml:id="j1-1138">
            <returns>
                <localType>Method</localType>
                <type>java.lang.reflect.Method</type>
            </returns>
            <throws>
                <localType>Exception</localType>
                <type>java.lang.Exception</type>
            </throws>
        </method>
    </class>
</unit>