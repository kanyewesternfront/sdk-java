package io.token.rpc;

import io.token.proto.common.member.MemberProtos.Member;
import io.token.proto.common.member.MemberProtos.MemberAddKeyOperation;
import io.token.proto.common.member.MemberProtos.MemberUpdate;
import io.token.proto.common.security.SecurityProtos;
import io.token.proto.gateway.Gateway.CreateMemberRequest;
import io.token.proto.gateway.Gateway.CreateMemberResponse;
import io.token.proto.gateway.Gateway.UpdateMemberRequest;
import io.token.proto.gateway.Gateway.UpdateMemberResponse;
import io.token.proto.gateway.GatewayServiceGrpc.GatewayServiceFutureStub;
import io.token.security.SecretKey;
import io.token.util.codec.ByteEncoding;
import rx.Observable;

import static io.token.rpc.util.Converters.toObservable;
import static io.token.security.Crypto.sign;
import static io.token.util.Util.generateNonce;

/**
 * Similar to {@link Client} but is only used for a handful of requests that
 * don't require authentication. We use this client to create new member or
 * login an existing one and switch to the authenticated {@link Client}.
 */
public final class UnauthenticatedClient {
    private final GatewayServiceFutureStub gateway;

    /**
     * @param gateway gateway gRPC stub
     */
    public UnauthenticatedClient(GatewayServiceFutureStub gateway) {
        this.gateway = gateway;
    }

    /**
     * Creates new member ID. After the method returns the ID is reserved on
     * the server.
     *
     * @return newly created member id
     */
    public Observable<String> createMemberId() {
        return
                toObservable(gateway.createMember(CreateMemberRequest.newBuilder()
                        .setNonce(generateNonce())
                        .build()))
                .map(CreateMemberResponse::getMemberId);
    }

    /**
     * Adds first key to be linked with the specified member id.
     *
     * @param memberId member id
     * @param key adds first key to be linked with the member id
     * @return member information
     */
    public Observable<Member> addFirstKey(String memberId, SecretKey key) {
        MemberUpdate update = MemberUpdate.newBuilder()
                .setMemberId(memberId)
                .setAddKey(MemberAddKeyOperation.newBuilder()
                        .setLevel(0) // TODO(alexey): This will be enum at some point.
                        .setPublicKey(ByteEncoding.serialize(key.getPublicKey())))
                .build();

        return
                toObservable(gateway.updateMember(UpdateMemberRequest.newBuilder()
                        .setUpdate(update)
                        .setSignature(SecurityProtos.Signature.newBuilder()
                                .setKeyId(key.getId())
                                .setSignature(sign(key, update)))
                        .build()))
                .map(UpdateMemberResponse::getMember);
    }
}