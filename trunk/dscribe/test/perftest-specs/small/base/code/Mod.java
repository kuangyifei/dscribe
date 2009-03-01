<unit xmlns="http://ideanest.com/dscribe/ns/java">
    <file type="source">src\com\ideanest\dscribe\mixt\Mod.java</file>
    <packageref>com.ideanest.dscribe.mixt</packageref>
    <import>org.hamcrest.Matchers.*</import>
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
    <class fullName="com.ideanest.dscribe.mixt.Mod" implName="com.ideanest.dscribe.mixt.Mod" line="20" modifiers="public" name="Mod" xml:id="j1-267">
        <field line="22" modifiers="final static" name="LOG" xml:id="j1-268">
            <localType>Logger</localType>
            <type>org.apache.log4j.Logger</type>
        </field>
        <field line="23" modifiers="final static" name="MOD_NAMESPACE" xml:id="j1-269">
            <localType>NamespaceMap</localType>
            <type>org.exist.fluent.NamespaceMap</type>
        </field>
        <field line="24" modifiers="final static" name="EMPTY_NAMESPACES" xml:id="j1-270">
            <localType>NamespaceMap</localType>
            <type>org.exist.fluent.NamespaceMap</type>
        </field>
        <field line="26" modifiers="final" name="rule" xml:id="j1-271">
            <localType>Rule</localType>
            <type>com.ideanest.dscribe.mixt.Rule</type>
        </field>
        <field line="27" modifiers="final" name="stage" xml:id="j1-272">
            <type>int</type>
        </field>
        <field line="28" modifiers="final" name="parent" xml:id="j1-273">
            <localType>Mod</localType>
            <type>com.ideanest.dscribe.mixt.Mod</type>
        </field>
        <field line="30" modifiers="private" name="previouslyResolved" xml:id="j1-274">
            <type>boolean</type>
        </field>
        <field line="31" modifiers="private" name="boundVariables" xml:id="j1-275">
            <localType>Set</localType>
            <type>java.util.Set</type>
        </field>
        <field line="32" modifiers="private" name="references" xml:id="j1-276">
            <localType>List</localType>
            <type>java.util.List</type>
        </field>
        <field line="33" modifiers="private" name="seg" xml:id="j1-277">
            <localType>Seg</localType>
            <type>com.ideanest.dscribe.mixt.Seg</type>
        </field>
        <field line="34" modifiers="private" name="node" xml:id="j1-278">
            <localType>Node</localType>
            <type>org.exist.fluent.Node</type>
        </field>
        <field line="35" modifiers="private" name="supplementQuery" xml:id="j1-279">
            <localType>QueryService</localType>
            <type>org.exist.fluent.QueryService</type>
        </field>
        <field line="37" modifiers="private" name="self" xml:id="j1-280">
            <localType>Shim</localType>
            <typeNotResolved>not found</typeNotResolved>
        </field>
        <constructor line="39" modifiers="" xml:id="j1-281">
            <param name="rule">
                <localType>Rule</localType>
                <type>com.ideanest.dscribe.mixt.Rule</type>
            </param>
        </constructor>
        <constructor line="47" modifiers="" xml:id="j1-282">
            <param name="parent">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </param>
        </constructor>
        <interface fullName="com.ideanest.dscribe.mixt.Mod.Shim" implName="com.ideanest.dscribe.mixt.Mod$Shim" line="55" modifiers="private" name="Shim" xml:id="j1-283">
            <method line="56" modifiers="" name="prepScopeClone" xml:id="j1-284">
                <returns>
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </returns>
                <param name="queryService">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
            </method>
            <method line="57" modifiers="" name="deriveChild" xml:id="j1-285">
                <returns>
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </returns>
                <param name="block">
                    <localType>Block</localType>
                    <type>com.ideanest.dscribe.mixt.Block</type>
                </param>
                <param name="key">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
        </interface>
        <method line="60" modifiers="private" name="initDefaultShim" xml:id="j1-286">
            <returns>
                <type>void</type>
            </returns>
        </method>
        <method line="71" modifiers="" name="binder" xml:id="j1-287">
            <returns>
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </returns>
            <param name="varName">
                <localType>QName</localType>
                <type>org.exist.fluent.QName</type>
            </param>
        </method>
        <method line="76" modifiers="" name="variableBindings" xml:id="j1-288">
            <returns>
                <localType>Map</localType>
                <type>java.util.Map</type>
            </returns>
        </method>
        <method line="80" modifiers="public" name="globalScope" xml:id="j1-289">
            <returns>
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </returns>
        </method>
        <method line="92" modifiers="public" name="scope" xml:id="j1-290">
            <returns>
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </returns>
            <comment>Take the given query service (or the workspace query service if the given one is &lt;code&gt;null&lt;/code&gt;)
