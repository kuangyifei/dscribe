package com.ideanest.dscribe.vcm;

import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.jmock.Mock;
import org.jmock.core.Verifiable;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.Cycle;
import com.ideanest.dscribe.job.TaskBase;

/**
 * Reviews proposed checkpoint ranges and decides on the checkpoint to use to update
 * checked-out artifacts.  If no agreement is possible, postpones or abandons the job run.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class CheckpointMediator extends TaskBase {
	private static final Logger LOG = Logger.getLogger(CheckpointMediator.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Namespace.VCM,
			"vcm", Namespace.VCM,
			"notes", Namespace.NOTES
	);
	
	private Folder workspace, prevspace;
	
	@Override
	protected void init(Node taskDef) throws ParseException {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
	}
	
	@Phase
	public void agree() {
		if (workspace.query().exists("/notes:notes/vcm:checkpoint")) {
			LOG.error("checkpoint already set before mediation; deleting and recomputing");
			workspace.query().all("/notes:notes/vcm:checkpoint").deleteAllNodes();
		}
		
		Date checkpoint = workspace.query()
		.let("$workspace", workspace)
		.let("$prevspace", prevspace)
		.optional(
				"let \n" +
				"	$timestamp := xs:dateTime($workspace/notes:notes/@started), \n" +
				"	$lcs := $prevspace/notes:notes/vcm:checkpoint/@at, \n" +
				"	$lastcheckpoint := if (empty($lcs)) then () else xs:dateTime($lcs), \n" +
				"	$lower_bounds := distinct-values(for $date in //checkpoint-range/@from return xs:dateTime($date)), \n" +
				"	$upper_bounds := distinct-values(for $date in //checkpoint-range/@to return xs:dateTime($date)) \n" +
				"return min( \n" +
				"	for $interest in //checkpoint-range[@interesting='true'] \n" +
				"	let \n" +
				"		$ilb := max(($lastcheckpoint, xs:dateTime($interest/@from))), \n" +
				"		$iub := min(($timestamp, xs:dateTime($interest/@to))) \n" +
				"	return min( (\n" +
				"			for $bound in ($ilb, $lower_bounds) \n" +
				"			where \n" +
				"				$bound ge $ilb and $bound lt $iub and \n" +
				"				(every $proposal in //checkpoint-proposal satisfies \n" +
				"					some $range in $proposal/checkpoint-range satisfies \n" +
				"						$bound ge xs:dateTime($range/@from) and $bound lt xs:dateTime($range/@to) \n" +
				"				) \n" +
				"			return $bound \n" +
				"		, \n" +
				"			for $bound in ($iub, $upper_bounds) \n" +
				"			where \n" +
				"				$bound gt $ilb and $bound le $iub and \n" +
				"				(every $proposal in //checkpoint-proposal satisfies \n" +
				"					some $range in $proposal/checkpoint-range satisfies \n" +
				"						$bound gt xs:dateTime($range/@from) and $bound le xs:dateTime($range/@to) \n" +
				"				) \n" +
				"			return $bound" +
				"	) ) \n" +
				")").instantValue();
		if (checkpoint != null) {
			workspace.query().single("/notes:notes").node().append()
				.elem("vcm:checkpoint").attr("at", checkpoint).end("vcm:checkpoint").commit();
			LOG.debug("mediated checkpoint at " + checkpoint);
		} else {
			Date postdate = workspace.query().optional(
					"max(for $date in //checkpoint-proposal/@postpone-until return xs:dateTime($date))"
					).instantValue();
			if (postdate != null) {
				cycle().postpone(postdate);
			} else {
				cycle().abandon();
			}
		}
		
	}
	
	/**
	 * @deprecated Test class that should not be javadoc'ed.
	 */
	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml")
	public static class Test extends DatabaseTestCase {
		private CheckpointMediator mediator;
		private Mock job;
		@Override
		protected void setUp() {
			super.setUp();
			
			job = mock(Cycle.class);
			mediator = new CheckpointMediator() {
				@Override
				protected Cycle cycle() {
					return (Cycle) job.proxy();
				}
			};
			mediator.workspace = db.createFolder("/workspace");
			mediator.workspace.namespaceBindings().putAll(NAMESPACE_MAPPINGS);
			mediator.prevspace = db.createFolder("/prevspace");
			mediator.prevspace.namespaceBindings().putAll(NAMESPACE_MAPPINGS);
			
			mediator.workspace.documents().build(Name.create("notes"))
				.elem("notes:notes").attr("started", "2004-12-10T10:00:00").end("notes:notes")
				.commit();
			
		}
		
		private void registerVerifyCheckpoint(final Date date) {
			registerToVerify(new Verifiable() {
				public void verify() {
					Date actualDate = mediator.workspace.query().optional("/notes:notes/vcm:checkpoint/@at").instantValue();
					if (date == null) assertNull(actualDate); else assertEquals(date, actualDate);
				}
			});			
			mediator.agree();
		}
		
		protected void expectAbandon() {
			job.expects(never()).method("postpone").withAnyArguments();
			job.expects(once()).method("abandon").withNoArguments();
			registerVerifyCheckpoint(null);
		}
		
		protected void expectPostpone(Date date) {
			job.expects(once()).method("postpone").with(eq(date));
			job.expects(never()).method("abandon").withAnyArguments();
			registerVerifyCheckpoint(null);
		}
		
		protected void expectPostpone(String date) {
			expectPostpone(DataUtils.toDate(DataUtils.datatypeFactory().newXMLGregorianCalendar(date)));
		}
		
		protected void expectCheckpoint(Date date) {
			job.expects(never()).method("postpone").withAnyArguments();
			job.expects(never()).method("abandon").withAnyArguments();
			registerVerifyCheckpoint(date);
		}
		
		protected void expectCheckpoint(String date) {
			expectCheckpoint(DataUtils.toDate(DataUtils.datatypeFactory().newXMLGregorianCalendar(date)));
		}
		
		private static final Pattern RANGE_PROPOSAL = Pattern.compile("from (\\S+) to (\\S+)( interesting)?");
		
		protected void addProposal(String postponeDate, String... ranges) {
			ElementBuilder<?> b = mediator.workspace.documents().build(Name.generate())
				.elem("checkpoint-proposal").attrIf(postponeDate != null, "postpone-until", postponeDate);
			for (String range : ranges) {
				Matcher matcher = RANGE_PROPOSAL.matcher(range);
				if (!matcher.matches()) fail("bad range proposal spec: " + range);
				b.elem("checkpoint-range")
					.attr("from", matcher.group(1)).attr("to", matcher.group(2))
					.attrIf(matcher.group(3) != null, "interesting", "true")
				.end("checkpoint-range");
			}
			b.end("checkpoint-proposal").commit();
		}
		
		public void testEmpty() {
			expectAbandon();
		}
		
		public void testPostpone() {
			addProposal("2004-12-15T05:00:00");
			expectPostpone("2004-12-15T05:00:00");
		}

		public void testCheckpoint1() {
			addProposal(null, "from 2004-12-10T09:00:00 to 2004-12-10T09:05:00 interesting");
			expectCheckpoint("2004-12-10T09:00:00");	// lower bound of only range
		}
		public void testCheckpoint2() {
			addProposal(null, "from 2004-12-10T10:00:00 to 2004-12-10T10:05:00");
			expectAbandon();	// because only range is not interesting
		}
		public void testCheckpoint3() {
			addProposal(null, "from 2004-12-10T10:00:00 to 2004-12-10T10:05:00 interesting");
			expectAbandon();	// because the range is empty once clipped to starting time
		}
		public void testCheckpoint4() {
			addProposal(null,
					"from 2004-12-10T09:00:00 to 2004-12-10T09:05:00 interesting",
					"from 2004-12-10T09:30:00 to 2004-12-10T09:35:00 interesting");
			expectCheckpoint("2004-12-10T09:00:00");	// lower bound of min range
		}
	}
	
}
