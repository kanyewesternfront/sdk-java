/**
 * Copyright (c) 2017 Token, Inc.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.token;

import static io.token.TokenIO.TokenCluster;
import static io.token.proto.common.member.MemberProtos.CreateMemberType.BUSINESS;
import static io.token.proto.common.member.MemberProtos.CreateMemberType.PERSONAL;
import static io.token.proto.common.member.MemberProtos.CreateMemberType.TRANSIENT;
import static io.token.proto.common.security.SecurityProtos.Key.Level.LOW;
import static io.token.proto.common.security.SecurityProtos.Key.Level.PRIVILEGED;
import static io.token.proto.common.security.SecurityProtos.Key.Level.STANDARD;
import static io.token.util.Util.generateNonce;
import static io.token.util.Util.hashString;
import static io.token.util.Util.normalizeAlias;
import static io.token.util.Util.toAddAliasOperation;
import static io.token.util.Util.toAddAliasOperationMetadata;
import static io.token.util.Util.toAddKeyOperation;
import static io.token.util.Util.toRecoveryAgentOperation;
import static io.token.util.Util.urlEncode;
import static io.token.util.Util.verifySignature;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ManagedChannel;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import io.token.browser.BrowserFactory;
import io.token.exceptions.InvalidStateException;
import io.token.exceptions.VerificationException;
import io.token.proto.common.alias.AliasProtos.Alias;
import io.token.proto.common.bank.BankProtos.Bank;
import io.token.proto.common.blob.BlobProtos.Blob;
import io.token.proto.common.member.MemberProtos;
import io.token.proto.common.member.MemberProtos.CreateMemberType;
import io.token.proto.common.member.MemberProtos.Member;
import io.token.proto.common.member.MemberProtos.MemberOperation;
import io.token.proto.common.member.MemberProtos.MemberOperationMetadata;
import io.token.proto.common.member.MemberProtos.MemberRecoveryOperation;
import io.token.proto.common.member.MemberProtos.MemberRecoveryOperation.Authorization;
import io.token.proto.common.member.MemberProtos.ReceiptContact;
import io.token.proto.common.notification.NotificationProtos.AddKey;
import io.token.proto.common.notification.NotificationProtos.DeviceMetadata;
import io.token.proto.common.notification.NotificationProtos.NotifyStatus;
import io.token.proto.common.security.SecurityProtos.Key;
import io.token.proto.common.token.TokenProtos;
import io.token.proto.common.token.TokenProtos.TokenPayload;
import io.token.proto.common.token.TokenProtos.TokenRequestOptions;
import io.token.rpc.Client;
import io.token.rpc.ClientFactory;
import io.token.rpc.UnauthenticatedClient;
import io.token.security.CryptoEngine;
import io.token.security.CryptoEngineFactory;
import io.token.security.InMemoryKeyStore;
import io.token.security.Signer;
import io.token.security.TokenCryptoEngine;
import io.token.tokenrequest.TokenRequestCallbackParameters;
import io.token.tokenrequest.TokenRequestResult;
import io.token.tokenrequest.TokenRequestState;

import java.io.Closeable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * An SDK Client that interacts with TokenOS.
 *
 * <p>The class provides async API with {@link TokenIO} providing a synchronous
 * version. {@link TokenIO} instance can be obtained by calling {@link #sync}
 * method.</p>
 */
public class TokenIOAsync implements Closeable {
    private static final String TOKEN_REQUEST_TEMPLATE =
            "https://%s/request-token/%s?state=%s";
    private static final long SHUTDOWN_DURATION_MS = 10000L;

    private final ManagedChannel channel;
    private final CryptoEngineFactory cryptoFactory;
    private final String devKey;
    private final TokenCluster tokenCluster;
    private final BrowserFactory browserFactory;

    /**
     * Creates an instance of a Token SDK.
     *
     * @param channel GRPC channel
     * @param cryptoFactory crypto factory instance
     * @param developerKey developer key
     * @param tokenCluster token cluster
     */
    TokenIOAsync(
            ManagedChannel channel,
            CryptoEngineFactory cryptoFactory,
            String developerKey,
            TokenCluster tokenCluster,
            BrowserFactory browserFactory) {
        this.channel = channel;
        this.cryptoFactory = cryptoFactory;
        this.devKey = developerKey;
        this.tokenCluster = tokenCluster;
        this.browserFactory = browserFactory;
    }

