package com.huawei.ascend.examples.workmate.question;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Blocks agent tool execution until the user answers or skips (W36-A1). */
public final class QuestionGate {

    public enum Outcome {
        ANSWERED,
        SKIPPED,
        TIMED_OUT
    }

    public record AnswerResult(List<String> selections, String text, boolean skipped) {
    }

    private final UserQuestionService questionService;
    private volatile Consumer<PendingQuestion> listener;
    private volatile Consumer<PendingQuestion> cancelledListener;
    private final long timeoutSeconds;

    public QuestionGate(UserQuestionService questionService, long timeoutSeconds) {
        this.questionService = questionService;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setListener(Consumer<PendingQuestion> listener) {
        this.listener = listener;
    }

    public void setCancelledListener(Consumer<PendingQuestion> listener) {
        this.cancelledListener = listener;
    }

    public AnswerResult await(
            UUID sessionId,
            String taskId,
            String toolName,
            String question,
            List<String> options,
            boolean allowFreeText,
            boolean multiSelect) {
        var resolved = questionService.resolveWithoutPrompting(sessionId, question, options);
        if (resolved.isPresent()) {
            return resolved.get();
        }
        PendingQuestion pending = questionService.register(
                sessionId, taskId, toolName, question, options, allowFreeText, multiSelect);
        Consumer<PendingQuestion> notify = listener;
        if (notify != null) {
            notify.accept(pending);
        }

        try {
            if (!pending.await(timeoutSeconds, TimeUnit.SECONDS)) {
                questionService.expire(pending.id());
                notifyCancelled(pending);
                return new AnswerResult(List.of(), null, false);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            questionService.expire(pending.id());
            notifyCancelled(pending);
            return new AnswerResult(List.of(), null, false);
        }

        return pending.result().orElse(new AnswerResult(List.of(), null, false));
    }

    private void notifyCancelled(PendingQuestion pending) {
        Consumer<PendingQuestion> notify = cancelledListener;
        if (notify != null) {
            notify.accept(pending);
        }
    }

    public Outcome outcomeFor(AnswerResult result, boolean timedOut) {
        if (timedOut) {
            return Outcome.TIMED_OUT;
        }
        if (result.skipped()) {
            return Outcome.SKIPPED;
        }
        return Outcome.ANSWERED;
    }

    public static final class PendingQuestion {
        private final UUID id;
        private final UUID sessionId;
        private final String taskId;
        private final String toolName;
        private final String question;
        private final List<String> options;
        private final boolean allowFreeText;
        private final boolean multiSelect;
        private final Instant createdAt;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<AnswerResult> result = new AtomicReference<>();

        PendingQuestion(
                UUID id,
                UUID sessionId,
                String taskId,
                String toolName,
                String question,
                List<String> options,
                boolean allowFreeText,
                boolean multiSelect) {
            this.id = id;
            this.sessionId = sessionId;
            this.taskId = taskId;
            this.toolName = toolName;
            this.question = question;
            this.options = List.copyOf(options);
            this.allowFreeText = allowFreeText;
            this.multiSelect = multiSelect;
            this.createdAt = Instant.now();
        }

        public UUID id() {
            return id;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public String taskId() {
            return taskId;
        }

        public String toolName() {
            return toolName;
        }

        public String question() {
            return question;
        }

        public List<String> options() {
            return options;
        }

        public boolean allowFreeText() {
            return allowFreeText;
        }

        public boolean multiSelect() {
            return multiSelect;
        }

        public Instant createdAt() {
            return createdAt;
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        void complete(AnswerResult answer) {
            result.set(answer);
            latch.countDown();
        }

        java.util.Optional<AnswerResult> result() {
            return java.util.Optional.ofNullable(result.get());
        }
    }
}
