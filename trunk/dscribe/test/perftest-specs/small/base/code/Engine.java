<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Engine.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.junit.Assert.*</import>
    <import>java.lang.reflect.Field</import>
    <import>java.util.*</import>
    <import>org.apache.log4j.Logger</import>
    <import>org.exist.fluent.*</import>
    <import>org.hamcrest.*</import>
    <import>org.jmock.*</import>
    <import>org.jmock.api.*</import>
    <import>org.jmock.integration.junit4.*</import>
    <import>org.jmock.lib.legacy.ClassImposteriser</import>
    <import>org.junit.*</import>
    <import>org.junit.runner.RunWith</import>
    <import>com.ideanest.dscribe.mixt.blocks.*</import>
    <class fullName="com.ideanest.dscribe.mixt.Engine" implName="com.ideanest.dscribe.mixt.Engine" line="21" modifiers="public" name="Engine" xml:id="j1-96">
        <field line="22" modifiers="final static public" name="RECORD_NS" xml:id="j1-97">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <field line="23" modifiers="final static public" name="MOD_NS" xml:id="j1-98">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <field line="24" modifiers="final static public" name="MIXT_NS" xml:id="j1-99">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <field line="26" modifiers="final static" name="LOG" xml:id="j1-100">
            <localType>Logger</localType>
            <type>org.apache.log4j.Logger</type>
        </field>
        <field line="27" modifiers="final static private" name="VERSION" xml:id="j1-101">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <class fullName="com.ideanest.dscribe.mixt.Engine.Stats" implName="com.ideanest.dscribe.mixt.Engine$Stats" line="29" modifiers="static public" name="Stats" xml:id="j1-102">
            <field line="30" modifiers="final public" name="numCycles" xml:id="j1-103">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
            <field line="31" modifiers="final public" name="numBlocksVerified" xml:id="j1-104">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
            <field line="32" modifiers="final public" name="numBlocksResolved" xml:id="j1-105">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
            <field line="33" modifiers="final public" name="numModsRestored" xml:id="j1-106">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
            <field line="34" modifiers="final public" name="numModsCompleted" xml:id="j1-107">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
            <field line="35" modifiers="final public" name="numModsWithdrawn" xml:id="j1-108">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
            <field line="36" modifiers="final public" name="numOrdersChecked" xml:id="j1-109">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
            <field line="37" modifiers="final public" name="numElementsMoved" xml:id="j1-110">
                <localType>Counter</localType>
                <type>com.ideanest.dscribe.mixt.Counter</type>
            </field>
        </class>
        <field line="41" modifiers="final private" name="ruleMap" xml:id="j1-111">
            <localType>Map</localType>
            <type>java.util.Map</type>
        </field>
        <field line="42" modifiers="final private" name="rules" xml:id="j1-112">
            <localType>List</localType>
            <type>java.util.List</type>
        </field>
        <field line="44" modifiers="final private" name="workspace" xml:id="j1-113">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <field line="44" modifiers="final private" name="modulespace" xml:id="j1-114">
            <localType>Folder</localType>
            <type>org.exist.fluent.Folder</type>
        </field>
        <field line="45" modifiers="final private" name="utilQuery" xml:id="j1-115">
            <localType>QueryService</localType>
            <type>org.exist.fluent.QueryService</type>
        </field>
        <field line="46" modifiers="final private" name="modStore" xml:id="j1-116">
            <localType>Node</localType>
            <type>org.exist.fluent.Node</type>
        </field>
        <field line="48" modifiers="private" name="autoGenIdPrefix" xml:id="j1-117">
            <localType>String</localType>
            <type>java.lang.String</type>
        </field>
        <field line="49" modifiers="private" name="didWork" xml:id="j1-118">
            <type>boolean</type>
        </field>
        <field line="49" modifiers="private" name="docsModified" xml:id="j1-119">
            <type>boolean</type>
        </field>
        <field line="50" modifiers="final" name="stats" xml:id="j1-120">
            <localType>Stats</localType>
            <typeNotResolved>not found</typeNotResolved>
        </field>
        <field line="51" modifiers="final private" name="modifiedDocs" xml:id="j1-121">
            <localType>Accumulator</localType>
            <type>com.ideanest.dscribe.mixt.Accumulator</type>
        </field>
        <field line="52" modifiers="final private" name="sortController" xml:id="j1-122">
            <localType>SortController</localType>
            <type>com.ideanest.dscribe.mixt.SortController</type>
        </field>
        <field line="54" modifiers="final private" name="random" xml:id="j1-123">
            <localType>Random</localType>
            <type>java.util.Random</type>
        </field>
        <field line="55" modifiers="final private" name="modCountFormatter" xml:id="j1-124">
            <localType>Counter</localType>
            <type>com.ideanest.dscribe.mixt.Counter</type>
        </field>
        <field line="56" modifiers="final private" name="affectedCountFormatter" xml:id="j1-125">
            <localType>Counter</localType>
            <type>com.ideanest.dscribe.mixt.Counter</type>
        </field>
        <constructor line="59" modifiers="public" xml:id="j1-126">
            <param name="rulespace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
            <param name="prevrulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
            <param name="workspace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
            <param name="modStore">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </constructor>
        <constructor line="110" modifiers="private" xml:id="j1-127">
            <param name="workspace">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </param>
            <param name="modStore">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </constructor>
        <class fullName="com.ideanest.dscribe.mixt.Engine.Module" implName="com.ideanest.dscribe.mixt.Engine$Module" line="123" modifiers="static" name="Module" xml:id="j1-128">
            <field line="124" modifiers="final static private" name="LOCAL_NS" xml:id="j1-129">
                <localType>String</localType>
                <type>java.lang.String</type>
            </field>
            <field line="125" modifiers="final private" name="functions" xml:id="j1-130">
                <localType>Map</localType>
                <type>java.util.Map</type>
            </field>
            <field line="126" modifiers="final private" name="namespaceBindings" xml:id="j1-131">
                <localType>NamespaceMap</localType>
                <type>org.exist.fluent.NamespaceMap</type>
            </field>
            <field line="127" modifiers="private" name="moduleDoc" xml:id="j1-132">
                <localType>Document</localType>
                <type>org.exist.fluent.Document</type>
            </field>
            <constructor line="129" modifiers="" xml:id="j1-133">
                <param name="rulesDoc">
                    <localType>XMLDocument</localType>
                    <type>org.exist.fluent.XMLDocument</type>
                </param>
            </constructor>
            <method line="133" modifiers="" name="defineFunction" xml:id="j1-134">
                <returns>
                    <type>void</type>
                </returns>
                <param name="def">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="144" modifiers="private" name="getFunction" xml:id="j1-135">
                <returns>
                    <localType>Function</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <param name="name">
                    <localType>QName</localType>
                    <type>org.exist.fluent.QName</type>
                </param>
            </method>
            <method line="148" modifiers="" name="saveTo" xml:id="j1-136">
                <returns>
                    <type>void</type>
                </returns>
                <param name="modulespace">
                    <localType>Folder</localType>
                    <type>org.exist.fluent.Folder</type>
                </param>
            </method>
            <method line="156" modifiers="" name="source" xml:id="j1-137">
                <returns>
                    <localType>Document</localType>
                    <type>org.exist.fluent.Document</type>
                </returns>
            </method>
            <method line="160" modifiers="" name="resolveReferencedFunctions" xml:id="j1-138">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="164" modifiers="" name="areFunctionsModified" xml:id="j1-139">
                <returns>
                    <type>boolean</type>
                </returns>
                <param name="calledFunctionNames">
                    <localType>Set</localType>
                    <type>java.util.Set</type>
                </param>
                <param name="prevModule">
                    <localType>Module</localType>
                    <type>com.ideanest.dscribe.mixt.Engine.Module</type>
                </param>
            </method>
            <method line="174" modifiers="private" name="appendModuleHeaderTo" xml:id="j1-140">
                <returns>
                    <type>void</type>
                </returns>
                <param name="out">
                    <localType>StringBuilder</localType>
                    <type>java.lang.StringBuilder</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.Engine.Module.Function" implName="com.ideanest.dscribe.mixt.Engine$Module$Function" line="187" modifiers="static private" name="Function" xml:id="j1-141">
                <implements>
                    <localType>Comparable</localType>
                    <type>java.lang.Comparable</type>
                </implements>
                <field line="188" modifiers="final private" name="name" xml:id="j1-142">
                    <localType>QName</localType>
                    <type>org.exist.fluent.QName</type>
                </field>
                <field line="189" modifiers="final private" name="query" xml:id="j1-143">
                    <localType>Query.Items</localType>
                    <type>com.ideanest.dscribe.mixt.Query.Items</type>
                </field>
                <field line="190" modifiers="final private" name="args" xml:id="j1-144">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </field>
                <field line="191" modifiers="final private" name="referencedFunctionNames" xml:id="j1-145">
                    <localType>Set</localType>
                    <type>java.util.Set</type>
                </field>
                <field line="193" modifiers="private" name="allReferencedFunctionsAndSelf" xml:id="j1-146">
                    <localType>Set</localType>
                    <type>java.util.Set</type>
                </field>
                <constructor line="195" modifiers="" xml:id="j1-147">
                    <param name="def">
                        <localType>Node</localType>
                        <type>org.exist.fluent.Node</type>
                    </param>
                    <throws>
                        <localType>RuleBaseException</localType>
                        <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                    </throws>
                </constructor>
                <method line="203" modifiers="" name="isModified" xml:id="j1-148">
                    <returns>
                        <type>boolean</type>
                    </returns>
                    <param name="prevModule">
                        <localType>Module</localType>
                        <type>com.ideanest.dscribe.mixt.Engine.Module</type>
                    </param>
                </method>
                <method line="211" modifiers="" name="resolveReferencedFunctions" xml:id="j1-149">
                    <returns>
                        <type>void</type>
                    </returns>
                    <param name="module">
                        <localType>Module</localType>
                        <type>com.ideanest.dscribe.mixt.Engine.Module</type>
                    </param>
                </method>
                <method line="226" modifiers="" name="appendTo" xml:id="j1-150">
                    <returns>
                        <type>void</type>
                    </returns>
                    <param name="out">
                        <localType>StringBuilder</localType>
                        <type>java.lang.StringBuilder</type>
                    </param>
                </method>
                <method line="232" modifiers="public" name="compareTo" xml:id="j1-151">
                    <returns>
                        <type>int</type>
                    </returns>
                    <param name="o">
                        <localType>Function</localType>
                        <type>com.ideanest.dscribe.mixt.Engine.Module.Function</type>
                    </param>
                </method>
            </class>
        </class>
        <method line="239" modifiers="private" name="parseFunctions" xml:id="j1-152">
            <returns>
                <localType>Map</localType>
                <type>java.util.Map</type>
            </returns>
            <param name="rulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <method line="251" modifiers="private" name="parseRules" xml:id="j1-153">
            <returns>
                <type>void</type>
            </returns>
            <param name="rulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
            <param name="prevrulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
            <param name="modules">
                <localType>Map</localType>
                <type>java.util.Map</type>
            </param>
            <param name="prevModules">
                <localType>Map</localType>
                <type>java.util.Map</type>
            </param>
            <throws>
                <localType>RuleBaseException</localType>
                <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
            </throws>
        </method>
        <method line="265" modifiers="private" name="invalidateIncompatibleBlocks" xml:id="j1-154">
            <returns>
                <type>void</type>
            </returns>
            <param name="prevrulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
        </method>
        <method line="279" modifiers="private" name="assignRuleIds" xml:id="j1-155">
            <returns>
                <type>void</type>
            </returns>
            <param name="rulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
            <param name="prevrulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
        </method>
        <method line="295" modifiers="static private" name="acronymize" xml:id="j1-156">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="name">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="340" modifiers="static public" name="isSameVersionAs" xml:id="j1-157">
            <returns>
                <type>boolean</type>
            </returns>
            <comment>Return whether the currently loaded MIXT processing code is the same version as
