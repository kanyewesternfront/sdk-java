/**
 * Copyright (c) 2019 Token, Inc.
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

package io.token.tpp;

import static io.token.proto.common.blob.BlobProtos.Blob.AccessMode.DEFAULT;
import static io.token.proto.common.blob.BlobProtos.Blob.AccessMode.PUBLIC;
import static io.token.proto.gateway.Gateway.GetTokensRequest.Type.ACCESS;
import static io.token.proto.gateway.Gateway.GetTokensRequest.Type.TRANSFER;
import static io.token.util.Util.generateNonce;

import com.google.protobuf.ByteString;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import io.token.Account;
import io.token.TokenClient.TokenCluster;
import io.token.proto.PagedList;
import io.token.proto.common.blob.BlobProtos.Attachment;
import io.token.proto.common.blob.BlobProtos.Blob;
import io.token.proto.common.blob.BlobProtos.Blob.Payload;
import io.token.proto.common.member.MemberProtos;
import io.token.proto.common.member.MemberProtos.ProfilePictureSize;
import io.token.proto.common.token.TokenProtos.Token;
import io.token.proto.common.token.TokenProtos.TokenOperationResult;
import io.token.proto.common.transfer.TransferProtos;
import io.token.proto.common.transfer.TransferProtos.Transfer;
import io.token.proto.common.transferinstructions.TransferInstructionsProtos.TransferEndpoint;
import io.token.tokenrequest.TokenRequest;
import io.token.tpp.rpc.Client;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Member in the Token system. Each member has an active secret
 * and public key pair that is used to perform authentication.
 */
public class Member extends io.token.Member implements Representable {
    private static final Logger logger = LoggerFactory.getLogger(Member.class);
    private final Client client;

    /**
     * Creates an instance of {@link Member}.
     *
     * @param member internal member representation, fetched from server
     * @param client RPC client used to perform operations against the server
     * @param cluster Token cluster, e.g. sandbox, production
     */
    Member(
            MemberProtos.Member member,
            Client client,
            TokenCluster cluster) {
        super(member, client, cluster);
        this.client = client;
    }

    Member(io.token.Member member, Client client) {
        super(member);
        this.client = client;
    }

    /**
     * Replaces auth'd member's public profile.
     *
     * @param profile profile to set
     * @return updated profile
     */
    public Observable<MemberProtos.Profile> setProfile(MemberProtos.Profile profile) {
        return client.setProfile(profile);
    }

    /**
     * Replaces the authenticated member's public profile.
     *
     * @param profile Profile to set
     * @return updated profile
     */
    public MemberProtos.Profile setProfileBlocking(MemberProtos.Profile profile) {
        return setProfile(profile).blockingSingle();
    }

    /**
     * Gets a member's public profile. Unlike setProfile, you can get another member's profile.
     *
     * @param memberId member ID of member whose profile we want
     * @return their profile
     */
    public Observable<MemberProtos.Profile> getProfile(String memberId) {
        return client.getProfile(memberId);
    }

    /**
     * Gets a member's public profile.
     *
     * @param memberId member ID of member whose profile we want
     * @return profile info
     */
    public MemberProtos.Profile getProfileBlocking(String memberId) {
        return getProfile(memberId).blockingSingle();
    }

    /**
     * Replaces auth'd member's public profile picture.
     *
     * @param type MIME type of picture
     * @param data image data
     * @return completable that indicates whether the operation finished or had an error
     */
    public Completable setProfilePicture(final String type, byte[] data) {
        Payload payload = Payload.newBuilder()
                .setOwnerId(memberId())
                .setType(type)
                .setName("profile")
                .setData(ByteString.copyFrom(data))
                .setAccessMode(PUBLIC)
                .build();
        return client.setProfilePicture(payload);
    }

    /**
     * Replaces auth'd member's public profile picture.
     *
     * @param type MIME type of picture
     * @param data image data
     */
    public void setProfilePictureBlocking(final String type, byte[] data) {
        setProfilePicture(type, data).blockingAwait();
    }

