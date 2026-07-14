package com.wellhouse.backend.service;

import com.wellhouse.backend.entity.CommandEntity;
import com.wellhouse.backend.entity.CommandStatus;
import com.wellhouse.backend.entity.CommandTarget;
import com.wellhouse.backend.entity.EventLogEntity;
import com.wellhouse.backend.messaging.DeviceCommander;
import com.wellhouse.backend.repository.CommandRepository;
import com.wellhouse.backend.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 명령 발행/ACK/타임아웃 처리. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandService {

    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final CommandRepository commandRepo;
    private final EventLogRepository eventRepo;
    private final DeviceCommander commander;

    /** 명령 발행 → 저장(PENDING) → 펌웨어로 전송. */
    @Transactional
    public CommandEntity issue(String deviceId, CommandTarget target, String issuedBy, String reason) {
        CommandEntity cmd = CommandEntity.builder()
                .id(UUID.randomUUID().toString())
                .deviceId(deviceId)
                .target(target)
                .status(CommandStatus.PENDING)
                .issuedBy(issuedBy)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
        commandRepo.save(cmd);
        commander.sendCommand(deviceId, cmd);
        log.info("command issued device={} target={} by={}", deviceId, target, issuedBy);
        return cmd;
    }

    /** 펌웨어 ACK 수신 처리. */
    @Transactional
    public void handleAck(String deviceId, String cmdId, String result, String detail) {
        CommandEntity cmd = commandRepo.findById(cmdId).orElse(null);
        if (cmd == null) {
            log.warn("ack for unknown command {}", cmdId);
            return;
        }
        boolean ok = "ok".equalsIgnoreCase(result);
        cmd.setStatus(ok ? CommandStatus.ACK_OK : CommandStatus.ACK_FAIL);
        cmd.setAckResult(result);
        cmd.setAckDetail(detail);
        cmd.setAckAt(Instant.now());
        commandRepo.save(cmd);

        eventRepo.save(EventLogEntity.builder()
                .deviceId(deviceId).type("command_ack")
                .detailJson("{\"cmdId\":\"" + cmdId + "\",\"target\":\"" + cmd.getTarget()
                        + "\",\"result\":\"" + result + "\"}")
                .ts(Instant.now()).build());
    }

    /** 10초 내 무응답 명령 타임아웃 처리 (스케줄러가 호출). */
    @Transactional
    public int sweepTimeouts() {
        Instant cutoff = Instant.now().minusSeconds(ACK_TIMEOUT_SECONDS);
        List<CommandEntity> stale = commandRepo.findByStatusAndCreatedAtBefore(CommandStatus.PENDING, cutoff);
        for (CommandEntity c : stale) {
            c.setStatus(CommandStatus.TIMEOUT);
            c.setAckResult("fail");
            c.setAckDetail("timeout: no ack within " + ACK_TIMEOUT_SECONDS + "s");
            c.setAckAt(Instant.now());
        }
        if (!stale.isEmpty()) {
            commandRepo.saveAll(stale);
            log.warn("command timeout swept: {}", stale.size());
        }
        return stale.size();
    }

    public List<CommandEntity> history(String deviceId) {
        return commandRepo.findByDeviceIdOrderByCreatedAtDesc(deviceId);
    }
}
