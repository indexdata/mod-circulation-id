package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.circulation.domain.RequestFulfilmentPreference.DELIVERY;
import static org.folio.circulation.domain.RequestFulfilmentPreference.HOLD_SHELF;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_FILLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_PICKUP_EXPIRED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_UNFILLED;
import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.domain.RequestStatus.OPEN_IN_TRANSIT;
import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;
import static org.folio.circulation.domain.representations.RequestProperties.*;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Request implements ItemRelatedRecord, UserRelatedRecord {
  final private TlrSettingsConfiguration tlrSettingsConfiguration;
  private final JsonObject requestRepresentation;
  private final JsonObject cancellationReasonRepresentation;
  private final Item item;
  private final User requester;
  private final User proxy;
  private final AddressType addressType;

  // Only used for ILT request. For TLR there can be multiple loans.
  private final Loan loan;

  private final ServicePoint pickupServicePoint;

  private boolean changedPosition;
  private Integer previousPosition;
  private boolean changedStatus;

  public static Request from(JsonObject representation) {
    return new Request(null, representation, null, null, null, null, null, null, null, false, null, false);
  }

  public static Request from(TlrSettingsConfiguration tlrSettingsConfiguration,
    JsonObject representation) {

    return new Request(tlrSettingsConfiguration, representation, null, null, null, null, null, null, null, false, null, false);
  }

  public Request withRequestJsonRepresentation(JsonObject representation) {
    return new Request(
      tlrSettingsConfiguration,
      representation,
      cancellationReasonRepresentation,
      getItem(),
      getRequester(),
      getProxy(),
      getAddressType(),
      getLoan(),
      getPickupServicePoint(),
      hasChangedPosition(),
      getPreviousPosition(),
      hasChangedStatus());
  }

  public Request withCancellationReasonJsonRepresentation(JsonObject representation) {
    return new Request(
      tlrSettingsConfiguration,
      requestRepresentation,
      representation,
      getItem(),
      getRequester(),
      getProxy(),
      getAddressType(),
      getLoan(),
      getPickupServicePoint(),
      hasChangedPosition(),
      getPreviousPosition(),
      hasChangedStatus());
  }

  public JsonObject asJson() {
    return requestRepresentation.copy();
  }

  boolean isFulfillable() {
    return getFulfilmentPreference() == HOLD_SHELF || getFulfilmentPreference() == DELIVERY;
  }

  public boolean isOpen() {
    return isAwaitingPickup() || isNotYetFilled() || isInTransit();
  }

  private boolean isInTransit() {
    return getStatus() == OPEN_IN_TRANSIT;
  }

  private boolean isNotYetFilled() {
    return getStatus() == OPEN_NOT_YET_FILLED;
  }

  boolean isCancelled() {
    return getStatus() == CLOSED_CANCELLED;
  }

  private boolean isFulfilled() {
    return getStatus() == CLOSED_FILLED;
  }

  private boolean isUnfilled() {
    return getStatus() == CLOSED_UNFILLED;
  }

  private boolean isPickupExpired() {
    return getStatus() == CLOSED_PICKUP_EXPIRED;
  }

  public boolean isClosed() {
    return isCancelled() || isFulfilled() || isUnfilled() || isPickupExpired();
  }

  public boolean isClosedExceptPickupExpired() {
    return isClosed() && !isPickupExpired();
  }

  public boolean isAwaitingPickup() {
    return getStatus() == OPEN_AWAITING_PICKUP;
  }

  boolean isFor(User user) {
    return StringUtils.equals(getUserId(), user.getId());
  }

  public String getInstanceId() {
    return requestRepresentation.getString(INSTANCE_ID);
  }

  @Override
  public String getItemId() {
    return requestRepresentation.getString(ITEM_ID);
  }

  public Request withItem(Item newItem) {
    // NOTE: this is null in RequestsAPIUpdatingTests.replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist test
    if (newItem != null && newItem.getItemId() != null) {
      requestRepresentation.put(ITEM_ID, newItem.getItemId());
    }
    else {
      requestRepresentation.put(ITEM_ID, null);
    }

    return new Request(tlrSettingsConfiguration, requestRepresentation,
      cancellationReasonRepresentation, newItem, requester, proxy, addressType,
      loan == null ? null : loan.withItem(newItem), pickupServicePoint, changedPosition,
      previousPosition, changedStatus);
  }

  public Request withRequester(User newRequester) {
    return new Request(tlrSettingsConfiguration, requestRepresentation,
      cancellationReasonRepresentation, item, newRequester, proxy, addressType, loan,
      pickupServicePoint, changedPosition, previousPosition, changedStatus);
  }

  public Request withProxy(User newProxy) {
    return new Request(tlrSettingsConfiguration, requestRepresentation,
      cancellationReasonRepresentation, item, requester, newProxy, addressType, loan,
      pickupServicePoint, changedPosition, previousPosition, changedStatus);
  }

  public Request withAddressType(AddressType addressType) {
    return new Request(tlrSettingsConfiguration, requestRepresentation,
      cancellationReasonRepresentation, item, requester, proxy, addressType, loan,
      pickupServicePoint, changedPosition, previousPosition, changedStatus);
  }

  public Request withLoan(Loan newLoan) {
    return new Request(tlrSettingsConfiguration, requestRepresentation,
      cancellationReasonRepresentation, item, requester, proxy, addressType, newLoan,
      pickupServicePoint, changedPosition, previousPosition, changedStatus);
  }

  public Request withPickupServicePoint(ServicePoint newPickupServicePoint) {
    return new Request(tlrSettingsConfiguration, requestRepresentation, cancellationReasonRepresentation, item, requester, proxy, addressType, loan,
      newPickupServicePoint, changedPosition, previousPosition, changedStatus);
  }

  @Override
  public String getUserId() {
    return requestRepresentation.getString("requesterId");
  }

  @Override
  public String getProxyUserId() {
    return requestRepresentation.getString("proxyUserId");
  }

  private String getFulfilmentPreferenceName() {
    return requestRepresentation.getString("fulfilmentPreference");
  }

  public RequestFulfilmentPreference getFulfilmentPreference() {
    return RequestFulfilmentPreference.from(getFulfilmentPreferenceName());
  }

  public String getId() {
    return requestRepresentation.getString("id");
  }

  public RequestLevel getRequestLevel() {
    return RequestLevel.from(getProperty(requestRepresentation, REQUEST_LEVEL));
  }

  public RequestType getRequestType() {
    return RequestType.from(getProperty(requestRepresentation, REQUEST_TYPE));
  }

  boolean allowedForItem() {
    return RequestTypeItemStatusWhiteList.canCreateRequestForItem(getItem().getStatus(), getRequestType());
  }

  LoanAction actionOnCreateOrUpdate() {
    return getRequestType().toLoanAction();
  }

  public RequestStatus getStatus() {
    return RequestStatus.from(requestRepresentation.getString(STATUS));
  }

  void changeStatus(RequestStatus newStatus) {
    if (getStatus() != newStatus) {
      newStatus.writeTo(requestRepresentation);
      changedStatus = true;
    }
  }

  Request withRequestType(RequestType type) {
    requestRepresentation.put(REQUEST_TYPE, type.getValue());
    return this;
  }

  public TlrSettingsConfiguration getTlrSettingsConfiguration() {
    return tlrSettingsConfiguration;
  }

  public Item getItem() {
    return item;
  }

  public Loan getLoan() {
    return loan;
  }

  public User getRequester() {
    return requester;
  }

  public JsonObject getRequesterFromRepresentation() {
    return requestRepresentation.getJsonObject("requester");
  }

  public String getRequesterBarcode() {
    return getRequesterFromRepresentation().getString("barcode", EMPTY);
  }

  public String getRequesterId() {
    return requestRepresentation.getString("requesterId", EMPTY);
  }

  public User getProxy() {
    return proxy;
  }

  public AddressType getAddressType() {
    return addressType;
  }

  public String getPickupServicePointId() {
    return requestRepresentation.getString("pickupServicePointId");
  }

  public ServicePoint getPickupServicePoint() {
    return pickupServicePoint;
  }

  void changePosition(Integer newPosition) {
    Integer prevPosition = getPosition();
    if (!Objects.equals(prevPosition, newPosition)) {
      previousPosition = prevPosition;
      write(requestRepresentation, POSITION, newPosition);
      changedPosition = true;
    }
  }

  void removePosition() {
    previousPosition = getPosition();
    requestRepresentation.remove(POSITION);
    changedPosition = true;
  }

  public Integer getPosition() {
    return getIntegerProperty(requestRepresentation, POSITION, null);
  }

  public boolean hasChangedPosition() {
    return changedPosition;
  }

  public Integer getPreviousPosition() {
    return previousPosition;
  }

  ItemStatus checkedInItemStatus() {
    return getFulfilmentPreference().toCheckedInItemStatus();
  }

  public String getDeliveryAddressTypeId() {
    return requestRepresentation.getString("deliveryAddressTypeId");
  }

  void changeHoldShelfExpirationDate(ZonedDateTime holdShelfExpirationDate) {
    write(requestRepresentation, HOLD_SHELF_EXPIRATION_DATE,
      holdShelfExpirationDate);
  }

  void removeHoldShelfExpirationDate() {
    requestRepresentation.remove(HOLD_SHELF_EXPIRATION_DATE);
  }

  public ZonedDateTime getRequestDate() {
    return getDateTimeProperty(requestRepresentation, REQUEST_DATE);
  }

  public ZonedDateTime getHoldShelfExpirationDate() {
    return getDateTimeProperty(requestRepresentation, HOLD_SHELF_EXPIRATION_DATE);
  }

  public ZonedDateTime getRequestExpirationDate() {
    return getDateTimeProperty(requestRepresentation, REQUEST_EXPIRATION_DATE);
  }

  public String getCancellationAdditionalInformation() {
    return getProperty(requestRepresentation, CANCELLATION_ADDITIONAL_INFORMATION);
  }

  public String getCancellationReasonId() {
    return getProperty(requestRepresentation, CANCELLATION_REASON_ID);
  }

  public String getCancellationReasonName() {
    return getProperty(cancellationReasonRepresentation, CANCELLATION_REASON_NAME);
  }

  public String getCancellationReasonPublicDescription() {
    return getProperty(cancellationReasonRepresentation, CANCELLATION_REASON_PUBLIC_DESCRIPTION);
  }

  public Request copy() {
    return withRequestJsonRepresentation(requestRepresentation.copy());
  }

  public String getPatronComments() {
    return getProperty(requestRepresentation, "patronComments");
  }

  public Request truncateRequestExpirationDateToTheEndOfTheDay(ZoneId zone) {
    ZonedDateTime requestExpirationDate = getRequestExpirationDate();
    if (requestExpirationDate != null) {
      final ZonedDateTime dateTime = atEndOfDay(requestExpirationDate, zone);
      write(requestRepresentation, REQUEST_EXPIRATION_DATE, dateTime);
    }
    return this;
  }

  public boolean hasTopPriority() {
    return Integer.valueOf(1).equals(getPosition());
  }

  public boolean hasChangedStatus() {
    return changedStatus;
  }

  public boolean canBeFulfilledByItem(Item item) {
    if (getRequestLevel() == RequestLevel.TITLE) {
      String itemInstanceId = item.getInstanceId();
      String requestInstanceId = this.getInstanceId();

      return itemInstanceId != null && itemInstanceId.equals(requestInstanceId);
    }
    else if (getRequestLevel() == RequestLevel.ITEM) {
      String itemId = item.getItemId();
      String requestItemId = this.getItemId();

      return itemId != null && itemId.equals(requestItemId);
    }

    return false;
  }
}
