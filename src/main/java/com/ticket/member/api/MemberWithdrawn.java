package com.ticket.member.api;

/** A committed withdrawal that invalidates the member's existing connections. */
public record MemberWithdrawn(long memberId) {}
