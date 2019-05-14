package org.folio.circulation.domain.notice;

import java.util.Optional;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class NoticeContextUtil {

  private static final String USER = "user";
  private static final String ITEM = "item";
  private static final String REQUEST = "request";
  private static final String LOAN = "loan";

  private static final String UNLIMITED = "unlimited";

  private NoticeContextUtil() {
  }

  public static JsonObject createLoanNoticeContext(Loan loan, LoanPolicy loanPolicy) {
    return createLoanNoticeContext(loan,loanPolicy, DateTimeZone.UTC);
  }

  public static JsonObject createLoanNoticeContext(
    Loan loan, LoanPolicy loanPolicy, DateTimeZone timeZone) {

    return new JsonObject()
      .put(USER, createPatronContext(loan.getUser()))
      .put(ITEM, createItemContext(loan.getItem()))
      .put(LOAN, createLoanContext(loan, loanPolicy, timeZone));
  }

  public static JsonObject createRequestNoticeContext(Request request) {
    JsonObject requestNoticeContext = new JsonObject()
      .put(USER, createPatronContext(request.getRequester()))
      .put(ITEM, createItemContext(request.getItem()))
      .put(REQUEST, createRequestContext(request));

    if (request.getRequestType() == RequestType.RECALL && request.getLoan() != null) {
      requestNoticeContext.put(LOAN, createLoanContext(request.getLoan()));
    }
    return requestNoticeContext;
  }

  public static JsonObject createAvailableNoticeContext(Item item, User user) {
    return new JsonObject()
      .put(USER, createPatronContext(user))
      .put(ITEM, createItemContext(item));
  }

  private static JsonObject createPatronContext(User user) {
    return new JsonObject()
      .put("firstName", user.getFirstName())
      .put("lastName", user.getLastName())
      .put("middleName", user.getMiddleName())
      .put("barcode", user.getBarcode());
  }

  private static JsonObject createItemContext(Item item) {
    return new JsonObject()
      .put("title", item.getTitle())
      .put("barcode", item.getBarcode())
      .put("status", item.getStatus().getValue())
      .put("allContributors", item.getContributorNames())
      .put("callNumber", item.getCallNumber())
      .put("callNumberPrefix", item.getCallNumberPrefix())
      .put("callNumberSuffix", item.getCallNumberSuffix())
      .put("enumeration", item.getEnumeration())
      .put("volume", item.getVolume())
      .put("chronology", item.getChronology())
      .put("materialType", item.getMaterialTypeName())
      .put("copy", item.getCopyNumbers())
      .put("numberOfPieces", item.getNumberOfPieces())
      .put("descriptionOfPieces", item.getDescriptionOfPieces());
  }

  private static JsonObject createRequestContext(Request request) {
    Optional<Request> optionalRequest = Optional.ofNullable(request);
    JsonObject requestContext = new JsonObject();

    optionalRequest
      .map(Request::getPickupServicePoint)
      .map(ServicePoint::getName)
      .ifPresent(value -> requestContext.put("servicePointPickup", value));
    optionalRequest
      .map(Request::getRequestExpirationDate)
      .map(DateTime::toString)
      .ifPresent(value -> requestContext.put("requestExpirationDate", value));
    optionalRequest
      .map(Request::getHoldShelfExpirationDate)
      .map(DateTime::toString)
      .ifPresent(value -> requestContext.put("holdShelfExpirationDate", value));
    optionalRequest
      .map(Request::getCancellationAdditionalInformation)
      .ifPresent(value -> requestContext.put("additionalInfo", value));
    optionalRequest
      .map(Request::getCancellationReasonName)
      .ifPresent(value -> requestContext.put("cancellationReason", value));

    return requestContext;
  }

  private static JsonObject createLoanContext(Loan loan) {
    return createLoanContext(loan, null, DateTimeZone.UTC);
  }

  private static JsonObject createLoanContext(
    Loan loan, LoanPolicy loanPolicy, DateTimeZone timeZone) {

    JsonObject loanContext = new JsonObject();
    loanContext.put("initialBorrowDate", loan.getLoanDate().withZone(timeZone).toString());
    loanContext.put("numberOfRenewalsTaken", loan.getRenewalCount());
    loanContext.put("dueDate", loan.getDueDate().withZone(timeZone).toString());

    if (loanPolicy != null) {
      if (loanPolicy.unlimitedRenewals()) {
        loanContext.put("numberOfRenewalsAllowed", UNLIMITED);
        loanContext.put("numberOfRenewalsRemaining", UNLIMITED);
      } else {
        int renewalLimit = loanPolicy.getRenewalLimit();
        int renewalsRemaining = renewalLimit - loan.getRenewalCount();
        loanContext.put("numberOfRenewalsAllowed", renewalLimit);
        loanContext.put("numberOfRenewalsRemaining", renewalsRemaining);
      }
    }

    return loanContext;
  }
}
