package com.huawei.ascend.examples.workmate.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.question.dto.QuestionAnswerRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserQuestionServiceTest {

    private SessionPersistenceService sessionPersistenceService;
    private UserQuestionService service;

    @BeforeEach
    void setUp() {
        sessionPersistenceService = mock(SessionPersistenceService.class);
        service = new UserQuestionService(sessionPersistenceService);
    }

    @Test
    void answerCompletesPendingQuestion() throws Exception {
        UUID sessionId = UUID.randomUUID();
        QuestionGate gate = new QuestionGate(service, 5);
        CountDownLatch registered = new CountDownLatch(1);
        AtomicReference<QuestionGate.PendingQuestion> captured = new AtomicReference<>();
        gate.setListener(pending -> {
            captured.set(pending);
            registered.countDown();
        });

        CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            QuestionGate.AnswerResult result = gate.await(
                    sessionId,
                    "task-1",
                    "workmate_ask_user_question__x",
                    "Pick one",
                    List.of("A", "B"),
                    false,
                    false);
            assertThat(result.selections()).containsExactly("A");
            done.countDown();
        });
        worker.start();

        assertThat(registered.await(2, TimeUnit.SECONDS)).isTrue();
        UUID questionId = captured.get().id();
        service.answer(questionId, new QuestionAnswerRequest(List.of("A"), null, false));

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        worker.join(1000);
        verify(sessionPersistenceService).recordQuestionAnswered(
                eq(sessionId),
                eq(questionId),
                eq("Pick one"),
                eq(List.of("A", "B")),
                eq(List.of("A")),
                eq(null),
                eq(false));
    }

    @Test
    void skipMarksQuestionSkipped() {
        UUID sessionId = UUID.randomUUID();
        QuestionGate.PendingQuestion pending = service.register(
                sessionId, "task-1", "tool", "Need input?", List.of(), true, false);
        service.skip(pending.id());
        assertThatThrownBy(() -> service.answer(pending.id(), new QuestionAnswerRequest(List.of(), "hi", false)))
                .isInstanceOf(QuestionNotFoundException.class);
        verify(sessionPersistenceService).recordQuestionAnswered(
                eq(sessionId),
                eq(pending.id()),
                eq("Need input?"),
                eq(List.of()),
                eq(List.of()),
                eq(null),
                eq(true));
    }

    @Test
    void expireNotifiesCancelledListener() throws Exception {
        UUID sessionId = UUID.randomUUID();
        QuestionGate gate = new QuestionGate(service, 1);
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicReference<QuestionGate.PendingQuestion> captured = new AtomicReference<>();
        gate.setListener(pending -> {
            captured.set(pending);
            registered.countDown();
        });
        gate.setCancelledListener(pending -> cancelled.countDown());

        Thread worker = new Thread(() -> gate.await(
                sessionId,
                "task-timeout",
                "workmate_ask_user_question__x",
                "Pick one",
                List.of("A", "B"),
                false,
                false));
        worker.start();

        assertThat(registered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(cancelled.await(3, TimeUnit.SECONDS)).isTrue();
        worker.join(2000);
        verify(sessionPersistenceService).recordQuestionCancelled(
                eq(sessionId),
                eq(captured.get().id()),
                eq("Pick one"),
                eq(List.of("A", "B")),
                eq(false),
                eq(false),
                eq("task-timeout"));
    }

    @Test
    void gateReusesPriorAnswerWithoutRegistering() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String prompt = "请确认执行模式";
        when(sessionPersistenceService.findAnsweredQuestion(sessionId, prompt, List.of("完整模式", "快速模式")))
                .thenReturn(java.util.Optional.of(new AnsweredQuestionRecord(List.of("完整模式"), null)));

        QuestionGate gate = new QuestionGate(service, 5);
        AtomicReference<QuestionGate.PendingQuestion> captured = new AtomicReference<>();
        gate.setListener(captured::set);

        QuestionGate.AnswerResult result = gate.await(
                sessionId,
                "task-2",
                "workmate_ask_user_question__x",
                prompt,
                List.of("完整模式", "快速模式"),
                false,
                false);

        assertThat(result.selections()).containsExactly("完整模式");
        assertThat(captured.get()).isNull();
    }

    @Test
    void gateReusesImplicitResearchParamsAfterTeamBuilt() {
        UUID sessionId = UUID.randomUUID();
        String prompt = "请确认以下研究参数：";
        List<String> options = List.of(
                "报告语言：中文",
                "时效窗口：近1年（AI/科技类默认）",
                "引用格式：APA",
                "输出格式：Markdown");
        when(sessionPersistenceService.findAnsweredQuestion(sessionId, prompt, options))
                .thenReturn(java.util.Optional.empty());
        when(sessionPersistenceService.hasAnsweredSemanticKey(sessionId, "hitl:research-mode"))
                .thenReturn(true);
        when(sessionPersistenceService.hasRunEvent(sessionId, "team.build.completed")).thenReturn(false);

        QuestionGate gate = new QuestionGate(service, 5);
        AtomicReference<QuestionGate.PendingQuestion> captured = new AtomicReference<>();
        gate.setListener(captured::set);

        QuestionGate.AnswerResult result = gate.await(
                sessionId,
                "task-3",
                "workmate_ask_user_question__x",
                prompt,
                options,
                false,
                true);

        assertThat(result.selections()).containsExactlyElementsOf(options);
        assertThat(captured.get()).isNull();
    }
}
