package org.folio.circulation.domain;

import java.util.Arrays;

public enum LoanAction {
  DECLARED_LOST("declaredLost"),
  RENEWED("renewed"),
  RENEWED_THROUGH_OVERRIDE("renewedThroughOverride"),
  CHECKED_IN("checkedin"),
  CHECKED_OUT("checkedout"),
  CHECKED_OUT_THROUGH_OVERRIDE("checkedOutThroughOverride"),
  RECALLREQUESTED("recallrequested"),
  HOLDREQUESTED("holdrequested"),
  CLAIMED_RETURNED("claimedReturned"),
  MISSING("markedMissing"),
  CLOSED_LOAN("closedLoan"),

  RESOLVE_CLAIM_AS_RETURNED_BY_PATRON("checkedInReturnedByPatron"),
  RESOLVE_CLAIM_AS_FOUND_BY_LIBRARY("checkedInFoundByLibrary");


  private final String value;

  LoanAction(String value) {
    this.value = value;
  }

  public static LoanAction forValue(String property) {
    return Arrays.stream(values())
      .filter(currentEnum -> currentEnum.getValue().equals(property))
      .findFirst()
      .orElse(null);
  }

  public String getValue() {
    return value;
  }
}