    @Override
    public void close() {
        channel.shutdown();

        try {
            channel.awaitTermination(SHUTDOWN_DURATION_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns a sync version of the API.
     *
     * @return synchronous version of the account API
     */
    public TokenIO sync() {
        return new TokenIO(this, devKey);
    }

    /**
     * Checks if a given alias already exists.
     *
     * @param alias alias to check
     * @return {@code true} if alias exists, {@code false} otherwise
     */
    public Observable<Boolean> aliasExists(Alias alias) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.aliasExists(alias);
    }

    /**
     * Looks up member id for a given alias.
     *
     * @param alias alias to check
     * @return member id, or throws exception if member not found
     */
    public Observable<String> getMemberId(Alias alias) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getMemberId(alias);
    }

    /**
     * Creates a new Token member with a set of auto-generated keys, an alias, and member type.
     *
     * @param alias nullable member alias to use, must be unique. If null, then no alias will
     *     be created with the member.
     * @param memberType the type of member to register
     * @return newly created member
     */
    public Observable<MemberAsync> createMember(
            Alias alias,
            CreateMemberType memberType) {
        return createMember(alias, memberType, null);
    }

    /**
     * Creates a new Token member with a set of auto-generated keys, an alias, and member type.
     *
     * @param alias nullable member alias to use, must be unique. If null, then no alias will
     *     be created with the member.
     * @param memberType the type of member to register
     * @param tokenRequestId (optional) token request id. If used, then the member will be claimed
     *     by the creator of the corresponding token request. Only works if memberType == TRANSIENT.
     * @return newly created member
     */
    public Observable<MemberAsync> createMember(
            final Alias alias,
            final CreateMemberType memberType,
            @Nullable final String tokenRequestId) {
        final UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated
                .createMemberId(memberType, tokenRequestId)
                .flatMap(new Function<String, Observable<MemberAsync>>() {
                    public Observable<MemberAsync> apply(String memberId) {
                        return setupMember(alias, memberId);
                    }
                });
    }

    /**
     * Creates a new personal-use Token member with a set of auto-generated keys and no alias.
     *
     * @return newly created member
     */
    public Observable<MemberAsync> createMember() {
        return createMember(null, PERSONAL, null);
    }

    /**
     * Creates a new personal-use Token member with a set of auto-generated keys and and an alias.
     *
     * @param alias alias to associate with member
     * @return newly created member
     */
    public Observable<MemberAsync> createMember(Alias alias) {
        return createMember(alias, PERSONAL, null);
    }

    /**
     * Creates a new transient Token member and claims it for the creator of the token request
     * corresponding to the given token request ID.
     *
     * @param tokenRequestId token request id
     * @return newly created member
     */
    public Observable<MemberAsync> createClaimedMember(String tokenRequestId) {
        return createMember(null, TRANSIENT, tokenRequestId);
    }

    /**
     * Creates a new business-use Token member with a set of auto-generated keys and alias.
     *
     * @param alias alias to associated with member
     * @return newly created member
     */
    public Observable<MemberAsync> createBusinessMember(Alias alias) {
        return createMember(alias, BUSINESS, null);
    }


    /**
     * Sets up a member given a specific ID of a member that already exists in the system. If
     * the member ID already has keys, this will not succeed. Used for testing since this
     * gives more control over the member creation process.
     *
     * <p>Adds an alias and a set of auto-generated keys to the member.</p>
     *
     * @param alias nullable member alias to use, must be unique. If null, then no alias will
     *     be created with the member
     * @param memberId member id
     * @return newly created member
     */
    @VisibleForTesting
    public Observable<MemberAsync> setupMember(final Alias alias, final String memberId) {
        final UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getDefaultAgent()
                .flatMap(new Function<String, Observable<MemberProtos.Member>>() {
                    public Observable<MemberProtos.Member> apply(String agentId) {
                        CryptoEngine crypto = cryptoFactory.create(memberId);
                        List<MemberOperation> operations = new ArrayList<>();
                        operations.add(
                                toAddKeyOperation(crypto.generateKey(PRIVILEGED)));
                        operations.add(
                                toAddKeyOperation(crypto.generateKey(STANDARD)));
                        operations.add(
                                toAddKeyOperation(crypto.generateKey(LOW)));
                        operations.add(toRecoveryAgentOperation(agentId));

                        if (alias != null) {
                            operations.add(toAddAliasOperation(
                                    normalizeAlias(alias)));
                        }
                        List<MemberOperationMetadata> metadata = alias == null
                                ? Collections.<MemberOperationMetadata>emptyList()
                                : singletonList(toAddAliasOperationMetadata(
                                        normalizeAlias(alias)));
                        Signer signer = crypto.createSigner(PRIVILEGED);
                        return unauthenticated.createMember(
                                memberId,
                                operations,
                                metadata,
                                signer);
                    }
                })
                .flatMap(new Function<MemberProtos.Member, Observable<MemberAsync>>() {
                    public Observable<MemberAsync> apply(MemberProtos.Member member) {
                        CryptoEngine crypto = cryptoFactory.create(member.getId());
                        Client client = ClientFactory.authenticated(
                                channel,
                                member.getId(),
                                crypto);
                        return Observable.just(new MemberAsync(
                                member,
                                client,
                                tokenCluster,
                                browserFactory));
                    }
                });
    }

    /**
     * Provisions a new device for an existing user. The call generates a set
     * of keys that are returned back. The keys need to be approved by an
     * existing device/keys.
     *
     * @param alias member id to provision the device for
     * @return device information
     */
    public Observable<DeviceInfo> provisionDevice(Alias alias) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated
                .getMemberId(alias)
                .map(new Function<String, DeviceInfo>() {
                    public DeviceInfo apply(String memberId) {
                        CryptoEngine crypto = cryptoFactory.create(memberId);
                        return new DeviceInfo(
                                memberId,
                                asList(
                                        crypto.generateKey(PRIVILEGED),
                                        crypto.generateKey(STANDARD),
                                        crypto.generateKey(LOW)));
                    }
                });
    }

