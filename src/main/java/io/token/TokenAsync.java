package io.token;

import static java.util.Collections.singletonList;

import io.grpc.ManagedChannel;
import io.token.proto.common.notification.NotificationProtos.NotifyStatus;
import io.token.proto.common.security.SecurityProtos.SealedMessage;
import io.token.rpc.Client;
import io.token.rpc.ClientFactory;
import io.token.rpc.UnauthenticatedClient;
import io.token.security.SecretKeyStore;
import io.token.security.Signer;
import io.token.security.crypto.Crypto;
import io.token.security.crypto.EdDsaCrypto;
import io.token.security.keystore.InMemorySecretKeyStore;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import rx.Observable;

/**
 * Use this class to create to create a new member using {@link #createMember}
 * method or login an existing member using {@link #login}.
 * <p></p>The class provides async API with {@link Token} providing a synchronous
 * version. {@link Token} instance can be obtained by calling {@link #sync}
 * method.
 * </p>
 */
public final class TokenAsync {
    private final ManagedChannel channel;

    TokenAsync(ManagedChannel channel) {
        this.channel = channel;
    }

    /**
     * Returns a sync version of the API.
     *
     * @return synchronous version of the account API
     */
    public Token sync() {
        return new Token(this);
    }

    /**
     * Checks if a given username already exists.
     *
     * @param username username to check
     * @return {@code true} if username exists, {@code false} otherwise
     */
    public Observable<Boolean> usernameExists(String username) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.usernameExists(username);
    }

    /**
     * Creates a new Token member with a pair of auto generated keys and the
     * given username.
     *
     * @param username member username to use, must be unique
     * @return newly created member
     */
    public Observable<MemberAsync> createMember(String username) {
        Crypto crypto = EdDsaCrypto.getInstance();
        KeyPair keyPair = crypto.generateKeyPair();
        SecretKeyStore keyStore = new InMemorySecretKeyStore(keyPair);
        Signer signer = keyStore.createSigner();

        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);

        return unauthenticated
                .createMemberId()
                .flatMap(memberId -> unauthenticated.addFirstKey(
                        memberId,
                        keyPair.getPublic(),
                        signer))
                .flatMap(member -> {
                    Client client = ClientFactory.authenticated(
                            channel,
                            member.getId(),
                            null,
                            signer);
                    return client
                            .addUsername(member, username)
                            .map(m -> new MemberAsync(m, signer, client));
                });
    }

    /**
     * Logs in an existing member to the system.
     *
     * @param memberId member id
     * @param signer the signer to use
     * @return logged in member
     */
    public Observable<MemberAsync> login(String memberId, Signer signer) {
        Client client = ClientFactory.authenticated(channel, memberId, null, signer);
        return client
                .getMember()
                .map(member -> new MemberAsync(member, signer, client));
    }

    /**
     * Logs in an existing member to the system, using an username.
     *
     * @param username username
     * @param signer the signer to use
     * @return logged in member
     */
    public Observable<MemberAsync> loginWithUsername(String username, Signer signer) {
        Client client = ClientFactory.authenticated(channel, null, username, signer);
        return client
                .getMember()
                .map(member -> new MemberAsync(member, signer, client));
    }

    /**
     * Notifies to link an account.
     *
     * @param username username to notify
     * @param bankId bank ID to link
     * @param bankName bank name to link
     * @param accountLinkPayloads a list of account payloads to be linked
     * @return status of the notification
     */
    public Observable<NotifyStatus> notifyLinkAccounts(
            String username,
            String bankId,
            String bankName,
            List<SealedMessage> accountLinkPayloads) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated
                .notifyLinkAccounts(username, bankId, bankName, accountLinkPayloads);
    }

    /**
     * Notifies to add a key.
     *
     * @param username username to notify
     * @param publicKey public key to add
     * @return status of the notification
     */
    public Observable<NotifyStatus> notifyAddKey(
            String username,
            PublicKey publicKey,
            String name) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated
                .notifyAddKey(username, publicKey, name);
    }

    /**
     * Notifies to link accounts and add a key.
     *
     * @param username username to notify
     * @param bankId bank ID to link
     * @param bankName bank name to link
     * @param accountLinkPayloads a list of account payloads to be linked
     * @param publicKey public key to add
     * @return status of the notification
     */
    public Observable<NotifyStatus> notifyLinkAccountsAndAddKey(
            String username,
            String bankId,
            String bankName,
            List<SealedMessage> accountLinkPayloads,
            PublicKey publicKey,
            String name) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated
                .notifyLinkAccountsAndAddKey(
                        username,
                        bankId,
                        bankName,
                        accountLinkPayloads,
                        publicKey,
                        name);
    }
}