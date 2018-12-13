/**
 * Copyright (c) 2018 Token, Inc.
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

package io.token.api;

import io.reactivex.Observable;
import io.token.proto.PagedList;
import io.token.proto.common.member.MemberProtos.AddressRecord;
import io.token.proto.common.security.SecurityProtos.Key;
import io.token.proto.common.transaction.TransactionProtos;
import io.token.proto.common.transaction.TransactionProtos.Balance;
import io.token.proto.common.transferinstructions.TransferInstructionsProtos.TransferEndpoint;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents the part of a token member that can be accessed through an access token.
 */
public interface Representable {
    /**
     * Looks up an address by id.
     *
     * @param addressId the address id
     * @return an address record
     */
    Observable<AddressRecord> getAddress(String addressId);

    /**
     * Looks up an address by id.
     *
     * @param addressId the address id
     * @return an address record
     */
    AddressRecord getAddressBlocking(String addressId);

    /**
     * Looks up a funding bank account linked to Token.
     *
     * @param accountId account id
     * @return looked up account
     */
    Observable<Account> getAccount(String accountId);

    /**
     * Looks up a funding bank account linked to Token.
     *
     * @param accountId account id
     * @return looked up account
     */
    Account getAccountBlocking(String accountId);

    /**
     * Looks up account balance.
     *
     * @param accountId the account id
     * @param keyLevel key level
     * @return balance
     */
    Observable<Balance> getBalance(String accountId, Key.Level keyLevel);

    /**
     * Looks up account balance.
     *
     * @param accountId account id
     * @param keyLevel key level
     * @return balance
     */
    Balance getBalanceBlocking(String accountId, Key.Level keyLevel);

    /**
     * Looks up transactions for a given account.
     *
     * @param accountId the account id
     * @param offset optional offset to start at
     * @param limit max number of records to return
     * @param keyLevel key level
     * @return a paged list of transaction records
     */
    Observable<PagedList<TransactionProtos.Transaction, String>> getTransactions(
            String accountId,
            @Nullable String offset,
            int limit,
            Key.Level keyLevel);

    /**
     * Looks up transactions for a given account.
     *
     * @param accountId the account id
     * @param offset optional offset to start at
     * @param limit max number of records to return
     * @param keyLevel key level
     * @return paged list of transactions
     */
    PagedList<TransactionProtos.Transaction, String> getTransactionsBlocking(
            String accountId,
            @Nullable String offset,
            int limit,
            Key.Level keyLevel);

    /**
     * Looks up an existing transaction for a given account.
     *
     * @param accountId the account id
     * @param transactionId ID of the transaction
     * @param keyLevel key level
     * @return transaction record
     */
    Observable<TransactionProtos.Transaction> getTransaction(
            String accountId,
            String transactionId,
            Key.Level keyLevel);

    /**
     * Looks up an existing transaction for a given account.
     *
     * @param accountId the account id
     * @param transactionId ID of the transaction
     * @param keyLevel key level
     * @return transaction
     */
    TransactionProtos.Transaction getTransactionBlocking(
            String accountId,
            String transactionId,
            Key.Level keyLevel);

    /**
     * Resolves transfer destinations for the given account ID.
     *
     * @param accountId account ID
     * @return transfer endpoints
     */
    Observable<List<TransferEndpoint>> resolveTransferDestinations(String accountId);

    /**
     * Resolves transfer destinations for the given account ID.
     *
     * @param accountId account ID
     * @return transfer endpoints
     */
    List<TransferEndpoint> resolveTransferDestinationsBlocking(String accountId);
}