    /**
     * Return a Member set up to use some Token member's keys (assuming we have them).
     *
     * @param memberId member id
     * @return member
     */
    public Observable<MemberAsync> getMember(String memberId) {
        CryptoEngine crypto = cryptoFactory.create(memberId);
        final Client client = ClientFactory.authenticated(channel, memberId, crypto);
        return client
                .getMember(memberId)
                .map(new Function<MemberProtos.Member, MemberAsync>() {
                    public MemberAsync apply(MemberProtos.Member member) {
                        return new MemberAsync(member, client, tokenCluster, browserFactory);
                    }
                });
    }

    /**
     * Updates an existing token request.
     *
     * @param requestId token request ID
     * @param options new token request options
     * @return completable
     */
    public Completable updateTokenRequest(String requestId, TokenRequestOptions options) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.updateTokenRequest(requestId, options);
    }

    /**
     * Return a TokenRequest that was previously stored.
     *
     * @param requestId request id
     * @return token request that was stored with the request id
     */
    public Observable<TokenRequest> retrieveTokenRequest(String requestId) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.retrieveTokenRequest(requestId);
    }

    /**
     * Notifies to add a key.
     *
     * @param alias alias to notify
     * @param keys keys that need approval
     * @param deviceMetadata device metadata of the keys
     * @return status of the notification
     */
    public Observable<NotifyStatus> notifyAddKey(
            Alias alias,
            List<Key> keys,
            DeviceMetadata deviceMetadata) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        AddKey addKey = AddKey.newBuilder()
                .addAllKeys(keys)
                .setDeviceMetadata(deviceMetadata)
                .build();
        return unauthenticated.notifyAddKey(alias, addKey);
    }

    /**
     * Sends a notification to request a payment.
     *
     * @param tokenPayload the payload of a token to be sent
     * @return status of the notification request
     */
    public Observable<NotifyStatus> notifyPaymentRequest(TokenPayload tokenPayload) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        if (tokenPayload.getRefId().isEmpty()) {
            tokenPayload = tokenPayload.toBuilder().setRefId(generateNonce()).build();
        }
        return unauthenticated.notifyPaymentRequest(tokenPayload);
    }

    /**
     * Notifies subscribed devices that a token should be created and endorsed.
     *
     * @param tokenRequestId the token request ID to send
     * @param keys keys to be added
     * @param deviceMetadata device metadata of the keys
     * @param receiptContact optional receipt contact to send
     * @return notify result of the notification request
     */
    public Observable<NotifyResult> notifyCreateAndEndorseToken(
            String tokenRequestId,
            @Nullable List<Key> keys,
            @Nullable DeviceMetadata deviceMetadata,
            @Nullable ReceiptContact receiptContact) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.notifyCreateAndEndorseToken(
                tokenRequestId,
                AddKey.newBuilder()
                        .addAllKeys(keys)
                        .setDeviceMetadata(deviceMetadata)
                        .build(),
                receiptContact);
    }

    /**
     * Notifies subscribed devices that a token payload should be endorsed and keys should be
     * added.
     *
     * @param tokenPayload the token payload to be sent
     * @param keys keys to be added
     * @param deviceMetadata device metadata of the keys
     * @param tokenRequestId optional token request id
     * @param bankId optional bank id
     * @param state optional token request state for signing
     * @param receiptContact optional receipt contact
     * @return notify result of the notification request
     * @deprecated use notifyCreateAndEndorseToken instead
     */
    @Deprecated
    public Observable<NotifyResult> notifyEndorseAndAddKey(
            TokenPayload tokenPayload,
            List<Key> keys,
            DeviceMetadata deviceMetadata,
            @Nullable String tokenRequestId,
            @Nullable String bankId,
            @Nullable String state,
            @Nullable ReceiptContact receiptContact) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.notifyEndorseAndAddKey(
                tokenPayload,
                AddKey.newBuilder()
                        .addAllKeys(keys)
                        .setDeviceMetadata(deviceMetadata)
                        .build(),
                tokenRequestId,
                bankId,
                state,
                receiptContact);
    }

    /**
     * Invalidate a notification.
     *
     * @param notificationId notification id to invalidate
     * @return status of the invalidation request
     */
    public Observable<NotifyStatus> invalidateNotification(String notificationId) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.invalidateNotification(notificationId);
    }

    /**
     * Retrieves a blob from the server.
     *
     * @param blobId id of the blob
     * @return Blob
     */
    public Observable<Blob> getBlob(String blobId) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getBlob(blobId);
    }

    /**
     * Begins account recovery.
     *
     * @param alias the alias used to recover
     * @return the verification id
     */
    public Observable<String> beginRecovery(Alias alias) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.beginRecovery(alias);
    }

    /**
     * Create a recovery authorization for some agent to sign.
     *
     * @param memberId Id of member we claim to be.
     * @param privilegedKey new privileged key we want to use.
     * @return authorization structure for agent to sign
     */
    public Observable<Authorization> createRecoveryAuthorization(
            String memberId,
            Key privilegedKey) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.createRecoveryAuthorization(memberId, privilegedKey);
    }

    /**
     * Gets recovery authorization from Token.
     *
     * @param verificationId the verification id
     * @param code the code
     * @param key the privileged key
     * @return the member recovery operation
     * @throws VerificationException if the code verification fails
     */
    public Observable<MemberRecoveryOperation> getRecoveryAuthorization(
            String verificationId,
            String code,
            Key key) throws VerificationException {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getRecoveryAuthorization(verificationId, code, key);
    }

    /**
     * Completes account recovery.
     *
     * @param memberId the member id
     * @param recoveryOperations the member recovery operations
     * @param privilegedKey the privileged public key in the member recovery operations
     * @param cryptoEngine the new crypto engine
     * @return an observable of the updated member
     */
    public Observable<MemberAsync> completeRecovery(
            String memberId,
            List<MemberRecoveryOperation> recoveryOperations,
            Key privilegedKey,
            final CryptoEngine cryptoEngine) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated
                .completeRecovery(memberId, recoveryOperations, privilegedKey, cryptoEngine)
                .map(new Function<MemberProtos.Member, MemberAsync>() {
                    public MemberAsync apply(MemberProtos.Member member) throws Exception {
                        Client client = ClientFactory.authenticated(
                                channel,
                                member.getId(),
                                cryptoEngine);
                        return new MemberAsync(member, client, tokenCluster, browserFactory);
                    }
                });
    }

    /**
     * Completes account recovery if the default recovery rule was set.
     *
     * @param memberId the member id
     * @param verificationId the verification id
     * @param code the code
     * @return the new member
     */
    public Observable<MemberAsync> completeRecoveryWithDefaultRule(
            String memberId,
            String verificationId,
            String code) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        final CryptoEngine cryptoEngine = new TokenCryptoEngine(memberId, new InMemoryKeyStore());
        return unauthenticated
                .completeRecoveryWithDefaultRule(memberId, verificationId, code, cryptoEngine)
                .map(new Function<MemberProtos.Member, MemberAsync>() {
                    public MemberAsync apply(MemberProtos.Member member) throws Exception {
                        Client client = ClientFactory.authenticated(
                                channel,
                                member.getId(),
                                cryptoEngine);
                        return new MemberAsync(member, client, tokenCluster, browserFactory);
                    }
                });
    }

    /**
     * Returns a list of token enabled banks.
     *
     * @param bankIds If specified, return banks whose 'id' matches any one of the given ids
     *     (case-insensitive). Can be at most 1000.
     * @param page Result page to retrieve. Default to 1 if not specified.
     * @param perPage Maximum number of records per page. Can be at most 200. Default to 200
     *     if not specified.
     * @return a list of banks
     */
    public Observable<List<Bank>> getBanks(
            @Nullable List<String> bankIds,
            @Nullable Integer page,
            @Nullable Integer perPage)  {
        return getBanks(bankIds, null, null, page, perPage, null, null);
    }

    /**
     * Returns a list of token enabled banks.
     *
     * @param search If specified, return banks whose 'name' or 'identifier' contains the given
     *     search string (case-insensitive)
     * @param country If specified, return banks whose 'country' matches the given ISO 3166-1
     *     alpha-2 country code (case-insensitive)
     * @param page Result page to retrieve. Default to 1 if not specified.
     * @param perPage Maximum number of records per page. Can be at most 200. Default to 200
     *     if not specified.
     * @param sort The key to sort the results. Could be one of: name, provider and country.
     *     Defaults to name if not specified.
     * @return a list of banks
     */
    @Deprecated
    public Observable<List<Bank>> getBanks(
            @Nullable String search,
            @Nullable String country,
            @Nullable Integer page,
            @Nullable Integer perPage,
            @Nullable String sort) {
        return getBanks(null, search, country, page, perPage, sort, null);
    }

    /**
     * Returns a list of token enabled banks.
     *
     * @param search If specified, return banks whose 'name' or 'identifier' contains the given
     *     search string (case-insensitive)
     * @param country If specified, return banks whose 'country' matches the given ISO 3166-1
     *     alpha-2 country code (case-insensitive)
     * @param page Result page to retrieve. Default to 1 if not specified.
     * @param perPage Maximum number of records per page. Can be at most 200. Default to 200
     *     if not specified.
     * @param sort The key to sort the results. Could be one of: name, provider and country.
     *     Defaults to name if not specified.
     * @param provider If specified, return banks whose 'provider' matches the given provider
     *     (case insensitive).
     * @return a list of banks
     */
    public Observable<List<Bank>> getBanks(
            @Nullable String search,
            @Nullable String country,
            @Nullable Integer page,
            @Nullable Integer perPage,
            @Nullable String sort,
            @Nullable String provider) {
        return getBanks(null, search, country, page, perPage, sort, provider);
    }

    /**
     * Returns a list of token enabled banks.
     *
     * @param bankIds If specified, return banks whose 'id' matches any one of the given ids
     *     (case-insensitive). Can be at most 1000.
     * @param search If specified, return banks whose 'name' or 'identifier' contains the given
     *     search string (case-insensitive)
     * @param country If specified, return banks whose 'country' matches the given ISO 3166-1
     *     alpha-2 country code (case-insensitive)
     * @param page Result page to retrieve. Default to 1 if not specified.
     * @param perPage Maximum number of records per page. Can be at most 200. Default to 200
     *     if not specified.
     * @param sort The key to sort the results. Could be one of: name, provider and country.
     *     Defaults to name if not specified.
     * @return a list of banks
     */
    @Deprecated
    public Observable<List<Bank>> getBanks(
            @Nullable List<String> bankIds,
            @Nullable String search,
            @Nullable String country,
            @Nullable Integer page,
            @Nullable Integer perPage,
            @Nullable String sort) {
        return getBanks(bankIds, search, country, page, perPage, sort, null);
    }

    /**
     * Returns a list of token enabled banks.
     *
     * @param bankIds If specified, return banks whose 'id' matches any one of the given ids
     *     (case-insensitive). Can be at most 1000.
     * @param search If specified, return banks whose 'name' or 'identifier' contains the given
     *     search string (case-insensitive)
     * @param country If specified, return banks whose 'country' matches the given ISO 3166-1
     *     alpha-2 country code (case-insensitive)
     * @param page Result page to retrieve. Default to 1 if not specified.
     * @param perPage Maximum number of records per page. Can be at most 200. Default to 200
     *     if not specified.
     * @param sort The key to sort the results. Could be one of: name, provider and country.
     *     Defaults to name if not specified.
     * @param provider If specified, return banks whose 'provider' matches the given provider
     *     (case insensitive).
     * @return a list of banks
     */
    public Observable<List<Bank>> getBanks(
            @Nullable List<String> bankIds,
            @Nullable String search,
            @Nullable String country,
            @Nullable Integer page,
            @Nullable Integer perPage,
            @Nullable String sort,
            @Nullable String provider)  {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getBanks(bankIds, search, country, page, perPage, sort, provider);
    }

    /**
     * Returns a list of token enabled countries for banks.
     *
     * @param provider If specified, return banks whose 'provider' matches the given provider
     *     (case insensitive).
     * @return a list of country codes
     */
    public Observable<List<String>> getBanksCountries(String provider) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getBanksCountries(provider);
    }

    /**
     * Generate a Token request URL from a request ID, and state. This does not set a CSRF token
     * or pass in a state.
     *
     * @param requestId request id
     * @return token request url
     */
    public Observable<String> generateTokenRequestUrl(String requestId) {
        return generateTokenRequestUrl(requestId, "", "");
    }

    /**
     * Generate a Token request URL from a request ID, and state. This does not set a CSRF token.
     *
     * @param requestId request id
     * @param state state
     * @return token request url
     */
    public Observable<String> generateTokenRequestUrl(
            String requestId,
            String state) {
        return generateTokenRequestUrl(requestId, state, "");
    }

    /**
     * Generate a Token request URL from a request ID, a state, and a CSRF token.
     *
     * @param requestId request id
     * @param state state
     * @param csrfToken csrf token
     * @return token request url
     */
    public Observable<String> generateTokenRequestUrl(
            String requestId,
            String state,
            String csrfToken) {
        String csrfTokenHash = hashString(csrfToken);
        TokenRequestState tokenRequestState = TokenRequestState.create(csrfTokenHash, state);
        return Observable.just(format(TOKEN_REQUEST_TEMPLATE,
                        tokenCluster.webAppUrl(),
                        requestId,
                        urlEncode(tokenRequestState.serialize())));
    }

    /**
     * Parse the token request callback URL to extract the state and the token ID. This assumes
     * that no CSRF token was set.
     *
     * @param callbackUrl token request callback url
     * @return TokenRequestCallback object containing the token id and the original state
     */
    public Observable<TokenRequestCallback> parseTokenRequestCallbackUrl(final String callbackUrl) {
        return parseTokenRequestCallbackUrl(callbackUrl, "");
    }

    /**
     * Parse the token request callback URL to extract the state and the token ID. Verify that the
     * state contains the CSRF token hash and that the signature on the state and CSRF token is
     * valid.
     *
     * @param callbackUrl token request callback url
     * @param csrfToken csrfToken
     * @return TokenRequestCallback object containing the token id and the original state
     */
    public Observable<TokenRequestCallback> parseTokenRequestCallbackUrl(
            final String callbackUrl,
            final String csrfToken) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getTokenMember().map(new Function<Member, TokenRequestCallback>() {
            @Override
            public TokenRequestCallback apply(Member tokenMember) throws Exception {
                TokenRequestCallbackParameters params = TokenRequestCallbackParameters
                        .create(new URL(callbackUrl).getQuery());

                // check that csrf token hashes match
                TokenRequestState state = TokenRequestState.parse(params.getSerializedState());
                if (!state.getCsrfTokenHash().equals(hashString(csrfToken))) {
                    throw new InvalidStateException(csrfToken);
                }

                verifySignature(
                        tokenMember,
                        TokenProtos.TokenRequestStatePayload.newBuilder()
                                .setTokenId(params.getTokenId())
                                .setState(urlEncode(params.getSerializedState()))
                                .build(),
                        params.getSignature());

                return TokenRequestCallback.create(params.getTokenId(), state.getInnerState());
            }
        });
    }

    /**
     * Get the token request result based on a token's tokenRequestId.
     *
     * @param tokenRequestId token request id
     * @return token request result
     */
    public Observable<TokenRequestResult> getTokenRequestResult(String tokenRequestId) {
        UnauthenticatedClient unauthenticated = ClientFactory.unauthenticated(channel);
        return unauthenticated.getTokenRequestResult(tokenRequestId);
    }
}