and bind all the variables defined by previous blocks in the mod chain. Return a query service ready
to run verification queries on.</comment>
            <tag name="param">qs the query service to use as a base, or &lt;code&gt;null&lt;/code&gt; to use the workspace query service</tag>
            <tag name="return">a clone of the given query service with variables from previous blocks bound and namespace map cleared</tag>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="97" modifiers="" name="prepScopeClone" xml:id="j1-291">
            <returns>
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </returns>
            <param name="qs">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
        </method>
        <method line="103" modifiers="public" name="bindVariable" xml:id="j1-292">
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
        <method line="111" modifiers="" name="seg" xml:id="j1-293">
            <returns>
                <localType>Seg</localType>
                <type>com.ideanest.dscribe.mixt.Seg</type>
            </returns>
        </method>
        <method line="112" modifiers="" name="node" xml:id="j1-294">
            <returns>
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </returns>
        </method>
        <method line="113" modifiers="public" name="key" xml:id="j1-295">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="114" modifiers="public" name="supplementQuery" xml:id="j1-296">
            <returns>
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </returns>
        </method>
        <method line="116" modifiers="public" name="workspace" xml:id="j1-297">
            <returns>
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </returns>
        </method>
        <method line="120" modifiers="public" name="references" xml:id="j1-298">
            <returns>
                <localType>List</localType>
                <type>java.util.List</type>
            </returns>
        </method>
        <method line="133" modifiers="public" name="nearest" xml:id="j1-299">
            <returns>
                <localType>T</localType>
                <typeNotResolved>not found</typeNotResolved>
            </returns>
            <comment>Return the nearest segment implementing the given type by traversing through
this mod's ancestor mods. This mod is not itself included in the search.</comment>
            <tag name="param">&lt;T&gt; the type of segment to look for</tag>
            <tag name="param">clazz the type of segment to look for</tag>
            <tag name="return">the nearest segment implementing the desired type</tag>
            <tag name="throws">TransformException if a segment implementing the desired type cannot be found</tag>
            <param name="clazz">
                <localType>Class</localType>
                <type>java.lang.Class</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="137" modifiers="" name="nearestAncestorOrSelfImplementing" xml:id="j1-300">
            <returns>
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </returns>
            <param name="clazz">
                <localType>Class</localType>
                <type>java.lang.Class</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="142" modifiers="" name="resolveChildren" xml:id="j1-301">
            <returns>
                <type>int</type>
            </returns>
            <param name="block">
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </param>
            <param name="lastBlock">
                <type>boolean</type>
            </param>
            <param name="touchedScope">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="162" modifiers="" name="deriveChild" xml:id="j1-302">
            <returns>
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </returns>
            <param name="block">
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </param>
            <param name="key">
                <localType>String</localType>
                <type>java.lang.String</type>
            </param>
        </method>
        <method line="169" modifiers="" name="restoreChild" xml:id="j1-303">
            <returns>
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </returns>
            <param name="block">
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </param>
            <param name="modNode">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="176" modifiers="" name="restore" xml:id="j1-304">
            <returns>
                <type>void</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="194" modifiers="private" name="setNode" xml:id="j1-305">
            <returns>
                <type>void</type>
            </returns>
            <param name="myNode">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </param>
        </method>
        <method line="201" modifiers="public" name="affectedIds" xml:id="j1-306">
            <returns>
                <localType>List</localType>
                <type>java.util.List</type>
            </returns>
        </method>
        <method line="205" modifiers="" name="verify" xml:id="j1-307">
            <returns>
                <type>void</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="217" modifiers="" name="analyze" xml:id="j1-308">
            <returns>
                <localType>QueryService.QueryAnalysis</localType>
                <type>org.exist.fluent.QueryService.QueryAnalysis</type>
            </returns>
            <throws>
                <localType>TransformException</localType>
                <type>com.ideanest.dscribe.mixt.TransformException</type>
            </throws>
        </method>
        <method line="222" modifiers="public" name="toString" xml:id="j1-309">
            <returns>
                <localType>String</localType>
                <type>java.lang.String</type>
            </returns>
        </method>
        <method line="227" modifiers="static" name="bootstrap" xml:id="j1-310">
            <returns>
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </returns>
            <param name="rule">
                <localType>Rule</localType>
                <type>com.ideanest.dscribe.mixt.Rule</type>
            </param>
        </method>
        <class fullName="com.ideanest.dscribe.mixt.Mod.Builder" implName="com.ideanest.dscribe.mixt.Mod$Builder" line="249" modifiers="static public" name="Builder" xml:id="j1-311">
            <comment>A mod builder enables a linear block to derive a child mod from a parent mod. The
