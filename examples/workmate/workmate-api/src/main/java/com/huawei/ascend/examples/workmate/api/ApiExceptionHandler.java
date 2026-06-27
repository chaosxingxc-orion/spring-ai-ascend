package com.huawei.ascend.examples.workmate.api;

import com.huawei.ascend.examples.workmate.audit.AuditLedgerException;
import com.huawei.ascend.examples.workmate.approval.ApprovalAlreadyDecidedException;
import com.huawei.ascend.examples.workmate.approval.ApprovalNotFoundException;
import com.huawei.ascend.examples.workmate.artifact.ArtifactNotFoundException;
import com.huawei.ascend.examples.workmate.artifact.PreviewNotAllowedException;
import com.huawei.ascend.examples.workmate.chat.InvalidConversationEditException;
import com.huawei.ascend.examples.workmate.connector.ConnectorNotFoundException;
import com.huawei.ascend.examples.workmate.filehistory.FileVersionNotFoundException;
import com.huawei.ascend.examples.workmate.mcp.McpServerNotFoundException;
import com.huawei.ascend.examples.workmate.question.QuestionAlreadyAnsweredException;
import com.huawei.ascend.examples.workmate.question.QuestionNotFoundException;
import com.huawei.ascend.examples.workmate.office.ExpertNotFoundException;
import com.huawei.ascend.examples.workmate.office.PlaybookNotFoundException;
import com.huawei.ascend.examples.workmate.office.SkillNotFoundException;
import com.huawei.ascend.examples.workmate.cloud.CloudDisabledException;
import com.huawei.ascend.examples.workmate.connector.OAuthMockDisabledException;
import com.huawei.ascend.examples.workmate.office.StudioDisabledException;
import com.huawei.ascend.examples.workmate.share.ShareNotFoundException;
import com.huawei.ascend.examples.workmate.session.SessionBusyException;
import com.huawei.ascend.examples.workmate.session.InsufficientArchivableSessionsException;
import com.huawei.ascend.examples.workmate.session.SessionLimitExceededException;
import com.huawei.ascend.examples.workmate.session.SessionNotFoundException;
import com.huawei.ascend.examples.workmate.session.SessionQueueFullException;
import com.huawei.ascend.examples.workmate.tenant.QuotaExceededException;
import com.huawei.ascend.examples.workmate.session.WorkspaceException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SessionBusyException.class)
    public ProblemDetail handleSessionBusy(SessionBusyException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Session busy");
        detail.setProperty("sessionId", ex.getSessionId().toString());
        return detail;
    }

    @ExceptionHandler(SessionQueueFullException.class)
    public ProblemDetail handleSessionQueueFull(SessionQueueFullException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Session queue full");
        detail.setProperty("sessionId", ex.getSessionId().toString());
        detail.setProperty("maxSize", ex.getMaxSize());
        return detail;
    }

    @ExceptionHandler(SessionLimitExceededException.class)
    public ProblemDetail handleSessionLimitExceeded(SessionLimitExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Session limit exceeded");
        detail.setProperty("activeCount", ex.getActiveCount());
        detail.setProperty("maxActive", ex.getMaxActive());
        return detail;
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ProblemDetail handleQuotaExceeded(QuotaExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        detail.setTitle("Quota exceeded");
        detail.setProperty("metric", ex.metric());
        return detail;
    }

    @ExceptionHandler(InsufficientArchivableSessionsException.class)
    public ProblemDetail handleInsufficientArchivable(InsufficientArchivableSessionsException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Insufficient archivable sessions");
        detail.setProperty("requested", ex.getRequested());
        detail.setProperty("available", ex.getAvailable());
        detail.setProperty("activeCount", ex.getActiveCount());
        detail.setProperty("maxActive", ex.getMaxActive());
        return detail;
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail handleNotFound(SessionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Session not found");
        return detail;
    }

    @ExceptionHandler(QuestionNotFoundException.class)
    public ProblemDetail handleQuestionNotFound(QuestionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Question not found");
        return detail;
    }

    @ExceptionHandler(QuestionAlreadyAnsweredException.class)
    public ProblemDetail handleQuestionAnswered(QuestionAlreadyAnsweredException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Question already answered");
        return detail;
    }

    @ExceptionHandler(ApprovalNotFoundException.class)
    public ProblemDetail handleApprovalNotFound(ApprovalNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Approval not found");
        return detail;
    }

    @ExceptionHandler(AuditLedgerException.class)
    public ProblemDetail handleAuditLedger(AuditLedgerException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        detail.setTitle("Audit ledger unavailable");
        return detail;
    }

    @ExceptionHandler(ApprovalAlreadyDecidedException.class)
    public ProblemDetail handleApprovalDecided(ApprovalAlreadyDecidedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Approval already decided");
        return detail;
    }

    @ExceptionHandler(PreviewNotAllowedException.class)
    public ProblemDetail handlePreviewNotAllowed(PreviewNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(StudioDisabledException.class)
    public ProblemDetail handleStudioDisabled(StudioDisabledException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Developer Studio disabled");
        return detail;
    }

    @ExceptionHandler(CloudDisabledException.class)
    public ProblemDetail handleCloudDisabled(CloudDisabledException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("Cloud sessions disabled");
        return detail;
    }

    @ExceptionHandler(OAuthMockDisabledException.class)
    public ProblemDetail handleOAuthMockDisabled(OAuthMockDisabledException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setTitle("OAuth mock redirect disabled");
        return detail;
    }

    @ExceptionHandler(ArtifactNotFoundException.class)
    public ProblemDetail handleArtifactNotFound(ArtifactNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Artifact not found");
        return detail;
    }

    @ExceptionHandler(FileVersionNotFoundException.class)
    public ProblemDetail handleFileVersionNotFound(FileVersionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("File version not found");
        return detail;
    }

    @ExceptionHandler(McpServerNotFoundException.class)
    public ProblemDetail handleMcpNotFound(McpServerNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("MCP server not found");
        return detail;
    }

    @ExceptionHandler(ExpertNotFoundException.class)
    public ProblemDetail handleExpertNotFound(ExpertNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Expert not found");
        return detail;
    }

    @ExceptionHandler(SkillNotFoundException.class)
    public ProblemDetail handleSkillNotFound(SkillNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Skill not found");
        return detail;
    }

    @ExceptionHandler(PlaybookNotFoundException.class)
    public ProblemDetail handlePlaybookNotFound(PlaybookNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Playbook not found");
        return detail;
    }

    @ExceptionHandler(ConnectorNotFoundException.class)
    public ProblemDetail handleConnectorNotFound(ConnectorNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Connector not found");
        return detail;
    }

    @ExceptionHandler(ShareNotFoundException.class)
    public ProblemDetail handleShareNotFound(ShareNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Share not found");
        return detail;
    }

    @ExceptionHandler(InvalidConversationEditException.class)
    public ProblemDetail handleInvalidConversationEdit(InvalidConversationEditException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Invalid conversation edit");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Bad request");
        return detail;
    }

    @ExceptionHandler(WorkspaceException.class)
    public ProblemDetail handleWorkspace(WorkspaceException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Workspace error");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation failed");
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage()))
                .toList());
        return detail;
    }

    /**
     * Catch-all so unexpected exceptions are logged server-side and a generic message is returned
     * instead of leaking a stack trace / internal detail to the client.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        detail.setTitle("Internal error");
        return detail;
    }
}
