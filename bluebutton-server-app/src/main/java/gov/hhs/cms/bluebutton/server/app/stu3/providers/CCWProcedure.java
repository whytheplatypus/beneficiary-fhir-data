package gov.hhs.cms.bluebutton.server.app.stu3.providers;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Models a ccwProcedure code entry in a claim.
 */
final class CCWProcedure extends IcdCode {

	private LocalDate procedureDate;

	/**
	 * Constructs a new {@link CCWProcedure}.
	 * 
	 * @param icdCode
	 *            the ICD code of the {@link CCWProcedure}, if any
	 * @param icdVersionCode
	 *            the CCW encoding (per <a href=
	 *            "https://www.ccwdata.org/cs/groups/public/documents/datadictionary/prncpal_dgns_vrsn_cd.txt">
	 *            CCW Data Dictionary: PRNCPAL_DGNS_VRSN_CD</a> and other
	 *            similar fields) of the code's ICD version, if any
	 * @param procedureDate
	 *            the value to use for {@link #getProcedureDate()}
	 */
	public CCWProcedure(Optional<String> icdCode, Optional<Character> icdVersionCode,
			Optional<LocalDate> procedureDate) {
		super(icdCode, icdVersionCode);
		Objects.requireNonNull(icdCode);
		Objects.requireNonNull(icdVersionCode);
		Objects.requireNonNull(procedureDate);

		this.procedureDate = procedureDate.get();

	}

	/**
	 * @return the ICD procedure date
	 */
	public LocalDate getProcedureDate() {
		return procedureDate;
	}

	/**
	 * Constructs a new {@link CCWProcedure}, if the specified
	 * <code>icdCode</code> is present.
	 * 
	 * @param icdCode
	 *            the ICD code of the {@link CCWProcedure}, if any
	 * @param icdVersionCode
	 *            the CCW encoding (per <a href=
	 *            "https://www.ccwdata.org/cs/groups/public/documents/datadictionary/prncpal_dgns_vrsn_cd.txt">
	 *            CCW Data Dictionary: PRNCPAL_DGNS_VRSN_CD</a> and other
	 *            similar fields) of the code's ICD version, if any
	 * @param procedureDate
	 * @return the new {@link CCWProcedure}, or {@link Optional#empty()} if no
	 *         <code>icdCode</code> was present
	 */
	static Optional<CCWProcedure> from(Optional<String> icdCode, Optional<Character> icdVersionCode,
			Optional<LocalDate> procedureDate) {
		if (!icdCode.isPresent())
			return Optional.empty();
		return Optional.of(new CCWProcedure(icdCode, icdVersionCode, procedureDate));
	}

}