    /**
     * Gets a member's public profile picture. Unlike set, you can get another member's picture.
     *
     * @param memberId member ID of member whose profile we want
     * @param size desired size category (small, medium, large, original)
     * @return blob with picture; empty blob (no fields set) if has no picture
     */
    public Observable<Blob> getProfilePicture(String memberId, ProfilePictureSize size) {
        return client.getProfilePicture(memberId, size);
    }

    /**
     * Gets a member's public profile picture.
     *
     * @param memberId member ID of member whose profile we want
     * @param size Size category desired (small/medium/large/original)
     * @return blob with picture; empty blob (no fields set) if has no picture
     */
    public Blob getProfilePictureBlocking(String memberId, ProfilePictureSize size) {
        return getProfilePicture(memberId, size).blockingSingle();
    }

    /**
     * Links a funding bank account to Token and returns it to the caller.
     *
     * @return list of accounts
     */
    public Observable<List<Account>> getAccounts() {
        return super.getAccountsImpl();
    }

    /**
     * Looks up funding bank accounts linked to Token.
     *
     * @return list of linked accounts
     */
    public List<Account> getAccountsBlocking() {
        return getAccounts().blockingSingle();
    }

    /**
     * Looks up a funding bank account linked to Token.
     *
     * @param accountId account id
     * @return looked up account
     */
    public Observable<Account> getAccount(String accountId) {
        return getAccountImpl(accountId);
    }

    /**
     * Looks up a funding bank account linked to Token.
     *
     * @param accountId account id
     * @return looked up account
     */
    public Account getAccountBlocking(String accountId) {
        return getAccount(accountId).blockingSingle();
    }

    /**
     * Creates and uploads a blob.
     *
     * @param ownerId id of the owner of the blob
     * @param type MIME type of the file
     * @param name name
     * @param data file data
     * @return attachment
     */
    public Observable<Attachment> createAttachment(
            String ownerId,
            final String type,
            final String name,
            byte[] data) {
        return createAttachment(ownerId, type, name, data, DEFAULT);
    }

    /**
     * Creates and uploads a blob.
     *
     * @param ownerId id of the owner of the blob
     * @param type MIME type of the file
     * @param name name
     * @param data file data
     * @param accessMode Normal access or public
     * @return attachment
     */
    public Observable<Attachment> createAttachment(
            String ownerId,
            final String type,
            final String name,
            byte[] data,
            Blob.AccessMode accessMode) {
        Payload payload = Payload
                .newBuilder()
                .setOwnerId(ownerId)
                .setType(type)
                .setName(name)
                .setData(ByteString.copyFrom(data))
                .setAccessMode(accessMode)
                .build();
        return client.createBlob(payload)
                .map(new Function<String, Attachment>() {
                    public Attachment apply(String id) {
                        return Attachment.newBuilder()
                                .setBlobId(id)
                                .setName(name)
                                .setType(type)
                                .build();
                    }
                });
    }

    /**
     * Creates and uploads a blob.
     *
     * @param ownerId id of the owner of the blob
     * @param type MIME type of the file
     * @param name name
     * @param data file data
     * @return attachment
     */
    public Attachment createAttachmentBlocking(
            String ownerId,
            final String type,
            final String name,
            byte[] data) {
        return createAttachment(ownerId, type, name, data).blockingSingle();
    }

    /**
     * Creates and uploads a blob.
     *
     * @param ownerId id of the owner of the blob
     * @param type MIME type of the file
     * @param name name
     * @param data file data
     * @param accessMode Normal access or public
     * @return attachment
     */
    public Attachment createAttachmentBlocking(
            String ownerId,
            final String type,
            final String name,
            byte[] data,
            Blob.AccessMode accessMode) {
        return createAttachment(ownerId, type, name, data, accessMode).blockingSingle();
    }

