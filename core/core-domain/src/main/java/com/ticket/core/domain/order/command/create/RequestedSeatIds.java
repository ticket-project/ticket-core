package com.ticket.core.domain.order.command.create;

import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class RequestedSeatIds {

    private final List<Long> values;

    private RequestedSeatIds(final List<Long> values) {
        this.values = values;
    }

    public static RequestedSeatIds from(final List<Long> requestedSeatIds) {
        validateNullList(requestedSeatIds);
        validateNullElement(requestedSeatIds);
        validateDuplicate(requestedSeatIds);
        return new RequestedSeatIds(normalize(requestedSeatIds));
    }

    private static void validateNullList(final List<Long> requestedSeatIds) {
        if (requestedSeatIds != null) {
            return;
        }
        throw new CoreException(ErrorType.INVALID_REQUEST, "좌석 ID 목록은 null일 수 없습니다.");
    }

    private static void validateNullElement(final List<Long> requestedSeatIds) {
        if (requestedSeatIds.stream().noneMatch(Objects::isNull)) {
            return;
        }
        throw new CoreException(ErrorType.INVALID_REQUEST, "null seatId가 포함되어 있습니다.");
    }

    private static void validateDuplicate(final List<Long> requestedSeatIds) {
        if (requestedSeatIds.size() == new HashSet<>(requestedSeatIds).size()) {
            return;
        }
        throw new CoreException(ErrorType.INVALID_REQUEST, "중복된 seatId가 포함되어 있습니다.");
    }

    private static List<Long> normalize(final List<Long> requestedSeatIds) {
        final List<Long> seatIds = requestedSeatIds.stream()
                .sorted()
                .toList();
        validateEmpty(seatIds);
        return seatIds;
    }

    private static void validateEmpty(final List<Long> seatIds) {
        if (!seatIds.isEmpty()) {
            return;
        }
        throw new CoreException(ErrorType.INVALID_REQUEST, "선택한 좌석이 없습니다.");
    }

    public int size() {
        return values.size();
    }

    public List<Long> toList() {
        return values;
    }

    @Override
    public boolean equals(final Object object) {
        if (!(object instanceof RequestedSeatIds requestedSeatIds)) {
            return false;
        }
        return Objects.equals(values, requestedSeatIds.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }
}
