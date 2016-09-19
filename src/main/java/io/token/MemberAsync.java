package io.token;

import io.token.proto.common.member.MemberProtos;
import io.token.proto.common.member.MemberProtos.Address;
import io.token.proto.common.payment.PaymentProtos.Payment;
import io.token.rpc.Client;
import io.token.security.SecretKey;
import io.token.util.codec.ByteEncoding;
import rx.Observable;

import javax.annotation.Nullable;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;

/**
 * Represents a Member in the Token system. Each member has an active secret
 * and public key pair that is used to perform authentication.
 */
public final class MemberAsync {
    private final SecretKey key;
    private final Client client;
    private final MemberProtos.Member.Builder member;

    /**
     * @param member internal member representation, fetched from server
     * @param key secret/public key pair
     * @param client RPC client used to perform operations against the server
     */
    MemberAsync(MemberProtos.Member member, SecretKey key, Client client) {
        this.key = key;
        this.client = client;
        this.member = member.toBuilder();
    }

    /**
     * @return synchronous version of the account API
     */
    public Member sync() {
        return new Member(this);
    }

    /**
     * @return a unique ID that identifies the member in the Token system
     */
    public String getMemberId() {
        return member.getId();
    }

    /**
     * @return secret/public keys associated with this member instance
     */
    public SecretKey getKey() {
        return key;
    }

    /**
     * @return first alias owned by the user
     */
    public String getFirstAlias() {
        return member.getAliasesCount() == 0 ? null : member.getAliases(0);
    }

    /**
     * @return list of aliases owned by the member
     */
    public List<String> getAliases() {
        return member.getAliasesList();
    }

    /**
     * @return list of public keys that are approved for this member
     */
    public List<byte[]> getPublicKeys() {
        return member.getKeysBuilderList()
                .stream()
                .map(k -> ByteEncoding.parse(k.getPublicKey()))
                .collect(toList());
    }

    /**
     * Adds a new alias for the member.
     *
     * @param alias alias, e.g. 'john', must be unique
     */
    public Observable<Void> addAlias(String alias) {
        return client
                .addAlias(member.build(), alias)
                .map(m -> {
                    member.clear().mergeFrom(m);
                    return null;
                });
    }

    /**
     * Removes an alias for the member.
     *
     * @param alias alias, e.g. 'john'
     */
    public Observable<Void> removeAlias(String alias) {
        return client
                .removeAlias(member.build(), alias)
                .map(m -> {
                    member.clear().mergeFrom(m);
                    return null;
                });
    }

    /**
     * Approves a public key owned by this member. The key is added to the list
     * of valid keys for the member.
     *
     * @param publicKey public key to add to the approved list
     * @param level key security level
     */
    public Observable<Void> approveKey(byte[] publicKey, int level) {
        return client
                .addKey(member.build(), level, publicKey)
                .map(m -> {
                    member.clear().mergeFrom(m);
                    return null;
                });
    }

    /**
     * Removes a public key owned by this member.
     *
     * @param keyId key ID of the key to remove
     */
    public Observable<Void> removeKey(String keyId) {
        return client
                .removeKey(member.build(), keyId)
                .map(m -> {
                    member.clear().mergeFrom(m);
                    return null;
                });
    }

    /**
     * Links a funding bank account to Token and returns it to the caller.
     *
     * @param bankId bank id
     * @param accountLinkPayload account link authorization payload generated
     *                           by the bank
     */
    public Observable<List<AccountAsync>> linkAccounts(String bankId, byte[] accountLinkPayload) {
        return client
                .linkAccounts(bankId, accountLinkPayload)
                .map(accounts -> accounts.stream()
                        .map(a -> new AccountAsync(this, a, client))
                        .collect(toList()));
    }

    /**
     * Links a funding bank account to Token and returns it to the caller.
     *
     * @return list of accounts
     */
    public Observable<List<AccountAsync>> lookupAccount() {
        return client
                .lookupAccounts()
                .map(accounts -> accounts.stream()
                        .map(a -> new AccountAsync(this, a, client))
                        .collect(toList()));
    }

    /**
     * Looks up an existing token payment.
     *
     * @param paymentId ID of the payment record
     * @return payment record
     */
    public Observable<Payment> lookupPayment(String paymentId) {
        return client.lookupPayment(paymentId);
    }

    /**
     * Looks up existing token payments.
     *
     * @param offset offset to start at
     * @param limit max number of records to return
     * @param tokenId optional token id to restrict the search
     * @return payment record
     */
    public Observable<List<Payment>> lookupPayments(int offset, int limit, @Nullable String tokenId) {
        return client.lookupPayments(offset, limit, tokenId);
    }

    /**
     * Creates a new member address
     *
     * @param name the name of the address
     * @param address the address json
     * @return an address record created
     */
    public Observable<Address> createAddress(String name, String address) {
        return client.createAddress(name, address);
    }

    /**
     * Looks up an address by id
     *
     * @param addressId the address id
     * @return an address record
     */
    public Observable<Address> getAddress(String addressId) {
        return client.getAddress(addressId);
    }

    /**
     * Looks up member addresses
     *
     * @return a list of addresses
     */
    public Observable<List<Address>> getAddresses() {
        return client.getAddresses();
    }

    /**
     * Deletes a member address by its id
     *
     * @param addressId the id of the address
     */
    public Observable<Void> deleteAddress(String addressId) {
        return client.deleteAddress(addressId);
    }

    /**
     * Sets member preferences
     *
     * @param preferences member json preferences
     */
    public Observable<Void> setPreferences(String preferences) {
        return client.setPreferences(preferences);
    }

    /**
     * Looks up member preferences
     *
     * @return member preferences
     */
    public Observable<String> getPreferences() {
        return client.getPreferences();
    }

    @Override
    public String toString() {
        return reflectionToString(this);
    }
}