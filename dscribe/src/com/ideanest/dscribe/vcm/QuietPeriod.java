package com.ideanest.dscribe.vcm;

import java.text.ParseException;
import java.util.*;

import javax.xml.datatype.Duration;

import org.apache.log4j.Logger;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.Cycle;

/**
 * Keeps track of a quiet period and decides when changes are too recent, postponing
 * the job as necessary.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class QuietPeriod {
	private static final Logger LOG = Logger.getLogger(QuietPeriod.class);
	private static final Duration ONE_HOUR = DataUtils.datatypeFactory().newDuration("PT1H");
	private static final Duration ONE_SECOND = DataUtils.datatypeFactory().newDuration("PT1S");
	
	private Duration quietPeriod;
	private Duration precision = DataUtils.datatypeFactory().newDuration(1);	// 1ms by default, finest precision possible
	private final Date scanStart, jobStart;
	private Date firstChange;
	private final SortedSet<Date> changeDates = new TreeSet<Date>();
	private final SortedSet<Date> interestDates = new TreeSet<Date>();

	/**
	 * Create a new quiet period scanner.
	 *
	 * @param job the job we're scanning for, used to retrieve the upper scan bound
	 * @param taskDef the task definition that includes the quiet period attribute to parse
	 * @param scanStart the date from which we'll be scanning for changes, or <code>null</code> to automatically start at upper bound minus quiet period duration
	 * @throws ParseException
	 */
	public QuietPeriod(Cycle job, Node taskDef, Date scanStart) throws ParseException {
		this.quietPeriod = initQuietPeriod(taskDef);
		this.jobStart = job.workspace(new NamespaceMap("", Namespace.NOTES)).query().single("/notes/@started").instantValue();
		this.scanStart = initScanStart(scanStart);
	}
	
	private Date initScanStart(Date proposedStart) {
		// note that we don't check whether the proposed start is too close to the job
		// start; if it is closer than the quiet period, then we simply won't find any
		// valid quiet ranges to propose checkpoints on
		if (proposedStart == null) {
			Date maxStart = (Date) jobStart.clone();
			quietPeriod.negate().addTo(maxStart);
			return maxStart;
		}
		return (Date) proposedStart.clone();
	}

	private Duration initQuietPeriod(Node taskDef) {
		Duration qp = taskDef.query().single("@quiet-period").durationValue();
		if (qp.getSign() == -1) {
			LOG.error("negative quiet period; using absolute value");
			qp = qp.negate();
		}
		if (qp.isLongerThan(ONE_HOUR)) LOG.warn("quiet period unusually long at " + qp);
		if (qp.isShorterThan(ONE_SECOND)) LOG.warn("quiet period unusually short at " + qp);
		return qp;
	}
	
	public void setPrecision(Duration precision) {
		this.precision = precision;
		Duration precisionTimesTwo = precision.add(precision);
		if (quietPeriod.isShorterThan(precisionTimesTwo)) {
			LOG.warn("quiet period of " + quietPeriod + " shorter than available precision of " + precision + "; setting to " + precisionTimesTwo);
			quietPeriod = precisionTimesTwo;
		}
	}

	public Date scanStart() {
		return (Date) scanStart.clone();
	}
	
	public Duration duration() {
		return quietPeriod;
	}
	
	public void addChangePointBeforeScanStart() {
		firstChange = scanStart;
	}
	
	public void addChangePoint(Date changeDate) {
		if (changeDate.after(scanStart)) {
			changeDate = (Date) changeDate.clone();
			changeDates.add(changeDate);
			if (firstChange == null || changeDate.before(firstChange)) firstChange = changeDate;
		}
	}
	
	public void addInterestPoint(Date interestDate) {
		if (interestDate.after(scanStart)) interestDates.add((Date) interestDate.clone());
	}
	
	public void recordProposal(ElementBuilder<Node> builder) {
		if (firstChange == null && !changeDates.isEmpty()) firstChange = changeDates.first();
		
		builder.namespace("", Namespace.VCM).elem("checkpoint-proposal");
		
		// 1. if last change is within quiet period window, suggest postponement
		if (!changeDates.isEmpty()) {
			Date lastChange = (Date) changeDates.last().clone();
			quietPeriod.addTo(lastChange);
			builder.attrIf(lastChange.after(jobStart), "postpone-until", lastChange);
		}

		// 2. find quiet ranges
		assert changeDates.isEmpty() || changeDates.first().after(scanStart);
		Date fromDate = scanStart;
		for (Date changeDate : changeDates) {
			recordRange(builder, fromDate, changeDate);
			fromDate = changeDate;
		}
		recordRange(builder, fromDate, jobStart);
		
		// 3. commit and tag last range as interesting if at least one change noted
		Node proposal = builder.end("checkpoint-proposal").commit();
		if (firstChange != null) {
			Node lastChangedRange = proposal.query().namespace("", Namespace.VCM).optional("let $firstChange := $_1 cast as xs:dateTime return //checkpoint-range[(@from cast as xs:dateTime) ge $firstChange][last()]", firstChange).node();
			if (lastChangedRange.extant()) lastChangedRange.update().attr("interesting", "true").commit();
		}
	}
	
	private void recordRange(ElementBuilder<?> builder, Date fromDate, Date changeDate) {
		Duration window = DataUtils.datatypeFactory().newDuration(changeDate.getTime() - fromDate.getTime());
		if (window.isLongerThan(quietPeriod) || window.equals(quietPeriod)) {
			Date adjustedFromDate = (Date) fromDate.clone();
			precision.addTo(adjustedFromDate);
			builder.elem("checkpoint-range")
				.attr("from", adjustedFromDate)
				.attr("to", changeDate)
				.attrIf(
						!interestDates.subSet(fromDate, changeDate).isEmpty()
						&& firstChange != null
						&& !fromDate.before(firstChange),
						"interesting", "true")
				.end("checkpoint-range");
		}
	}

	public int numViolations() {
		return changeDates.size();
	}

}
