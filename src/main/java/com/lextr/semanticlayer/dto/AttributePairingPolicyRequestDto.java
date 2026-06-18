package com.lextr.semanticlayer.dto;

public record AttributePairingPolicyRequestDto(
        String client_id,
        String pairing_cd,
        boolean is_cross_engine_flg
) {
}