    /**
     * Retrieves a blob from the server.
     *
     * @param blobId id of the blob
     * @return Blob
     */
    public Observable<Blob> getBlob(String blobId) {
        return client.getBlob(blobId);
    }

    /**
     * Retrieves a blob from the server.
     *
     * @param blobId id of the blob
     * @return Blob
     */
    public Blob getBlobBlocking(String blobId) {
        return getBlob(blobId).blockingSingle();
    }

    /**
     * Creates a {@link Representable} that acts as another member using the access token
     * that was granted by that member.
     *
     * @param tokenId the token id
     * @return the {@link Representable}
     */
    public Representable forAccessToken(String tokenId) {
        return forAccessToken(tokenId, false);
    }

    /**
     * Creates a {@link Representable} that acts as another member using the access token
     * that was granted by that member.
     *
     * @param tokenId the token id
     * @param customerInitiated whether the call is initiated by the customer
     * @return the {@link Representable}
     */
    public Representable forAccessToken(String tokenId, boolean customerInitiated) {
        return forAccessTokenInternal(tokenId, customerInitiated);
    }

    Member forAccessTokenInternal(String tokenId, boolean customerInitiated) {
        Client cloned = client.forAccessToken(tokenId, customerInitiated);
        return new Member(member.build(), cloned, cluster);
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @return transfer record
     */
    public Observable<Transfer> redeemToken(Token token) {
        return redeemToken(token, null, null, null, null, null);
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param refId transfer reference id
     * @return transfer record
     */
    public Observable<Transfer> redeemToken(Token token, String refId) {
        return redeemToken(token, null, null, null, null, refId);
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param destination transfer instruction destination
     * @return transfer record
     */
    public Observable<Transfer> redeemToken(Token token, TransferEndpoint destination) {
        return redeemToken(token, null, null, null, destination, null);
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param destination transfer instruction destination
     * @param refId transfer reference id
     * @return transfer record
     */
    public Observable<Transfer> redeemToken(
            Token token,
            TransferEndpoint destination,
            String refId) {
        return redeemToken(token, null, null, null, destination, refId);
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param amount transfer amount
     * @param currency transfer currency code, e.g. "EUR"
     * @param description transfer description
     * @param destination the transfer instruction destination
     * @param refId transfer reference id
     * @return transfer record
     */
    public Observable<Transfer> redeemToken(
            Token token,
            @Nullable Double amount,
            @Nullable String currency,
            @Nullable String description,
            @Nullable TransferEndpoint destination,
            @Nullable String refId) {
        TransferProtos.TransferPayload.Builder payload = TransferProtos.TransferPayload.newBuilder()
                .setTokenId(token.getId())
                .setDescription(token
                        .getPayload()
                        .getDescription());

        if (destination != null) {
            payload.addDestinations(destination);
        }
        if (amount != null) {
            payload.getAmountBuilder().setValue(Double.toString(amount));
        }
        if (currency != null) {
            payload.getAmountBuilder().setCurrency(currency);
        }
        if (description != null) {
            payload.setDescription(description);
        }
        if (refId != null) {
            payload.setRefId(refId);
        } else {
            logger.warn("refId is not set. A random ID will be used.");
            payload.setRefId(generateNonce());
        }

        return client.createTransfer(payload.build());
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @return transfer record
     */
    public Transfer redeemTokenBlocking(Token token) {
        return redeemToken(token).blockingSingle();
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param refId transfer reference id
     * @return transfer record
     */
    public Transfer redeemTokenBlocking(Token token, String refId) {
        return redeemToken(token, refId).blockingSingle();
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param destination transfer instruction destination
     * @return transfer record
     */
    public Transfer redeemTokenBlocking(Token token, TransferEndpoint destination) {
        return redeemToken(token, destination).blockingSingle();
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param destination transfer instruction destination
     * @param refId transfer reference id
     * @return transfer record
     */
    public Transfer redeemTokenBlocking(Token token, TransferEndpoint destination, String refId) {
        return redeemToken(token, destination, refId).blockingSingle();
    }

    /**
     * Redeems a transfer token.
     *
     * @param token transfer token to redeem
     * @param amount transfer amount
     * @param currency transfer currency code, e.g. "EUR"
     * @param description transfer description
     * @param destination transfer instruction destination
     * @param refId transfer reference id
     * @return transfer record
     */
    public Transfer redeemTokenBlocking(
            Token token,
            @Nullable Double amount,
            @Nullable String currency,
            @Nullable String description,
            @Nullable TransferEndpoint destination,
            @Nullable String refId) {
        return redeemToken(token, amount, currency, description, destination, refId)
                .blockingSingle();
    }

    /**
     * Stores a token request. This can be retrieved later by the token request id.
     *
     * @param tokenRequest token request
     * @return token request id
     */
    public Observable<String> storeTokenRequest(TokenRequest tokenRequest) {
        return client.storeTokenRequest(
                tokenRequest.getTokenRequestPayload(),
                tokenRequest.getTokenRequestOptions());
    }

    /**
     * Stores a token request to be retrieved later (possibly by another member).
     *
     * @param tokenRequest token request
     * @return ID to reference the stored token request
     */
    public String storeTokenRequestBlocking(TokenRequest tokenRequest) {
        return storeTokenRequest(tokenRequest).blockingSingle();
    }

    /**
     * Creates a customization.
     *
     * @param logo logo
     * @param colors map of ARGB colors #AARRGGBB
     * @param consentText consent text
     * @param name display name
     * @param appName corresponding app name
     * @return customization id
     */
    public Observable<String> createCustomization(
            Payload logo,
            Map<String, String> colors,
            String consentText,
            String name,
            String appName) {
        return client.createCustomization(logo, colors, consentText, name, appName);
    }

    /**
     * Creates a customization.
     *
     * @param logo logo
     * @param colors map of ARGB colors #AARRGGBB
     * @param consentText consent text
     * @param name display name
     * @param appName corresponding app name
     * @return customization id
     */
    public String createCustomizationBlocking(
            Payload logo,
            Map<String, String> colors,
            String consentText,
            String name,
            String appName) {
        return createCustomization(logo, colors, consentText, name, appName).blockingSingle();
    }

    /**
     * Looks up an existing token transfer.
     *
     * @param transferId ID of the transfer record
     * @return transfer record
     */
    public Observable<Transfer> getTransfer(String transferId) {
        return client.getTransfer(transferId);
    }

    /**
     * Looks up an existing token transfer.
     *
     * @param transferId ID of the transfer record
     * @return transfer record
     */
    public Transfer getTransferBlocking(String transferId) {
        return getTransfer(transferId).blockingSingle();
    }

    /**
     * Looks up existing token transfers.
     *
     * @param offset optional offset to start at
     * @param limit max number of records to return
     * @param tokenId optional token id to restrict the search
     * @return transfer record
     */
    public Observable<PagedList<Transfer, String>> getTransfers(
            @Nullable String offset,
            int limit,
            @Nullable String tokenId) {
        return client.getTransfers(offset, limit, tokenId);
    }

    /**
     * Looks up existing token transfers.
     *
     * @param offset optional offset to start at
     * @param limit max number of records to return
     * @param tokenId optional token id to restrict the search
     * @return transfer record
     */
    public PagedList<Transfer, String> getTransfersBlocking(
            @Nullable String offset,
            int limit,
            @Nullable String tokenId) {
        return getTransfers(offset, limit, tokenId).blockingSingle();
    }

    /**
     * Looks up a existing access token where the calling member is the grantor and given member is
     * the grantee.
     *
     * @param toMemberId beneficiary of the active access token
     * @return access token returned by the server
     */
    public Observable<Token> getActiveAccessToken(String toMemberId) {
        return client.getActiveAccessToken(toMemberId);
    }

    /**
     * Looks up a existing access token where the calling member is the grantor and given member is
     * the grantee.
     *
     * @param toMemberId beneficiary of the active access token
     * @return access token returned by the server
     */
    public Token getActiveAccessTokenBlocking(String toMemberId) {
        return getActiveAccessToken(toMemberId).blockingSingle();
    }

    /**
     * Looks up access tokens owned by the member.
     *
     * @param offset optional offset to start at
     * @param limit max number of records to return
     * @return transfer tokens owned by the member
     */
    public Observable<PagedList<Token, String>> getAccessTokens(
            @Nullable String offset,
            int limit) {
        return client.getTokens(ACCESS, offset, limit);
    }

    /**
     * Looks up tokens owned by the member.
     *
     * @param offset optional offset offset to start at
     * @param limit max number of records to return
     * @return transfer tokens owned by the member
     */
    public PagedList<Token, String> getAccessTokensBlocking(@Nullable String offset, int limit) {
        return getAccessTokens(offset, limit).blockingSingle();
    }


    /**
     * Looks up transfer tokens owned by the member.git st
     *
     * @param offset optional offset to start at
     * @param limit max number of records to return
     * @return transfer tokens owned by the member
     */
    public Observable<PagedList<Token, String>> getTransferTokens(
            @Nullable String offset,
            int limit) {
        return client.getTokens(TRANSFER, offset, limit);
    }

    /**
     * Looks up tokens owned by the member.
     *
     * @param offset optional offset to start at
     * @param limit max number of records to return
     * @return transfer tokens owned by the member
     */
    public PagedList<Token, String> getTransferTokensBlocking(@Nullable String offset, int limit) {
        return getTransferTokens(offset, limit).blockingSingle();
    }

    /**
     * Looks up a existing token.
     *
     * @param tokenId token id
     * @return token returned by the server
     */
    public Observable<Token> getToken(String tokenId) {
        return client.getToken(tokenId);
    }

    /**
     * Looks up an existing token.
     *
     * @param tokenId token id
     * @return token returned by the server
     */
    public Token getTokenBlocking(String tokenId) {
        return getToken(tokenId).blockingSingle();
    }


    /**
     * Retrieves a blob that is attached to a transfer token.
     *
     * @param tokenId id of the token
     * @param blobId id of the blob
     * @return Blob
     */
    public Observable<Blob> getTokenAttachment(String tokenId, String blobId) {
        return client.getTokenBlob(tokenId, blobId);
    }

    /**
     * Retrieves a blob that is attached to a token.
     *
     * @param tokenId id of the token
     * @param blobId id of the blob
     * @return Blob
     */
    public Blob getTokenAttachmentBlocking(String tokenId, String blobId) {
        return getTokenAttachment(tokenId, blobId).blockingSingle();
    }


    /**
     * Cancels the token by signing it. The signature is persisted along
     * with the token.
     *
     * @param token token to cancel
     * @return result of cancel token
     */
    public Observable<TokenOperationResult> cancelToken(Token token) {
        return client.cancelToken(token);
    }

    /**
     * Cancels the token by signing it. The signature is persisted along
     * with the token.
     *
     * @param token token to cancel
     * @return result of endorsed token
     */
    public TokenOperationResult cancelTokenBlocking(Token token) {
        return cancelToken(token).blockingSingle();
    }

    /**
     * Creates a test bank account in a fake bank and links the account.
     *
     * @param balance account balance to set
     * @param currency currency code, e.g. "EUR"
     * @return the linked account
     */
    public Observable<Account> createTestBankAccount(double balance, String currency) {
        return createTestBankAccountImpl(balance, currency)
                .map(new Function<Account, Account>() {
                    @Override
                    public Account apply(Account acc) {
                        return new Account(acc);
                    }
                });
    }

    /**
     * Creates a test bank account in a fake bank and links the account.
     *
     * @param balance account balance to set
     * @param currency currency code, e.g. "EUR"
     * @return the linked account
     */
    public Account createTestBankAccountBlocking(double balance, String currency) {
        return createTestBankAccount(balance, currency).blockingSingle();
    }
}