that used for the last run of the transformation.</comment>
            <tag name="param">prevrulespace an ancestor of the resource to which versions were recorded on the last run</tag>
            <tag name="return">&lt;code&gt;true&lt;/code&gt; if the engine version matches, &lt;code&gt;false&lt;/code&gt; otherwise</tag>
            <param name="prevrulespace">
                <localType>Resource</localType>
                <type>org.exist.fluent.Resource</type>
            </param>
        </method>
        <method line="350" modifiers="static public" name="recordVersions" xml:id="j1-158">
            <returns>
                <type>void</type>
            </returns>
            <comment>Record the versions of the various currently loaded pieces of MIXT processing code.</comment>
            <tag name="param">builder a builder on the resource to write to</tag>
            <param name="builder">
                <localType>ElementBuilder</localType>
                <type>org.exist.fluent.ElementBuilder</type>
            </param>
        </method>
        <method line="356" modifiers="" name="relativePath" xml:id="j1-159">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="doc">
                <localType>Document</localType>
                <type>org.exist.fluent.Document</type>
            </param>
        </method>
        <method line="360" modifiers="" name="workspace" xml:id="j1-160">
            <returns>
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </returns>
        </method>
        <method line="361" modifiers="" name="utilQuery" xml:id="j1-161">
            <returns>
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </returns>
        </method>
        <method line="362" modifiers="" name="modStore" xml:id="j1-162">
            <returns>
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </returns>
        </method>
        <method line="363" modifiers="public" name="stats" xml:id="j1-163">
            <returns>
                <localType>Stats</localType>
                <typeNotResolved>not found</typeNotResolved>
            </returns>
        </method>
        <method line="365" modifiers="" name="findRule" xml:id="j1-164">
            <returns>
                <localType>Rule</localType>
                <type>com.ideanest.dscribe.mixt.Rule</type>
            </returns>
            <param name="ruleId">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="369" modifiers="public" name="autoGenerateIdsWithPrefix" xml:id="j1-165">
            <returns>
                <type>void</type>
            </returns>
            <param name="prefix">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="375" modifiers="" name="ensureWorkspaceNodeHasXmlId" xml:id="j1-166">
            <returns>
                <type>boolean</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="382" modifiers="" name="generateUniqueId" xml:id="j1-167">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
            <param name="prefix">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
            <param name="scope">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="393" modifiers="" name="eventuallySort" xml:id="j1-168">
            <returns>
                <type>void</type>
            </returns>
            <param name="node">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <field line="397" modifiers="final private" name="modifiedDocListener" xml:id="j1-169">
            <localType>Document.Listener</localType>
            <type>org.exist.fluent.Document.Listener</type>
        </field>
        <method line="419" modifiers="public" name="executeTransform" xml:id="j1-170">
            <returns>
                <localType>Date</localType>
                <type>java.util.Date</type>
            </returns>
            <comment>Run the transformation.</comment>
            <tag name="param">initialModifiedDocs the docs that may have been modified since the last run; if &lt;code&gt;null&lt;/code&gt;, don't do incremental processing</tag>
            <tag name="return">the date as of which the transformation is current; any documents modified after this date may not have been considered</tag>
            <tag name="throws">TransformException</tag>
            <tag name="throws">InterruptedException</tag>
            <param name="initialModifiedDocs">
                <localType>Collection</localType>
                <type>java.util.Collection</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
            <throws>
                <localType>InterruptedException</localType>
                <type>java.lang.InterruptedException</type>
            </throws>
        </method>
        <method line="451" modifiers="" name="withdrawMod" xml:id="j1-171">
            <returns>
                <type>void</type>
            </returns>
            <param name="modNode">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="458" modifiers="" name="withdrawRule" xml:id="j1-172">
            <returns>
                <type>void</type>
            </returns>
            <param name="ruleId">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="463" modifiers="" name="withdrawMods" xml:id="j1-173">
            <returns>
                <type>void</type>
            </returns>
            <param name="newMods">
                <localType>ItemList</localType>
                <type>org.exist.fluent.ItemList</type>
            </param>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.Engine._Test" implName="com.ideanest.dscribe.mixt.Engine$_Test" line="505" modifiers="static public" name="_Test" xml:id="j1-174">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="507" modifiers="static private" name="firstDifferentStage" xml:id="j1-175">
                <localType>Field</localType>
                <type>java.lang.reflect.Field</type>
            </field>
            <method line="508" modifiers="static public" name="setupAccessors" xml:id="j1-176">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>SecurityException</localType>
                    <type>java.lang.SecurityException</type>
                </throws>
                <throws>
                    <localType>NoSuchFieldException</localType>
                    <type>java.lang.NoSuchFieldException</type>
                </throws>
            </method>
            <field line="513" modifiers="final protected" name="mockery" xml:id="j1-177">
                <localType>Mockery</localType>
                <type>org.jmock.Mockery</type>
            </field>
            <field line="516" modifiers="private" name="workspace" xml:id="j1-178">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <field line="516" modifiers="private" name="rulespace" xml:id="j1-179">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <field line="516" modifiers="private" name="prevrulespace" xml:id="j1-180">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <method line="518" modifiers="public" name="createSpaces" xml:id="j1-181">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="527" modifiers="public" name="invalidateIncompatibleBlocks" xml:id="j1-182">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="552" modifiers="public" name="recordAndVerifyVersion" xml:id="j1-183">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="560" modifiers="public" name="verifyBadVersion" xml:id="j1-184">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="568" modifiers="public" name="parseRules" xml:id="j1-185">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
                <throws>
                    <localType>IllegalAccessException</localType>
                    <type>java.lang.IllegalAccessException</type>
                </throws>
            </method>
            <method line="582" modifiers="public" name="assignRuleIDs_generateFreshID" xml:id="j1-186">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="596" modifiers="public" name="assignRuleIDs_copyPrevID" xml:id="j1-187">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="613" modifiers="public" name="assignRuleIDs_matchAliasToName" xml:id="j1-188">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="630" modifiers="public" name="assignRuleIDs_matchNameToAlias" xml:id="j1-189">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="647" modifiers="public" name="assignRuleIDs_matchAliasToAlias" xml:id="j1-190">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="664" modifiers="public" name="assignRuleIDs_multipleMatch" xml:id="j1-191">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="683" modifiers="public" name="create" xml:id="j1-192">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="714" modifiers="public" name="ensureWorkspaceNodeHasXmlId" xml:id="j1-193">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="719" modifiers="public" name="ensureWorkspaceNodeHasXmlId_noIdNoAutogen" xml:id="j1-194">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="724" modifiers="public" name="ensureWorkspaceNodeHasXmlId_noIdWithAutogen" xml:id="j1-195">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="732" modifiers="public" name="executeTransform" xml:id="j1-196">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>InterruptedException</localType>
                    <type>java.lang.InterruptedException</type>
                </throws>
            </method>
            <method line="760" modifiers="public" name="executeTransformCanBeInterrupted" xml:id="j1-197">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
                <throws>
                    <localType>InterruptedException</localType>
                    <type>java.lang.InterruptedException</type>
                </throws>
            </method>
            <method line="782" modifiers="public" name="withdrawModsSimple" xml:id="j1-198">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="802" modifiers="public" name="withdrawModsWithDescendants" xml:id="j1-199">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="825" modifiers="public" name="withdrawModsWithAffectedDocs" xml:id="j1-200">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="849" modifiers="public" name="withdrawModsWithAffectedDocsAndDependencies" xml:id="j1-201">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="880" modifiers="public" name="withdrawModsWithAffectedDocsAndKnockOnReferences" xml:id="j1-202">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="911" modifiers="public" name="withdrawModsWithOrderedNodes" xml:id="j1-203">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>SecurityException</localType>
                    <type>java.lang.SecurityException</type>
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
            <method line="939" modifiers="public" name="withdrawRule" xml:id="j1-204">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="961" modifiers="public" name="withdrawMod" xml:id="j1-205">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="981" modifiers="private" name="aCollectionOf" xml:id="j1-206">
                <returns>
                    <localType>Matcher</localType>
                    <type>org.hamcrest.Matcher</type>
                </returns>
                <param name="items">
                    <localType>E</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </param>
            </method>
            <method line="993" modifiers="private" name="anEmptyCollection" xml:id="j1-207">
                <returns>
                    <localType>Matcher</localType>
                    <type>org.hamcrest.Matcher</type>
                </returns>
                <param name="clazz">
                    <localType>Class</localType>
                    <type>java.lang.Class</type>
                </param>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Engine._TestModule" implName="com.ideanest.dscribe.mixt.Engine$_TestModule" line="1005" modifiers="static public" name="_TestModule" xml:id="j1-208">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="1007" modifiers="final protected" name="mockery" xml:id="j1-209">
                <localType>Mockery</localType>
                <type>org.jmock.Mockery</type>
            </field>
            <method line="1011" modifiers="private" name="makeRules" xml:id="j1-210">
                <returns>
                    <localType>XMLDocument</localType>
                    <type>org.exist.fluent.XMLDocument</type>
                </returns>
                <param name="fn">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="1018" modifiers="private" name="defineModule" xml:id="j1-211">
                <returns>
                    <localType>Engine.Module</localType>
                    <type>com.ideanest.dscribe.mixt.Engine.Module</type>
                </returns>
                <param name="functions">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="1025" modifiers="public" name="defineFunctionGood" xml:id="j1-212">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="1035" modifiers="public" name="defineFunctionExtraNamespaces" xml:id="j1-213">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="1040" modifiers="public" name="defineFunctionOverload" xml:id="j1-214">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="1045" modifiers="public" name="saveTo" xml:id="j1-215">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
            <method line="1063" modifiers="public" name="areFunctionsModified" xml:id="j1-216">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>RuleBaseException</localType>
                    <type>com.ideanest.dscribe.mixt.RuleBaseException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Engine._TestAcronymize" implName="com.ideanest.dscribe.mixt.Engine$_TestAcronymize" line="1119" modifiers="static public" name="_TestAcronymize" xml:id="j1-217">
            <method line="1121" modifiers="public" name="test1" xml:id="j1-218">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1124" modifiers="public" name="test2" xml:id="j1-219">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1127" modifiers="public" name="test3" xml:id="j1-220">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1130" modifiers="public" name="test4" xml:id="j1-221">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1133" modifiers="public" name="test5" xml:id="j1-222">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1136" modifiers="public" name="test6" xml:id="j1-223">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1139" modifiers="public" name="test7" xml:id="j1-224">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1142" modifiers="public" name="test8" xml:id="j1-225">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1145" modifiers="public" name="test9" xml:id="j1-226">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1148" modifiers="public" name="test10" xml:id="j1-227">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1151" modifiers="public" name="test11" xml:id="j1-228">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1154" modifiers="public" name="test12" xml:id="j1-229">
                <returns>
                    <type>void</type>
                </returns>
            </method>
        </class>
    </class>
</unit>