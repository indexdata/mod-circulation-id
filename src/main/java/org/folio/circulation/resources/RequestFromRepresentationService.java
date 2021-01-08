package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_ITEM;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_LOAN;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_PROXY_USER;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_REQUEST_QUEUE;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_SERVICE_POINT;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.error.CirculationError.FAILED_TO_FETCH_USER_FOR_LOAN;
import static org.folio.circulation.resources.error.CirculationError.INVALID_PICKUP_SERVICE_POINT;
import static org.folio.circulation.resources.error.CirculationError.INVALID_PROXY_RELATIONSHIP;
import static org.folio.circulation.resources.error.CirculationError.INVALID_STATUS;
import static org.folio.circulation.resources.error.CirculationError.INVALID_ITEM_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.error.CirculationErrorHandler;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

class RequestFromRepresentationService {
  private final ItemRepository itemRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ServicePointPickupLocationValidator pickupLocationValidator;
  private final CirculationErrorHandler errorHandler;


  RequestFromRepresentationService(ItemRepository itemRepository,
    RequestQueueRepository requestQueueRepository, UserRepository userRepository,
    LoanRepository loanRepository, ServicePointRepository servicePointRepository,
    ProxyRelationshipValidator proxyRelationshipValidator,
    ServicePointPickupLocationValidator pickupLocationValidator,
    CirculationErrorHandler errorHandler) {

    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.requestQueueRepository = requestQueueRepository;
    this.userRepository = userRepository;
    this.servicePointRepository = servicePointRepository;
    this.proxyRelationshipValidator = proxyRelationshipValidator;
    this.pickupLocationValidator = pickupLocationValidator;
    this.errorHandler = errorHandler;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(JsonObject representation) {
    return completedFuture(succeeded(representation))
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> errorHandler.handle(r, INVALID_STATUS, representation))
      .thenApply(r -> r.next(this::refuseWhenNoItemId))
      .thenApply(r -> errorHandler.handle(r, INVALID_ITEM_ID, representation))
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(Request::from))
      .thenCompose(res -> res.after(request -> itemRepository.fetchFor(request)
        .thenApply(r -> r.map(request::withItem))
        .thenApply(r -> errorHandler.handle(r, FAILED_TO_FETCH_ITEM, request))))
      .thenCompose(res -> res.after(request -> userRepository.getUser(request)
        .thenApply(r -> r.map(request::withRequester))
        .thenApply(r -> errorHandler.handle(r, FAILED_TO_FETCH_USER, request))))
      .thenCompose(res -> res.after(request -> userRepository.getProxyUser(request)
        .thenApply(r -> r.map(request::withProxy))
        .thenApply(r -> errorHandler.handle(r, FAILED_TO_FETCH_PROXY_USER, request))))
      .thenCompose(res -> res.after(request -> servicePointRepository.getServicePointForRequest(request)
        .thenApply(r -> r.map(request::withPickupServicePoint))
        .thenApply(r -> errorHandler.handle(r, FAILED_TO_FETCH_SERVICE_POINT, request))))
      .thenCompose(res -> res.after(request -> loanRepository.findOpenLoanForRequest(request)
        .thenApply(r -> r.map(request::withLoan))
        .thenApply(r -> errorHandler.handle(r, FAILED_TO_FETCH_LOAN, request))))
      .thenCompose(res -> res.after(request -> getUserForExistingLoan(request)
        .thenApply(r -> r.map(user -> addUserToLoan(request, user)))
        .thenApply(r -> errorHandler.handle(r, FAILED_TO_FETCH_USER_FOR_LOAN, request))))
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenCompose(res -> res.after(records -> requestQueueRepository.get(records)
        .thenApply(r -> r.map(records::withRequestQueue))
        .thenApply(r -> errorHandler.handle(r, FAILED_TO_FETCH_REQUEST_QUEUE, records))))
      .thenCompose(res -> res.after(records -> proxyRelationshipValidator.refuseWhenInvalid(records)
        .thenApply(r -> errorHandler.handle(r, INVALID_PROXY_RELATIONSHIP, records))))
      .thenApply(r -> r.next(records -> pickupLocationValidator.refuseInvalidPickupServicePoint(records)
        .mapFailure(error -> errorHandler.handle(error, INVALID_PICKUP_SERVICE_POINT, records))));
  }

  private CompletableFuture<Result<User>> getUserForExistingLoan(Request request) {
    Loan loan = request.getLoan();

    if (loan == null) {
      return ofAsync(() -> null);
    }

    return userRepository.getUser(loan.getUserId());
  }

  private Request addUserToLoan(Request request, User user) {
    if (request.getLoan() == null) {
      return request;
    }
    return request.withLoan(request.getLoan().withUser(user));
  }

  private Result<JsonObject> validateStatus(JsonObject representation) {
    RequestStatus status = RequestStatus.from(representation);

    if (!status.isValid()) {
      return failed(new BadRequestFailure(RequestStatus.invalidStatusErrorMessage()));
    }
    else {
      status.writeTo(representation);
      return succeeded(representation);
    }
  }

  private Result<JsonObject> refuseWhenNoItemId(JsonObject representation) {
    String itemId = getProperty(representation, ITEM_ID);

    if (isBlank(itemId)) {
      return failedValidation("Cannot create a request with no item ID", "itemId", itemId);
    }
    else {
      return of(() -> representation);
    }
  }

  private JsonObject removeRelatedRecordInformation(JsonObject representation) {
    representation.remove("item");
    representation.remove("requester");
    representation.remove("proxy");
    representation.remove("loan");
    representation.remove("pickupServicePoint");
    representation.remove("deliveryAddress");

    return representation;
  }
}
