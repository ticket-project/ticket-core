package com.ticket.booking;

import java.util.List;

/**
 * metadata module이 조합하는 코드/라벨 목록 중 booking이 소유한 부분이다.
 * 어떤 module도 booking internal enum·entity를 직접 import하지 않고 이 계약만 쓴다.
 */
public interface BookingMetadata {

    List<CodeLabel> performanceSeatStates();

    List<CodeLabel> holdStates();

    List<CodeLabel> orderStates();

    record CodeLabel(String code, String label) {
    }
}