workflow is to call various parameter-setting methods on the builder, and finish with
a single call to the {@link #commit()} method, which actually creates the child mod.</comment>
            <tag name="author">&lt;a href="mailto:piotr@ideanest.com"&gt;Piotr Kaminski&lt;/a&gt;</tag>
            <field line="251" modifiers="final" name="parent" xml:id="j1-312">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </field>
            <field line="252" modifiers="final private" name="block" xml:id="j1-313">
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </field>
            <field line="253" modifiers="final private" name="scope" xml:id="j1-314">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </field>
            <field line="254" modifiers="final private" name="lastBlock" xml:id="j1-315">
                <type>boolean</type>
            </field>
            <field line="256" modifiers="private" name="dependentDocNames" xml:id="j1-316">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </field>
            <field line="256" modifiers="private" name="unverifiedDocNames" xml:id="j1-317">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </field>
            <field line="256" modifiers="private" name="affectedNodeIds" xml:id="j1-318">
                <localType>Set</localType>
                <type>java.util.Set</type>
            </field>
            <field line="257" modifiers="private" name="references" xml:id="j1-319">
                <localType>List</localType>
                <type>java.util.List</type>
            </field>
            <field line="257" modifiers="private" name="orders" xml:id="j1-320">
                <localType>List</localType>
                <type>java.util.List</type>
            </field>
            <field line="258" modifiers="private" name="supplement" xml:id="j1-321">
                <localType>ElementBuilder</localType>
                <type>org.exist.fluent.ElementBuilder</type>
            </field>
            <field line="260" modifiers="" name="childCount" xml:id="j1-322">
                <type>int</type>
            </field>
            <constructor line="262" modifiers="" xml:id="j1-323">
                <param name="parent">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
                <param name="block">
                    <localType>Block</localType>
                    <type>com.ideanest.dscribe.mixt.Block</type>
                </param>
                <param name="lastBlock">
                    <type>boolean</type>
                </param>
                <param name="scope">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
            </constructor>
            <method line="270" modifiers="" name="reset" xml:id="j1-324">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="284" modifiers="public" name="parent" xml:id="j1-325">
                <returns>
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </returns>
                <comment>Return the mod that will be the parent of all mods created by this builder.</comment>
                <tag name="return">the parent mod for built children</tag>
            </method>
            <method line="295" modifiers="public" name="dependOnNearest" xml:id="j1-326">
                <returns>
                    <localType>AncestorDependencyModifier</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <comment>Return the nearest segment implementing the given type by traversing through the ancestor mod chain,
starting at the parent of the mod being built.</comment>
                <tag name="param">&lt;T&gt; the type of the segment to look for</tag>
                <tag name="param">clazz the type of the segment to look for</tag>
                <tag name="return">a dependency modifier that can be used to declare the dependency as unverified; call &lt;code&gt;get()&lt;/code&gt; to retrieve the actual segment</tag>
                <tag name="throws">TransformException if a segment implementing the desired type cannot be found</tag>
                <param name="clazz">
                    <localType>Class</localType>
                    <type>java.lang.Class</type>
                </param>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="306" modifiers="public" name="commit" xml:id="j1-327">
                <returns>
                    <type>void</type>
                </returns>
                <comment>Create and store the mod being built using the parameters previously specified on this builder, then
reset the builder for the creation of another mod with the same parent.</comment>
                <tag name="throws">TransformException</tag>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="323" modifiers="private" name="writeMod" xml:id="j1-328">
                <returns>
                    <type>void</type>
                </returns>
                <param name="key">
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </param>
            </method>
            <method line="355" modifiers="" name="checkChildCount" xml:id="j1-329">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="359" modifiers="" name="key" xml:id="j1-330">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
            </method>
            <method line="363" modifiers="public" name="openScope" xml:id="j1-331">
                <returns>
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </returns>
            </method>
            <method line="367" modifiers="public" name="closedScope" xml:id="j1-332">
                <returns>
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </returns>
            </method>
            <method line="371" modifiers="public" name="customScope" xml:id="j1-333">
                <returns>
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </returns>
                <param name="qs">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </param>
            </method>
            <method line="382" modifiers="public" name="supplement" xml:id="j1-334">
                <returns>
                    <localType>ElementBuilder</localType>
                    <type>org.exist.fluent.ElementBuilder</type>
                </returns>
                <comment>Return an element builder that can be used to add supplemental information to be stored
with the mod being built. There is one supplemental builder per mod built, and it will be
committed when the mod builder itself is committed.</comment>
                <tag name="return">this mod builder's supplemental element builder; don't commit it</tag>
            </method>
            <method line="394" modifiers="public" name="dependOn" xml:id="j1-335">
                <returns>
                    <localType>DependencyModifier</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <comment>Declare that the mod being built depends on the given document. The dependency is
assumed to be verifiable by default.</comment>
                <tag name="param">doc the document the mod being built depends on</tag>
                <tag name="return">a dependency modifier that lets you mark this dependency as unverified</tag>
                <param name="doc">
                    <localType>XMLDocument</localType>
                    <type>org.exist.fluent.XMLDocument</type>
                </param>
            </method>
            <method line="398" modifiers="private" name="dependOn" xml:id="j1-336">
                <returns>
                    <localType>DependencyModifier</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <param name="docs">
                    <localType>Collection</localType>
                    <type>java.util.Collection</type>
                </param>
                <param name="depMod">
                    <localType>DependencyModifier</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </param>
            </method>
            <method line="411" modifiers="public" name="dependOn" xml:id="j1-337">
                <returns>
                    <localType>DependencyModifier</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <comment>Declare that the mod being built depends on the given ancestor mod, adding all of that
mod's unverified dependencies to the one being built. The dependency is assumed to be
verifiable by default.</comment>
                <tag name="param">ancestor an ancestor of the mod being built, starting with this builder's parent</tag>
                <tag name="return">a dependency modifier that lets you mark this dependency as unverified</tag>
                <param name="ancestor">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
            </method>
            <method line="415" modifiers="private" name="dependOn" xml:id="j1-338">
                <returns>
                    <localType>T</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <param name="ancestor">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </param>
                <param name="depMod">
                    <localType>T</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </param>
            </method>
            <method line="433" modifiers="public" name="dependOn" xml:id="j1-339">
                <returns>
                    <localType>DependencyModifier</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </returns>
                <comment>Declare that the mod being built depends on the given list of variables.
This will automatically declare dependencies on the mods that have bound these variables,
as well as on the parent documents of any persistent nodes that are bound to the variables.
The dependency is assumed to be verifiable by default.</comment>
                <tag name="param">variables the names of the variables to depend on</tag>
                <tag name="return">a dependency modifier that lets you mark this dependency as unverified</tag>
                <param name="variables">
                    <localType>Collection</localType>
                    <type>java.util.Collection</type>
                </param>
            </method>
            <method line="471" modifiers="public" name="affect" xml:id="j1-340">
                <returns>
                    <type>void</type>
                </returns>
                <comment>Declare that the mod being built affects the given node, so that if the mod is
invalidated the node needs to be recomputed.</comment>
                <tag name="param">node the persistent node that's modified by the mod being built</tag>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
            </method>
            <method line="486" modifiers="public" name="order" xml:id="j1-341">
                <returns>
                    <type>void</type>
                </returns>
                <comment>Declare that the mod being built has an opinion about the ordering of the given node within its
parent. The seg corresponding to the mod must implement OrderProvider.</comment>
                <tag name="param">node the persistent node whose order relative to its siblings is affected by the mod being built</tag>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
            </method>
            <method line="505" modifiers="public" name="reference" xml:id="j1-342">
                <returns>
                    <type>void</type>
                </returns>
                <comment>Declare that the mod being built references the given node, and hence also depends
on the node's parent document. The dependency must be verifiable.</comment>
                <tag name="param">node the node that's referenced by the mod being built</tag>
                <param name="node">
                    <localType>Node</localType>
                    <type>org.exist.fluent.Node</type>
                </param>
            </method>
            <method line="525" modifiers="public" name="generateId" xml:id="j1-343">
                <returns>
                    <localType>String</localType>
                    <type>java.lang.String</type>
                </returns>
                <comment>Generate a new ID, suitable for an xml:id attribute, based on the parent mod's key and
the stage of the mod being built. If a non-negative serial number is given, include it in
the ID as well. The generated ID will be different from IDs generated in other rules,
from those generated by other key mod chains in the current rule, and from those generated
by other stages in the current rule (even if there's no interspersed key mods). However,
calls to this method that span builder commits will keep returning the same value, so it's only
appropriate to call it from linear blocks. The generated reproducible IDs can be assigned to nodes
created by the block.</comment>
                <tag name="param">serial a non-negative serial number if the block needs to generate multiple IDs; ignored if negative</tag>
                <tag name="return">an ID string with the properties above and a syntax appropriate for an xml:id attribute</tag>
                <param name="serial">
                    <type>int</type>
                </param>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.Mod.Builder.DependencyModifier" implName="com.ideanest.dscribe.mixt.Mod$Builder$DependencyModifier" line="532" modifiers="public" name="DependencyModifier" xml:id="j1-344">
                <field line="533" modifiers="final private" name="docNames" xml:id="j1-345">
                    <localType>Set</localType>
                    <type>java.util.Set</type>
                </field>
                <method line="535" modifiers="" name="add" xml:id="j1-346">
                    <returns>
                        <type>void</type>
                    </returns>
                    <param name="docName">
                        <localType>String</localType>
                        <type>java.lang.String</type>
                    </param>
                </method>
                <method line="540" modifiers="" name="addAll" xml:id="j1-347">
                    <returns>
                        <type>void</type>
                    </returns>
                    <param name="docNames_">
                        <localType>Collection</localType>
                        <type>java.util.Collection</type>
                    </param>
                </method>
                <method line="553" modifiers="public" name="unverified" xml:id="j1-348">
                    <returns>
                        <localType>DependencyModifier</localType>
                        <type>com.ideanest.dscribe.mixt.Mod.Builder.DependencyModifier</type>
                    </returns>
                    <comment>Mark the previous dependency as unverified. Unverified dependencies can never cause
a seg's verification to fail, and so cannot be used to validate stored mods. All dependencies
must be verified by the time the last block of a rule is reached.</comment>
                    <tag name="return">this dependency modifier, for chaining calls</tag>
                    <tag name="throws">TransformException if called while building the last mod of a rule</tag>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
            </class>
            <class fullName="com.ideanest.dscribe.mixt.Mod.Builder.AncestorDependencyModifier" implName="com.ideanest.dscribe.mixt.Mod$Builder$AncestorDependencyModifier" line="561" modifiers="public" name="AncestorDependencyModifier" xml:id="j1-349">
                <extends>
                    <localType>DependencyModifier</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </extends>
                <field line="562" modifiers="final private" name="seg" xml:id="j1-350">
                    <localType>T</localType>
                    <typeNotResolved>not found</typeNotResolved>
                </field>
                <constructor line="563" modifiers="public" xml:id="j1-351">
                    <param name="seg">
                        <localType>T</localType>
                        <typeNotResolved>not found</typeNotResolved>
                    </param>
                </constructor>
                <method line="566" modifiers="public" name="get" xml:id="j1-352">
                    <returns>
                        <localType>T</localType>
                        <typeNotResolved>not found</typeNotResolved>
                    </returns>
                </method>
                <method line="569" modifiers="public" name="unverified" xml:id="j1-353">
                    <returns>
                        <localType>AncestorDependencyModifier</localType>
                        <type>com.ideanest.dscribe.mixt.Mod.Builder.AncestorDependencyModifier</type>
                    </returns>
                    <throws>
                        <localType>TransformException</localType>
                        <type>com.ideanest.dscribe.mixt.TransformException</type>
                    </throws>
                </method>
            </class>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Mod._Test" implName="com.ideanest.dscribe.mixt.Mod$_Test" line="577" modifiers="abstract static public" name="_Test" xml:id="j1-354">
            <extends>
                <localType>DatabaseTestCase</localType>
                <type>org.exist.fluent.DatabaseTestCase</type>
            </extends>
            <field line="579" modifiers="protected static" name="rule_engine" xml:id="j1-355">
                <localType>Field</localType>
                <type>java.lang.reflect.Field</type>
            </field>
            <field line="579" modifiers="protected static" name="rule_id" xml:id="j1-356">
                <localType>Field</localType>
                <type>java.lang.reflect.Field</type>
            </field>
            <field line="579" modifiers="protected static" name="mod_rule" xml:id="j1-357">
                <localType>Field</localType>
                <type>java.lang.reflect.Field</type>
            </field>
            <field line="579" modifiers="protected static" name="mod_stage" xml:id="j1-358">
                <localType>Field</localType>
                <type>java.lang.reflect.Field</type>
            </field>
            <field line="579" modifiers="protected static" name="modBuilder_lastBlock" xml:id="j1-359">
                <localType>Field</localType>
                <type>java.lang.reflect.Field</type>
            </field>
            <method line="580" modifiers="static public" name="initializeFieldAccessors" xml:id="j1-360">
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
            <field line="593" modifiers="final protected" name="mockery" xml:id="j1-361">
                <localType>Mockery</localType>
                <type>org.jmock.Mockery</type>
            </field>
            <field line="596" modifiers="protected" name="modStore" xml:id="j1-362">
                <localType>Node</localType>
                <type>org.exist.fluent.Node</type>
            </field>
            <field line="597" modifiers="protected" name="engine" xml:id="j1-363">
                <localType>Engine</localType>
                <type>com.ideanest.dscribe.mixt.Engine</type>
            </field>
            <field line="598" modifiers="protected" name="rule" xml:id="j1-364">
                <localType>Rule</localType>
                <type>com.ideanest.dscribe.mixt.Rule</type>
            </field>
            <field line="599" modifiers="protected" name="parentMod" xml:id="j1-365">
                <localType>Mod</localType>
                <type>com.ideanest.dscribe.mixt.Mod</type>
            </field>
            <field line="600" modifiers="protected" name="block" xml:id="j1-366">
                <localType>Block</localType>
                <type>com.ideanest.dscribe.mixt.Block</type>
            </field>
            <field line="601" modifiers="protected" name="resolutionScope" xml:id="j1-367">
                <localType>QueryService</localType>
                <type>org.exist.fluent.QueryService</type>
            </field>
            <field line="602" modifiers="protected" name="doc1" xml:id="j1-368">
                <localType>XMLDocument</localType>
                <type>org.exist.fluent.XMLDocument</type>
            </field>
            <field line="603" modifiers="protected" name="doc1Name" xml:id="j1-369">
                <localType>String</localType>
                <type>java.lang.String</type>
            </field>
            <field line="604" modifiers="protected" name="parentModBindings" xml:id="j1-370">
                <localType>Map</localType>
                <type>java.util.Map</type>
            </field>
            <field line="605" modifiers="protected" name="workspace" xml:id="j1-371">
                <localType>Folder</localType>
                <type>org.exist.fluent.Folder</type>
            </field>
            <method line="607" modifiers="public" name="setupContext" xml:id="j1-372">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>IllegalAccessException</localType>
                    <type>java.lang.IllegalAccessException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Mod._BuilderTest" implName="com.ideanest.dscribe.mixt.Mod$_BuilderTest" line="643" modifiers="static public" name="_BuilderTest" xml:id="j1-373">
            <extends>
                <localType>_Test</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <field line="644" modifiers="private" name="builder" xml:id="j1-374">
                <localType>Builder</localType>
                <typeNotResolved>not found</typeNotResolved>
            </field>
            <method line="646" modifiers="public" name="setupBuilder" xml:id="j1-375">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>IllegalArgumentException</localType>
                    <type>java.lang.IllegalArgumentException</type>
                </throws>
                <throws>
                    <localType>IllegalAccessException</localType>
                    <type>java.lang.IllegalAccessException</type>
                </throws>
            </method>
            <method line="650" modifiers="private" name="setLastBlock" xml:id="j1-376">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="654" modifiers="public" name="resetsParameterFields" xml:id="j1-377">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="663" modifiers="public" name="key" xml:id="j1-378">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="667" modifiers="public" name="supplement" xml:id="j1-379">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="673" modifiers="public" name="dependOnDoc" xml:id="j1-380">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="681" modifiers="public" name="dependOnDocUnverified" xml:id="j1-381">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="689" modifiers="public" name="dependOnDocUnverifiedLastBlock" xml:id="j1-382">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="695" modifiers="private" name="createAncestor" xml:id="j1-383">
                <returns>
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </returns>
                <param name="n">
                    <type>int</type>
                </param>
                <throws>
                    <localType>IllegalAccessException</localType>
                    <type>java.lang.IllegalAccessException</type>
                </throws>
            </method>
            <method line="713" modifiers="public" name="dependOnAncestor" xml:id="j1-384">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="721" modifiers="public" name="dependOnAncestorUnverified" xml:id="j1-385">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="729" modifiers="public" name="dependOnVariables" xml:id="j1-386">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="747" modifiers="public" name="dependOnVariables2" xml:id="j1-387">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="761" modifiers="public" name="dependOnVariables3" xml:id="j1-388">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="775" modifiers="public" name="affect" xml:id="j1-389">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="783" modifiers="public" name="affectNoId" xml:id="j1-390">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="788" modifiers="public" name="reference" xml:id="j1-391">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="796" modifiers="public" name="referenceGenerateId" xml:id="j1-392">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="815" modifiers="public" name="referenceNoId" xml:id="j1-393">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="823" modifiers="public" name="order" xml:id="j1-394">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="834" modifiers="public" name="orderNoId" xml:id="j1-395">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="843" modifiers="public" name="orderNoParentId" xml:id="j1-396">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="854" modifiers="public" name="orderNoParent" xml:id="j1-397">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="862" modifiers="public" name="generateId" xml:id="j1-398">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="867" modifiers="public" name="writeMod" xml:id="j1-399">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>Exception</localType>
                    <type>java.lang.Exception</type>
                </throws>
            </method>
            <method line="901" modifiers="public" name="twoCommitsFail" xml:id="j1-400">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="913" modifiers="public" name="commitNewNode" xml:id="j1-401">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="930" modifiers="public" name="commitExtantNodeEarlierStage" xml:id="j1-402">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
        </class>
        <class fullName="com.ideanest.dscribe.mixt.Mod._ModTest" implName="com.ideanest.dscribe.mixt.Mod$_ModTest" line="945" modifiers="static public" name="_ModTest" xml:id="j1-403">
            <extends>
                <localType>_Test</localType>
                <typeNotResolved>not found</typeNotResolved>
            </extends>
            <method line="947" modifiers="public" name="constructorFromRule" xml:id="j1-404">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="955" modifiers="public" name="constructorFromParentMod" xml:id="j1-405">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="963" modifiers="public" name="createBootstrap" xml:id="j1-406">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="972" modifiers="public" name="bootstrapAppendChild" xml:id="j1-407">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="976" modifiers="public" name="bootstrapNearestAncestorOrSelfImplementing" xml:id="j1-408">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="981" modifiers="public" name="binder" xml:id="j1-409">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="990" modifiers="public" name="bindVariable1" xml:id="j1-410">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="998" modifiers="public" name="bindVariable2" xml:id="j1-411">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1006" modifiers="public" name="variableBindings" xml:id="j1-412">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1011" modifiers="public" name="prepScopeClone" xml:id="j1-413">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1021" modifiers="public" name="workspace" xml:id="j1-414">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1027" modifiers="public" name="references" xml:id="j1-415">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1032" modifiers="public" name="scopeNonNull" xml:id="j1-416">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1041" modifiers="public" name="scopeNull" xml:id="j1-417">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1051" modifiers="public" name="isItselfNearestAncestorImplementing" xml:id="j1-418">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1066" modifiers="public" name="hasNearestAncestorImplementing" xml:id="j1-419">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1079" modifiers="public" name="verifyNoOrder" xml:id="j1-420">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1092" modifiers="public" name="verifyWithOrder" xml:id="j1-421">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1109" modifiers="public" name="analyze" xml:id="j1-422">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1118" modifiers="public" name="deriveChild" xml:id="j1-423">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1129" modifiers="public" name="deriveKeyChild" xml:id="j1-424">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <method line="1142" modifiers="public" name="deriveKeyChildBadKey" xml:id="j1-425">
                <returns>
                    <type>void</type>
                </returns>
            </method>
            <class fullName="com.ideanest.dscribe.mixt.Mod._ModTest.CheckModBuilderParams" implName="com.ideanest.dscribe.mixt.Mod$_ModTest$CheckModBuilderParams" line="1148" modifiers="static private" name="CheckModBuilderParams" xml:id="j1-426">
                <extends>
                    <localType>TypeSafeMatcher</localType>
                    <type>org.hamcrest.TypeSafeMatcher</type>
                </extends>
                <field line="1149" modifiers="final private" name="mod" xml:id="j1-427">
                    <localType>Mod</localType>
                    <type>com.ideanest.dscribe.mixt.Mod</type>
                </field>
                <field line="1150" modifiers="final private" name="block" xml:id="j1-428">
                    <localType>Block</localType>
                    <type>com.ideanest.dscribe.mixt.Block</type>
                </field>
                <field line="1151" modifiers="final private" name="lastBlock" xml:id="j1-429">
                    <type>boolean</type>
                </field>
                <field line="1152" modifiers="final private" name="scope" xml:id="j1-430">
                    <localType>QueryService</localType>
                    <type>org.exist.fluent.QueryService</type>
                </field>
                <constructor line="1153" modifiers="" xml:id="j1-431">
                    <param name="mod">
                        <localType>Mod</localType>
                        <type>com.ideanest.dscribe.mixt.Mod</type>
                    </param>
                    <param name="block">
                        <localType>Block</localType>
                        <type>com.ideanest.dscribe.mixt.Block</type>
                    </param>
                    <param name="lastBlock">
                        <type>boolean</type>
                    </param>
                    <param name="scope">
                        <localType>QueryService</localType>
                        <type>org.exist.fluent.QueryService</type>
                    </param>
                </constructor>
                <method line="1159" modifiers="public" name="matchesSafely" xml:id="j1-432">
                    <returns>
                        <type>boolean</type>
                    </returns>
                    <param name="item">
                        <localType>E</localType>
                        <typeNotResolved>not found</typeNotResolved>
                    </param>
                </method>
                <method line="1162" modifiers="public" name="describeTo" xml:id="j1-433">
                    <returns>
                        <type>void</type>
                    </returns>
                    <param name="description">
                        <localType>Description</localType>
                        <type>org.hamcrest.Description</type>
                    </param>
                </method>
            </class>
            <method line="1167" modifiers="public" name="resolveChildren" xml:id="j1-434">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1184" modifiers="public" name="resolveChildrenLastBlock" xml:id="j1-435">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1198" modifiers="public" name="resolveChildrenKeyBlock" xml:id="j1-436">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1215" modifiers="public" name="restoreChild" xml:id="j1-437">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1231" modifiers="private" name="checkThatNamespaceBindingsAreEmpty" xml:id="j1-438">
                <returns>
                    <localType>Action</localType>
                    <type>org.jmock.api.Action</type>
                </returns>
                <param name="bindings">
                    <localType>NamespaceMap</localType>
                    <type>org.exist.fluent.NamespaceMap</type>
                </param>
            </method>
            <method line="1243" modifiers="public" name="restoreNoReferences" xml:id="j1-439">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1258" modifiers="public" name="restoreWrongStage" xml:id="j1-440">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1267" modifiers="public" name="restoreWithReferences" xml:id="j1-441">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1283" modifiers="public" name="restoreWithBadReferencePath" xml:id="j1-442">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
            <method line="1293" modifiers="public" name="affected" xml:id="j1-443">
                <returns>
                    <type>void</type>
                </returns>
                <throws>
                    <localType>TransformException</localType>
                    <type>com.ideanest.dscribe.mixt.TransformException</type>
                </throws>
            </method>
        </class>
    </class>
</unit>