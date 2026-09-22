package com.ticket.booking.order.endpoint;

import java.net.URI;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.order.endpoint.docs.HoldControllerDocs;
import com.ticket.booking.order.usecase.StartBookingUseCase;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 기존 프론트가 쓰던 주문 생성 호환 API다. {@code POST /api/v1/performances/{performanceId}/holds}로 들어온 요청을 그대로
 * {@link StartBookingUseCase}에 넘긴다 — 별도의 hold 생성 유스케이스는 없고, 만들지도 않는다. "hold"라는 URL은 선점과 주문 생성을 같은 것으로 보던 시절의 이름이며, 지금 이
 * endpoint가 만드는 것은 PENDING 주문이다.
 *
 * <p>URL·요청 본문·응답(201, {@code Location}, {@code X-Order-Key}, {@code StartBookingUseCase.Output})은 모두 {@code POST
 * /api/v1/orders}와 같은 계약이라 바꾸지 않는다. 새 클라이언트는 {@code OrderController}를 쓴다. 이 클래스가 hold 패키지에 있는 이유도 URL이 hold이기 때문일 뿐, 소유
 * 업무는 주문이다.
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/performances/{performanceId}/holds")
@RequiredArgsConstructor
public class HoldController implements HoldControllerDocs {
    private final StartBookingUseCase startBookingUseCase;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<StartBookingUseCase.Output>> createHold(
            @PathVariable final Long performanceId,
            @RequestBody final CreateHoldRequest request,
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @RequestHeader(value = "X-Admission-Token", required = false) final String admissionToken,
            final AuthenticatedMember member) {
        // @Valid가 seatIds의 null·빈 목록을 이미 400으로 거른 뒤에야 여기에 닿는다.
        final StartBookingUseCase.Input input = new StartBookingUseCase.Input(
                performanceId,
                Objects.requireNonNull(request.getSeatIds(), "seatIds"),
                member.memberId(),
                admissionToken);
        final StartBookingUseCase.Output output = startBookingUseCase.execute(input);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + output.orderKey()))
                .header("X-Order-Key", output.orderKey())
                .body(ApiResponse.success(output));
    }
}
