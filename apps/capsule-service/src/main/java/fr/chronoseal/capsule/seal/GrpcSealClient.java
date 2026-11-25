package fr.chronoseal.capsule.seal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;

import fr.chronoseal.seal.v1.SealRequest;
import fr.chronoseal.seal.v1.SealResponse;
import fr.chronoseal.seal.v1.SealServiceGrpc;
import fr.chronoseal.seal.v1.SealedPayload;
import fr.chronoseal.seal.v1.UnsealRequest;
import fr.chronoseal.seal.v1.UnsealResponse;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

@Component
public class GrpcSealClient implements SealClient {

    private final SealServiceGrpc.SealServiceBlockingStub stub;
    private final Duration deadline;

    public GrpcSealClient(ManagedChannel sealChannel, SealProperties properties) {
        this.stub = SealServiceGrpc.newBlockingStub(sealChannel);
        this.deadline = properties.deadline();
    }

    @Override
    public SealedContent seal(UUID capsuleId, String message, Instant unlockAt) {
        SealRequest request = SealRequest.newBuilder()
                .setCapsuleId(capsuleId.toString())
                .setPlaintext(ByteString.copyFromUtf8(message))
                .setUnlockAt(toTimestamp(unlockAt))
                .build();
        try {
            SealResponse response = stubWithDeadline().seal(request);
            SealedPayload payload = response.getPayload();
            return new SealedContent(
                    payload.getKeyId(),
                    payload.getNonce().toByteArray(),
                    payload.getCiphertext().toByteArray(),
                    response.getCommitment());
        } catch (StatusRuntimeException exception) {
            throw translate("scellement", capsuleId, exception);
        }
    }

    @Override
    public UnsealedContent unseal(UUID capsuleId, Instant unlockAt, SealedContent sealed) {
        UnsealRequest request = UnsealRequest.newBuilder()
                .setCapsuleId(capsuleId.toString())
                .setUnlockAt(toTimestamp(unlockAt))
                .setPayload(SealedPayload.newBuilder()
                        .setKeyId(sealed.keyId())
                        .setNonce(ByteString.copyFrom(sealed.nonce()))
                        .setCiphertext(ByteString.copyFrom(sealed.ciphertext())))
                .build();
        try {
            UnsealResponse response = stubWithDeadline().unseal(request);
            return new UnsealedContent(response.getPlaintext().toStringUtf8(), response.getSalt().toByteArray());
        } catch (StatusRuntimeException exception) {
            throw translate("ouverture", capsuleId, exception);
        }
    }

    private SealServiceGrpc.SealServiceBlockingStub stubWithDeadline() {
        return stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private static SealServiceException translate(String operation, UUID capsuleId, StatusRuntimeException exception) {
        String message = "Échec du " + operation + " de la capsule " + capsuleId + " : " + exception.getStatus();
        return switch (exception.getStatus().getCode()) {
            case FAILED_PRECONDITION -> new SealTimeLockException(message, exception);
            case DATA_LOSS, NOT_FOUND -> new SealIntegrityException(message, exception);
            case UNAVAILABLE, DEADLINE_EXCEEDED -> new SealServiceUnavailableException(message, exception);
            default -> new SealServiceException(message, exception);
        };
    }
}
