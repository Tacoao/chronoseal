package fr.chronoseal.capsule.seal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

import fr.chronoseal.seal.v1.SealRequest;
import fr.chronoseal.seal.v1.SealResponse;
import fr.chronoseal.seal.v1.SealServiceGrpc;
import fr.chronoseal.seal.v1.SealedPayload;
import fr.chronoseal.seal.v1.UnsealRequest;
import fr.chronoseal.seal.v1.UnsealResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

class GrpcSealClientTest {

    private static final UUID ID = UUID.fromString("7f6c1d1e-5b0a-4c55-9a43-0c7c2f0e9a11");
    private static final Instant UNLOCK_AT = Instant.parse("2026-11-25T11:00:00Z");

    private final FakeSealService fake = new FakeSealService();
    private Server server;
    private ManagedChannel channel;
    private GrpcSealClient client;

    @BeforeEach
    void setUp() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(fake).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new GrpcSealClient(channel, new SealProperties("unused", 0, Duration.ofSeconds(2)));
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void sendsCapsuleDataAndMapsSealResponse() {
        SealedContent sealed = client.seal(ID, "été 2030", UNLOCK_AT);

        SealRequest request = fake.lastSealRequest.get();
        assertThat(request.getCapsuleId()).isEqualTo(ID.toString());
        assertThat(request.getPlaintext().toStringUtf8()).isEqualTo("été 2030");
        assertThat(request.getUnlockAt().getSeconds()).isEqualTo(UNLOCK_AT.getEpochSecond());
        assertThat(sealed.keyId()).isEqualTo("k1");
        assertThat(sealed.nonce()).containsExactly(9, 9, 9);
        assertThat(sealed.ciphertext()).containsExactly(7, 7);
        assertThat(sealed.commitment()).isEqualTo("abc123");
    }

    @Test
    void sendsSealedPayloadBackAndMapsUnsealResponse() {
        UnsealedContent content = client.unseal(ID, UNLOCK_AT, new SealedContent("k1", new byte[] {9}, new byte[] {7}, "c"));

        UnsealRequest request = fake.lastUnsealRequest.get();
        assertThat(request.getPayload().getKeyId()).isEqualTo("k1");
        assertThat(request.getPayload().getCiphertext().toByteArray()).containsExactly(7);
        assertThat(content.message()).isEqualTo("bonjour");
        assertThat(content.salt()).containsExactly(1, 2);
    }

    @Test
    void mapsFailedPreconditionToTimeLock() {
        fake.unsealError = Status.FAILED_PRECONDITION;

        assertThatThrownBy(this::unseal).isInstanceOf(SealTimeLockException.class);
    }

    @Test
    void mapsDataLossToIntegrityFailure() {
        fake.unsealError = Status.DATA_LOSS;

        assertThatThrownBy(this::unseal).isInstanceOf(SealIntegrityException.class);
    }

    @Test
    void mapsUnknownKeyToIntegrityFailure() {
        fake.unsealError = Status.NOT_FOUND;

        assertThatThrownBy(this::unseal).isInstanceOf(SealIntegrityException.class);
    }

    @Test
    void mapsUnavailableAndDeadlineToUnavailable() {
        fake.unsealError = Status.UNAVAILABLE;
        assertThatThrownBy(this::unseal).isInstanceOf(SealServiceUnavailableException.class);

        fake.unsealError = Status.DEADLINE_EXCEEDED;
        assertThatThrownBy(this::unseal).isInstanceOf(SealServiceUnavailableException.class);
    }

    @Test
    void mapsOtherErrorsToGenericFailure() {
        fake.sealError = Status.INVALID_ARGUMENT;

        assertThatThrownBy(() -> client.seal(ID, "m", UNLOCK_AT))
                .isExactlyInstanceOf(SealServiceException.class)
                .hasMessageContaining(ID.toString());
    }

    private UnsealedContent unseal() {
        return client.unseal(ID, UNLOCK_AT, new SealedContent("k1", new byte[] {9}, new byte[] {7}, "c"));
    }

    private static final class FakeSealService extends SealServiceGrpc.SealServiceImplBase {

        private final AtomicReference<SealRequest> lastSealRequest = new AtomicReference<>();
        private final AtomicReference<UnsealRequest> lastUnsealRequest = new AtomicReference<>();
        private Status sealError;
        private Status unsealError;

        @Override
        public void seal(SealRequest request, StreamObserver<SealResponse> responseObserver) {
            lastSealRequest.set(request);
            if (sealError != null) {
                responseObserver.onError(sealError.asRuntimeException());
                return;
            }
            responseObserver.onNext(SealResponse.newBuilder()
                    .setPayload(SealedPayload.newBuilder()
                            .setKeyId("k1")
                            .setNonce(ByteString.copyFrom(new byte[] {9, 9, 9}))
                            .setCiphertext(ByteString.copyFrom(new byte[] {7, 7})))
                    .setCommitment("abc123")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void unseal(UnsealRequest request, StreamObserver<UnsealResponse> responseObserver) {
            lastUnsealRequest.set(request);
            if (unsealError != null) {
                responseObserver.onError(unsealError.asRuntimeException());
                return;
            }
            responseObserver.onNext(UnsealResponse.newBuilder()
                    .setPlaintext(ByteString.copyFromUtf8("bonjour"))
                    .setSalt(ByteString.copyFrom(new byte[] {1, 2}))
                    .build());
            responseObserver.onCompleted();
        }
    }
}
