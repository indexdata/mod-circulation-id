package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemReportRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.support.ItemsInTransitReportContext;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

/**
 * Placeholder class which will replace items-in-transit report logic when implemented
 */
@AllArgsConstructor
public class ItemsInTransitReportService {
  private ItemReportRepository itemReportRepository;
  private GetManyRecordsClient loansStorageClient;
  private ServicePointRepository servicePointRepository;
  private GetManyRecordsClient requestsStorageClient;
  private ItemRepository itemRepository;
  private UserRepository userRepository;
  private PatronGroupRepository patronGroupRepository;

  public CompletableFuture<Result<JsonObject>> buildReport() {
    return completedFuture(succeeded(new ItemsInTransitReportContext()))
      .thenCompose(this::fetchItems)
      .thenCompose(this::fetchHoldingsRecords)
      .thenCompose(this::fetchInstances)
      .thenCompose(this::fetchLocations)
      .thenCompose(this::fetchMaterialTypes)
      .thenCompose(this::fetchLoanTypes)
      .thenCompose(this::fetchLoans)
      .thenCompose(this::fetchRequests)
      .thenCompose(this::fetchUsers)
      .thenCompose(this::fetchPatronGroups)
      .thenCompose(r -> r.after(this::fetchServicePoints))
      .thenApply(this::mapToJsonObject);
  }

  private Result<JsonObject> mapToJsonObject(Result<ItemsInTransitReportContext> context) {

    return succeeded(new JsonObject());
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchItems(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchHoldingsRecords(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchInstances(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLocations(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchMaterialTypes(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLoanTypes(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchLoans(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchRequests(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchUsers(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchPatronGroups(
    Result<ItemsInTransitReportContext> context) {

    return completedFuture(context);
  }

  // Needs to fetch all service points for items, loans and requests
  private CompletableFuture<Result<ItemsInTransitReportContext>> fetchServicePoints(
    ItemsInTransitReportContext context) {

    return findServicePointsToFetch(context)
      .after(servicePointsIds -> servicePointRepository.findServicePointsByIds(servicePointsIds))
      .thenApply(mapResult(this::toList))
      .thenApply(mapResult(servicePoints -> toMap(servicePoints, ServicePoint::getId)))
      .thenApply(mapResult(context::withServicePoints));
  }

  private Result<Set<String>> findServicePointsToFetch(ItemsInTransitReportContext context) {^
    return succeeded(concat(
      concat(
        findServicePointsIds(context.getItems().values(),
          Item::getInTransitDestinationServicePointId,
          item -> item.getLastCheckInServicePointId().toString()),
        findServicePointsIds(context.getLoans().values(), Loan::getCheckInServicePointId,
          Loan::getCheckoutServicePointId)
      ),
      findServicePointsIds(context.getRequests().values(), Request::getPickupServicePointId))
      .collect(toSet()));
  }

  private <T> Stream<String> findServicePointsIds(Collection<T> entities,
    Function<T, String> firstServicePointIdFunction,
    Function<T, String> secondServicePointIdFunction) {

    return concat(
      findServicePointsIds(entities, firstServicePointIdFunction),
      findServicePointsIds(entities, secondServicePointIdFunction));
  }

  private <T> Stream<String> findServicePointsIds(Collection<T> entities,
    Function<T, String> servicePointIdFunction) {

    return entities.stream()
      .filter(Objects::nonNull)
      .map(servicePointIdFunction)
      .filter(Objects::nonNull);
  }

  public <T> Map<String, T> toMap(List<T> list, Function<T, String> idMapper) {
    return list.stream()
      .collect(Collectors.toMap(idMapper, identity()));
  }

  private <T> List<T> toList(MultipleRecords<T> records) {
    return new ArrayList<>(records.getRecords());
  }
}
