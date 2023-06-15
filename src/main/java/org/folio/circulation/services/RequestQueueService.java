package org.folio.circulation.services;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RequestQueueService {
  private static final Logger log = LogManager.getLogger(
    MethodHandles.lookup().lookupClass());
  private final RequestPolicyRepository requestPolicyRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public static RequestQueueService using(Clients clients) {
    return new RequestQueueService(
      new RequestPolicyRepository(clients),
      new LoanPolicyRepository(clients)
    );
  }

  public CompletableFuture<Result<Request>> findRequestFulfillableByItem(Item item,
    RequestQueue requestQueue) {
    log.info("findRequestFulfillableByItem:: requestQueue: {}",requestQueue);

    return findRequestFulfillableByItem(item, requestQueue.fulfillableRequests().iterator());
  }

  private CompletableFuture<Result<Request>> findRequestFulfillableByItem(Item item,
    Iterator<Request> iterator) {

    if (!iterator.hasNext()) {
      return emptyAsync();
    }

    final Request request = iterator.next();

    return isRequestFulfillableByItem(item, request)
      .thenCompose(r -> r.after(whenTrue(ofAsync(request), findRequestFulfillableByItem(item, iterator))));
  }

  public CompletableFuture<Result<Boolean>> isRequestFulfillableByItem(Item item, Request request) {
    switch (request.getRequestLevel()) {
      case ITEM:
        return isItemLevelRequestFulfillableByItem(item, request);
      case TITLE:
        return isTitleLevelRequestFulfillableByItem(item, request);
      default:
        return ofAsync(false);
    }
  }

  private CompletableFuture<Result<Boolean>> isItemLevelRequestFulfillableByItem(Item item,
    Request request) {

    return ofAsync(StringUtils.equals(item.getItemId(), request.getItemId()));
  }

  private CompletableFuture<Result<Boolean>> isTitleLevelRequestFulfillableByItem(Item item,
    Request request) {

    boolean instanceIdsMatch = StringUtils.equals(request.getInstanceId(), item.getInstanceId());

    if (request.isRecall() && request.isNotYetFilled()) {
      return instanceIdsMatch ? isItemRequestableAndLoanable(item, request) : ofAsync(false);
    }

    String requestItemId = request.getItemId();

    return instanceIdsMatch && (requestItemId == null ^ StringUtils.equals(item.getItemId(), requestItemId))
      ? isItemRequestableAndLoanable(item, request)
      : ofAsync(false);
  }

  private CompletableFuture<Result<Boolean>> isItemRequestableAndLoanable(Item item, Request request) {
    return isItemRequestable(item, request)
      .thenCompose(r -> r.after(whenTrue(isItemLoanable(item, request), ofAsync(false))));
  }

  private CompletableFuture<Result<Boolean>> isItemRequestable(Item item, Request request) {
    return Optional.ofNullable(request.getInstanceItemsRequestPolicies())
      .map(policies -> policies.get(item.getItemId()))
      .map(Result::ofAsync)
      .orElseGet(() -> requestPolicyRepository.lookupRequestPolicy(item, request.getRequester()))
      .thenApply(r -> r.map(policy -> policy.allowsType(request.getRequestType())));
  }

  private CompletableFuture<Result<Boolean>> isItemLoanable(Item item, Request request) {
    return loanPolicyRepository.lookupPolicy(item, request.getRequester())
      .thenApply(r -> r.map(LoanPolicy::isLoanable));
  }

  public CompletableFuture<Result<Boolean>> isItemRequestedByAnotherPatron(
    RequestQueue requestQueue, User requester, Item item) {

    return findRequestFulfillableByItem(item, requestQueue)
      .thenApply(r -> r.map(request -> !(request == null || request.isFor(requester))));
  }

  private static <T> Function<Boolean, CompletableFuture<Result<T>>> whenTrue(
    CompletableFuture<Result<T>> action, CompletableFuture<Result<T>> otherwise) {

    return predicate -> isTrue(predicate) ? action : otherwise;
  }
}
