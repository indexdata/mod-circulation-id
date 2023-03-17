package org.folio.circulation.domain;

import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_UPDATED;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.validation.ClosedRequestValidator;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.resources.RequestNoticeSender;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;

public class UpdateRequestService {
  private final RequestRepository requestRepository;
  private final UpdateRequestQueue updateRequestQueue;
  private final ClosedRequestValidator closedRequestValidator;
  private final RequestNoticeSender requestNoticeSender;
  private final UpdateItem updateItem;
  private final EventPublisher eventPublisher;

  public UpdateRequestService(RequestRepository requestRepository,
    UpdateRequestQueue updateRequestQueue, ClosedRequestValidator closedRequestValidator,
    RequestNoticeSender requestNoticeSender, UpdateItem updateItem, EventPublisher eventPublisher) {

    this.requestRepository = requestRepository;
    this.updateRequestQueue = updateRequestQueue;
    this.closedRequestValidator = closedRequestValidator;
    this.requestNoticeSender = requestNoticeSender;
    this.updateItem = updateItem;
    this.eventPublisher = eventPublisher;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> replaceRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    Request updated = requestAndRelatedRecords.getRequest();

    return requestRepository.getById(updated.getId())
      .thenApply(originalRequest -> refuseWhenPatronCommentChanged(updated, originalRequest))
      .thenCompose(original -> original.after(o -> closedRequestValidator.refuseWhenAlreadyClosed(requestAndRelatedRecords)
        .thenApply(r -> r.next(this::removeRequestQueuePositionWhenCancelled))
        .thenApply(r -> r.next(this::unsetDueDateChangeByRecallFlag))
        .thenComposeAsync(r -> r.after(requestRepository::update))
        .thenComposeAsync(r -> r.after(updateRequestQueue::onCancellation))
        .thenComposeAsync(r -> r.after(updateItem::onRequestCreateOrUpdate))
        .thenApplyAsync(r -> r.map(p -> eventPublisher.publishLogRecordAsync(p, o, REQUEST_UPDATED)))
        .thenApply(r -> r.next(requestNoticeSender::sendNoticeOnRequestUpdated))));
  }

  private Result<Request> refuseWhenPatronCommentChanged(
    Request updated, Result<Request> originalRequestResult) {

    return originalRequestResult.failWhen(
      original -> succeeded(!Objects.equals(updated.getPatronComments(), original.getPatronComments())),
      original -> singleValidationError("Patron comments are not allowed to change",
        "existingPatronComments", original.getPatronComments())
    );
  }

  private Result<RequestAndRelatedRecords> removeRequestQueuePositionWhenCancelled(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    if(request.isCancelled()) {
      requestAndRelatedRecords.getRequestQueue().remove(request);
    }
    return succeeded(requestAndRelatedRecords);
  }



  private Result<RequestAndRelatedRecords> unsetDueDateChangeByRecallFlag(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    RequestQueue queue = requestAndRelatedRecords.getRequestQueue();
    Loan loan = requestAndRelatedRecords.getRequest().getLoan();
    System.out.println("Yes I cam here");
    if (loan!=null && loan.wasDueDateChangedByRecall() && !queue.hasOpenRecalls()) {
      requestAndRelatedRecords.getRequest().setSendRecallNotice(true);
      System.out.println("YEs I will reset the flag");
      requestAndRelatedRecords.withLoan(loan.unsetDueDateChangedByRecall());
    }

    return succeeded(requestAndRelatedRecords);
  }
}
