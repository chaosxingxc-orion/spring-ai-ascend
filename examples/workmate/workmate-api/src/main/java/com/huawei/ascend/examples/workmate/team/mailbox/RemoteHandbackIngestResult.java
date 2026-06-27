package com.huawei.ascend.examples.workmate.team.mailbox;

/** Result of ingesting a proactive remote member handback into the team mailbox. */
public record RemoteHandbackIngestResult(MemberSendMessageOutcome outcome, int sequence) {}
