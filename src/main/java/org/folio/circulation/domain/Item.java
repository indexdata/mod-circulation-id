package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.domain.ItemStatus.MISSING;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.representations.ItemProperties.STATUS_PROPERTY;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.storage.mappers.ItemMapper;

import io.vertx.core.json.JsonObject;
import lombok.NonNull;

public class Item {
  private final String id;
  private final JsonObject itemRepresentation;
  @NonNull private final Location location;
  private final LastCheckIn lastCheckIn;
  private final CallNumberComponents callNumberComponents;
  @NonNull private final Location permanentLocation;
  private final ServicePoint inTransitDestinationServicePoint;

  private boolean changed;

  @NonNull private final Holdings holdings;
  @NonNull private final Instance instance;
  @NonNull private final MaterialType materialType;
  @NonNull private final LoanType loanType;
  private final String barcode;
  private final String copyNumber;
  private final String enumeration;
  private final String temporaryLoanTypeId;
  private final String permanentLoanTypeId;

  public static Item from(JsonObject representation) {
    return new ItemMapper().toDomain(representation);
  }
  public Item(String id, JsonObject itemRepresentation, Location effectiveLocation,
    LastCheckIn lastCheckIn, CallNumberComponents callNumberComponents,
    Location permanentLocation, ServicePoint inTransitDestinationServicePoint,
    boolean changed, Holdings holdings, Instance instance,
    MaterialType materialType, LoanType loanType, String barcode,
    String copyNumber, String enumeration, String temporaryLoanTypeId,
    String permanentLoanTypeId) {

    this.id = id;
    this.itemRepresentation = itemRepresentation;
    this.location = effectiveLocation;
    this.lastCheckIn = lastCheckIn;
    this.callNumberComponents = callNumberComponents;
    this.permanentLocation = permanentLocation;
    this.inTransitDestinationServicePoint = inTransitDestinationServicePoint;
    this.changed = changed;
    this.holdings = holdings;
    this.instance = instance;
    this.materialType = materialType;
    this.loanType = loanType;
    this.barcode = barcode;
    this.copyNumber = copyNumber;
    this.enumeration = enumeration;
    this.temporaryLoanTypeId = temporaryLoanTypeId;
    this.permanentLoanTypeId = permanentLoanTypeId;
  }

  public boolean isCheckedOut() {
    return isInStatus(CHECKED_OUT);
  }

  public boolean isClaimedReturned() {
    return isInStatus(CLAIMED_RETURNED);
  }

  public boolean isPaged() {
    return isInStatus(PAGED);
  }

  public boolean isMissing() {
    return isInStatus(MISSING);
  }

  public boolean isAwaitingPickup() {
    return isInStatus(AWAITING_PICKUP);
  }

  public boolean isAvailable() {
    return isInStatus(AVAILABLE);
  }

  private boolean isInTransit() {
    return isInStatus(IN_TRANSIT);
  }

  public boolean isDeclaredLost() {
    return isInStatus(DECLARED_LOST);
  }

  boolean isNotSameStatus(ItemStatus prospectiveStatus) {
    return !isInStatus(prospectiveStatus);
  }

  public boolean isInStatus(ItemStatus status) {
    return getStatus().equals(status);
  }

  public boolean isNotInStatus(ItemStatus status) {
    return !isInStatus(status);
  }

  public boolean hasChanged() {
    return changed;
  }

  public String getTitle() {
    return instance.getTitle();
  }

  public Stream<String> getContributorNames() {
    return instance.getContributorNames();
  }

  public String getPrimaryContributorName() {
    return instance.getPrimaryContributorName();
  }

  public Stream<Identifier> getIdentifiers() {
    return instance.getIdentifiers().stream();
  }

  public String getBarcode() {
    return barcode;
  }

  public String getItemId() {
    return id;
  }

  public String getHoldingsRecordId() {
    return holdings.getId();
  }

  public String getInstanceId() {
    return holdings.getInstanceId();
  }

  public Stream<Publication> getPublication() {
    return instance.getPublication().stream();
  }

  public Stream<String> getEditions() {
    return instance.getEditions().stream();
  }

  public String getCallNumber() {
    return Optional.ofNullable(callNumberComponents)
      .map(CallNumberComponents::getCallNumber)
      .orElse(null);
  }

  public UUID getLastCheckInServicePointId() {
    return Optional.ofNullable(lastCheckIn)
      .map(LastCheckIn::getServicePointId)
      .orElse(null);
  }

  public CallNumberComponents getCallNumberComponents() {
    return callNumberComponents;
  }

  public ItemStatus getStatus() {
    return ItemStatus.from(getStatusName(), getStatusDate());
  }

  public String getStatusName() {
    return getNestedStringProperty(itemRepresentation, STATUS_PROPERTY, "name");
  }

  private String getStatusDate() {
    return getNestedStringProperty(itemRepresentation, STATUS_PROPERTY, "date");
  }

  public Location getLocation() {
    return location;
  }

  public Location getPermanentLocation() {
    return permanentLocation;
  }

  public MaterialType getMaterialType() {
    return materialType;
  }

  public String getMaterialTypeName() {
    return materialType.getName();
  }

  public String getCopyNumber() {
    return firstNonBlank(copyNumber, holdings.getCopyNumber());
  }

  public String getMaterialTypeId() {
    return materialType.getId();
  }

  public String getEffectiveLocationId() {
    return location.getId();
  }

  public String getEnumeration() {
    return enumeration;
  }

  public String getInTransitDestinationServicePointId() {
    if (inTransitDestinationServicePoint == null) {
      return null;
    }
    else {
      return inTransitDestinationServicePoint.getId();
    }
  }

