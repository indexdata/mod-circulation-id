package org.folio.circulation.domain.notice.schedule;

import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineNoticeContext;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class FeeFineScheduledNoticeHandler extends ScheduledNoticeHandler {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final FeeFineActionRepository actionRepository;
  private final LoanPolicyRepository loanPolicyRepository;

  public FeeFineScheduledNoticeHandler(Clients clients, LoanRepository loanRepository) {
    super(clients, loanRepository);
    this.actionRepository = new FeeFineActionRepository(clients);
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
    ScheduledNoticeContext context) {
    log.info("FeeFineScheduledNoticeHandler fetchData");
    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchAction))
      .thenCompose(r -> r.after(this::fetchAccount))
      .thenCompose(r -> r.after(this::fetchLoan))
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicyIdForLoan));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchAction(
    ScheduledNoticeContext context) {
    log.info("FeeFineScheduledNoticeHandler fetchAction context.getNotice().getFeeFineActionId() :{}",context.getNotice().getFeeFineActionId());
    return actionRepository.findById(context.getNotice().getFeeFineActionId())
      .thenApply(mapResult(context::withAction));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchAccount(
    ScheduledNoticeContext context) {
    log.info("FeeFineScheduledNoticeHandler fetchAccount context.getAction().getId() :{}",context.getAction().getId());
    return accountRepository.findAccountForAction(context.getAction())
      .thenApply(mapResult(context::withAccount));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchLoan(
    ScheduledNoticeContext context) {
    log.info("FeeFineScheduledNoticeHandler fetchLoan");

    if (isNoticeIrrelevant(context)) {
      log.info("FeeFineScheduledNoticeHandler fetchLoan isNoticeIrrelevant : {}",isNoticeIrrelevant(context));
      return ofAsync(() -> context);
    }
    log.info("FeeFineScheduledNoticeHandler fetchLoan context.getAction().getId() :{}",context.getAction().getId());

    // this also fetches user and item
    return loanRepository.findLoanForAccount(context.getAccount())
      .thenCompose(r -> r.after(loanPolicyRepository::findPolicyForLoan))
      .thenApply(mapResult(context::withLoan))
      .thenApply(r -> r.next(this::failWhenLoanIsIncomplete));
  }

  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(ScheduledNoticeContext context) {
    ScheduledNotice notice = context.getNotice();
    log.info("FeeFineScheduledNoticeHandler updateNotice");
    return isNoticeIrrelevant(context) || !notice.getConfiguration().isRecurring()
      ? deleteNoticeAsIrrelevant(notice)
      : scheduledNoticesRepository.update(getNextRecurringNotice(notice));
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    return context.getAccount().isClosed() &&
      !context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment();
  }

  @Override
  protected NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context) {
    log.info("buildNoticeLogContext");
    Loan loan = context.getLoan();
    ScheduledNotice notice = context.getNotice();

    NoticeLogContextItem logContextItem = NoticeLogContextItem.from(loan)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation())
      .withNoticePolicyId(context.getPatronNoticePolicyId());

    return new NoticeLogContext()
      .withUser(loan.getUser())
      .withAccountId(context.getAccount().getId())
      .withItems(singletonList(logContextItem));
  }

  @Override
  protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
    log.info("buildNoticeContextJson context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment() : {}",context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment());
    return context.getNotice().getTriggeringEvent().isAutomaticFeeFineAdjustment()
      ? createFeeFineNoticeContext(context.getAccount(), context.getLoan(), context.getAction())
      : createFeeFineNoticeContext(context.getAccount(), context.getLoan());
  }

  private static ScheduledNotice getNextRecurringNotice(ScheduledNotice notice) {
    Period recurringPeriod = notice.getConfiguration().getRecurringPeriod();
    ZonedDateTime nextRunTime = recurringPeriod.plusDate(notice.getNextRunTime());
    ZonedDateTime now = getZonedDateTime();

    if (isBeforeMillis(nextRunTime, now)) {
      nextRunTime = recurringPeriod.plusDate(now);
    }

    return notice.withNextRunTime(nextRunTime);
  }

}
