<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:java="http://ideanest.com/dscribe/ns/java"
	xmlns:uml="http://ideanest.com/dscribe/ns/uml"
	xmlns:map="http://ideanest.com/dscribe/ns/map">
	
	<xsl:template match='//map:create-diagram'>
		<xsl:result-document href='diagram/{@id}.xml'>
			<uml:diagram id='{@id}' kind='{@kind}'>
				<xsl:apply-templates select='//map:java-element-to-diagram'/>
			</uml:diagram>
		</xsl:result-document>
	</xsl:template>
	
	<xsl:template match='//map:java-element-to-diagram'>
		<xsl:variable name='javaelem' select='/id(@java-element)'/>
		<xsl:choose>
			<xsl:when test='$javaelem/self::java:class'>
				<uml:class depicts='$javaelem/@xml:id'>
					<uml:name><xsl:value-of select='$javaelem/@name'/></uml:name>
					<uml:compartment kind='attribute'>
						<xsl:apply-templates select='$javaelem/java:field'/>
					</uml:compartment>
					<uml:compartment kind='operation'>
						<xsl:apply-templates select='$javaelem/java:method | $javaelem/java:constructor'/>
					</uml:compartment>
					<xsl:apply-templates select='$javaelem/*'/>
				</uml:class>
			</xsl:when>
		</xsl:choose>
	</xsl:template>
</xsl:stylesheet>