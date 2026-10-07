package com.ticket.booking.order.usecase;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.ticket.shared.exception.InvalidRequestException;

public final class RequestedSeatIds {
    private final List<Long> values;

    private RequestedSeatIds(final List<Long> values) {
        this.values = values;
    }

    /** 검증한 뒤 오름차순으로 정렬해 담는다. 정렬은 같은 좌석 집합이면 요청 순서와 관계없이 같은 값이 되게 하고, 좌석 락을 항상 같은 순서로 잡게 한다. */
    public static RequestedSeatIds from(final List<Long> requestedSeatIds) {
        if (requestedSeatIds == null) {
            throw new InvalidRequestException("좌석 ID 목록은 null일 수 없습니다.");
        }
        if (requestedSeatIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRequestException("null seatId가 포함되어 있습니다.");
        }
        if (requestedSeatIds.size() != new HashSet<>(requestedSeatIds).size()) {
            throw new InvalidRequestException("중복된 seatId가 포함되어 있습니다.");
        }
        final List<Long> seatIds = requestedSeatIds.stream().sorted().toList();
        if (seatIds.isEmpty()) {
            throw new InvalidRequestException("선택한 좌석이 없습니다.");
        }
        return new RequestedSeatIds(seatIds);
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
