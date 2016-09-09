package io.token;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.token.proto.bankapi.AccountServiceGrpc;
import io.token.proto.bankapi.Bankapi;
import io.token.rpc.client.RpcChannelFactory;
import rx.Observable;

import java.util.List;
import java.util.Optional;

import static io.token.rpc.util.Converters.toObservable;
import static java.lang.String.format;


public final class BankClient {
    private final AccountServiceGrpc.AccountServiceFutureStub client;

    public BankClient(String hostName, int port) {
        ManagedChannel channel = RpcChannelFactory.forTarget(format("dns:///%s:%d/", hostName, port));
        this.client = AccountServiceGrpc.newFutureStub(channel);
    }

    public byte[] createLinkingPayload(
            String alias,
            Optional<String> secret,
            List<String> accountNumbers,
            Optional<Message> metadata) {
        return createLinkingPayloadAsync(alias, secret, accountNumbers, metadata)
                .toBlocking()
                .single();
    }

    public Observable<byte[]> createLinkingPayloadAsync(
            String alias,
            Optional<String> secret,
            List<String> accountNumbers,
            Optional<Message> metadata) {
        Bankapi.CreateAccountLinkPayloadRequest.Builder builder = Bankapi.CreateAccountLinkPayloadRequest.newBuilder();
        metadata.ifPresent(data -> builder.setMetadata(Any.pack(data)));
        builder
                .setAlias(alias)
                .addAllAccounts(accountNumbers)
                .setSecret(secret.orElse(""))
                .build();
        return toObservable(client.createAccountLinkPayload(builder.build()))
                .map(response -> response.getAccountLinkPayload().toByteArray());
    }
}