  public ServicePoint getInTransitDestinationServicePoint() {
    return inTransitDestinationServicePoint;
  }

  public String getVolume() {
    return getProperty(itemRepresentation, "volume");
  }

  public String getChronology() {
    return getProperty(itemRepresentation, "chronology");
  }

  public String getNumberOfPieces() {
    return getProperty(itemRepresentation, "numberOfPieces");
  }

  public String getDescriptionOfPieces() {
    return getProperty(itemRepresentation, "descriptionOfPieces");
  }

  public List<String> getYearCaption() {
    return toStream(itemRepresentation, "yearCaption")
      .collect(Collectors.toList());
  }

  private ServicePoint getPrimaryServicePoint() {
    return location.getPrimaryServicePoint();
  }

  public String getLoanTypeId() {
    return firstNonBlank(temporaryLoanTypeId, permanentLoanTypeId);
  }

  public String getLoanTypeName() {
    return loanType.getName();
  }

  public Item changeStatus(ItemStatus newStatus) {
    if (isNotSameStatus(newStatus)) {
      changed = true;
    }

    write(itemRepresentation, STATUS_PROPERTY,
      new JsonObject().put("name", newStatus.getValue()));

    //TODO: Remove this hack to remove destination service point
    // needs refactoring of how in transit for pickup is done
    if(!isInTransit()) {
      return removeDestination();
    }
    else {
      return this;
    }
  }


  Item available() {
    return changeStatus(AVAILABLE)
      .removeDestination();
  }

  Item inTransitToHome() {
    return changeStatus(IN_TRANSIT)
      .withInTransitDestinationServicePoint(getPrimaryServicePoint());
  }

  Item inTransitToServicePoint(UUID destinationServicePointId) {
    return changeStatus(IN_TRANSIT)
      .changeDestination(destinationServicePointId);
  }

  public Item updateDestinationServicePoint(ServicePoint servicePoint) {
    return withInTransitDestinationServicePoint(servicePoint);
  }

  public Item updateLastCheckInServicePoint(ServicePoint servicePoint) {
    if (lastCheckIn != null) {
      lastCheckIn.setServicePoint(servicePoint);
    }
    return this;
  }

  private Item changeDestination(@NonNull UUID destinationServicePointId) {
    return withInTransitDestinationServicePoint(
      ServicePoint.unknown(destinationServicePointId.toString()));
  }

  private Item removeDestination() {
    return withInTransitDestinationServicePoint(null);
  }

  public boolean isNotFound() {
    return !isFound();
  }

  public boolean isFound() {
    return itemRepresentation != null;
  }

  public LastCheckIn getLastCheckIn() {
    return lastCheckIn;
  }

  public String getPermanentLocationId() {
    return firstNonBlank(permanentLocation.getId(), holdings.getPermanentLocationId());
  }

  public Item withLocation(Location newLocation) {
    return new Item(this.id, this.itemRepresentation, newLocation,
      this.lastCheckIn, this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, this.loanType, this.barcode,
      this.copyNumber, this.enumeration, this.temporaryLoanTypeId,
      this.permanentLoanTypeId);
  }

  public Item withMaterialType(@NonNull MaterialType materialType) {
    return new Item(this.id, this.itemRepresentation, this.location,
      this.lastCheckIn, this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, materialType, this.loanType, this.barcode,
      this.copyNumber, this.enumeration, this.temporaryLoanTypeId,
      this.permanentLoanTypeId);
  }

  public Item withHoldings(@NonNull Holdings holdings) {
    return new Item(this.id, this.itemRepresentation, this.location,
      this.lastCheckIn, this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, holdings,
      this.instance, this.materialType, this.loanType, this.barcode,
      this.copyNumber, this.enumeration, this.temporaryLoanTypeId,
      this.permanentLoanTypeId);
  }

  public Item withInstance(@NonNull Instance instance) {
    return new Item(this.id, this.itemRepresentation, this.location,
      this.lastCheckIn, this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      instance, this.materialType, this.loanType, this.barcode, this.copyNumber,
      this.enumeration, this.temporaryLoanTypeId, this.permanentLoanTypeId);
  }

  public Item withLoanType(@NonNull LoanType loanType) {
    return new Item(this.id, this.itemRepresentation, this.location,
      this.lastCheckIn, this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, loanType, this.barcode, this.copyNumber,
      this.enumeration, this.temporaryLoanTypeId, this.permanentLoanTypeId);
  }

  public Item withLastCheckIn(@NonNull LastCheckIn lastCheckIn) {
    return new Item(this.id, this.itemRepresentation, this.location,
      lastCheckIn, this.callNumberComponents, this.permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, this.loanType, this.barcode,
      this.copyNumber, this.enumeration, this.temporaryLoanTypeId,
      this.permanentLoanTypeId);
  }

  public Item withPermanentLocation(Location permanentLocation) {
    return new Item(this.id, this.itemRepresentation, this.location,
      this.lastCheckIn, this.callNumberComponents, permanentLocation,
      this.inTransitDestinationServicePoint, this.changed, this.holdings,
      this.instance, this.materialType, this.loanType, this.barcode,
      this.copyNumber, this.enumeration, this.temporaryLoanTypeId,
      this.permanentLoanTypeId);
  }

  public Item withInTransitDestinationServicePoint(ServicePoint servicePoint) {
    return new Item(this.id, this.itemRepresentation, this.location,
      this.lastCheckIn, this.callNumberComponents, this.permanentLocation,
      servicePoint, this.changed, this.holdings, this.instance,
      this.materialType, this.loanType, this.barcode, this.copyNumber,
      this.enumeration, this.temporaryLoanTypeId, this.permanentLoanTypeId);
  }
}
