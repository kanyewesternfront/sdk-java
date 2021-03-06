package io.token.sample;

import static io.token.proto.common.transferinstructions.TransferInstructionsProtos.PurposeOfPayment.PERSONAL_EXPENSES;

import io.token.Destinations;
import io.token.Member;
import io.token.proto.common.alias.AliasProtos.Alias;
import io.token.proto.common.pricing.PricingProtos.Pricing;
import io.token.proto.common.pricing.PricingProtos.TransferQuote;
import io.token.proto.common.security.SecurityProtos.Key;
import io.token.proto.common.token.TokenProtos.Token;
import io.token.util.Util;

/**
 * Creates a transfer token and endorses it to a payee.
 */
public final class CreateAndEndorseTransferTokenSample {
    /**
     * Creates a transfer token and authorizes a money transfer from a payer to a payee.
     *
     * @param payer payer Token member
     * @param payeeAlias payee Token member alias
     * @return a transfer Token
     */
    public static Token createTransferToken(
            Member payer,
            Alias payeeAlias) {
        // We'll use this as a reference ID. Normally, a payer who
        // explicitly sets a reference ID would use an ID from a db.
        // E.g., a bill-paying service might use ID of a "purchase".
        // We don't have a db, so we fake it with a random string:
        String purchaseId = Util.generateNonce();

        // Create a transfer token.
        Token transferToken = payer.createTransferToken(
                100.0, // amount
                "EUR")  // currency
                // source account:
                .setAccountId(payer.getAccounts().get(0).id())
                // payee token alias:
                .setToAlias(payeeAlias)
                // optional description:
                .setDescription("Book purchase")
                // ref id (if not set, will get random ID)
                .setRefId(purchaseId)
                .execute();

        // Payer endorses a token to a payee by signing it
        // with her secure private key.
        transferToken = payer.endorseToken(
                transferToken,
                Key.Level.STANDARD).getToken();

        return transferToken;
    }

    /**
     * Creates a transfer token using some other options.
     *
     * @param payer payer Token member
     * @param payeeId payee Token member Id
     * @return a transfer Token
     */
    public static Token createTransferTokenWithOtherOptions(
            Member payer,
            String payeeId) {

        long now = System.currentTimeMillis();

        TransferQuote srcQuote = TransferQuote.newBuilder()
                .setId("b40d2555df187098da241e6b9079cf45")
                .setAccountCurrency("EUR")
                .setFeesTotal("0.02")
                .addFees(TransferQuote.Fee
                        .newBuilder()
                        .setAmount("0.02")
                        .setDescription("Transfer fee"))
                .build();

        Pricing pricing = Pricing.newBuilder()
                .setSourceQuote(srcQuote)
                .build();

        // Create a transfer token.
        Token transferToken =
                payer.createTransferToken(
                        120.0, // amount
                        "EUR")  // currency
                        // source account:
                        .setAccountId(payer.getAccounts().get(0).id())
                        .setToMemberId(payeeId)
                        .setToMemberId(payeeId)
                        // effective in one second:
                        .setEffectiveAtMs(now + 1000)
                        // expires in 300 seconds:
                        .setExpiresAtMs(now + (300 * 1000))
                        .setRefId("a713c8a61994a749")
                        .setPricing(pricing)
                        .setChargeAmount(10.0)
                        .setDescription("Book purchase")
                        .setPurposeOfPayment(PERSONAL_EXPENSES)
                        .execute();

        // Payer endorses a token to a payee by signing it
        // with her secure private key.
        transferToken = payer.endorseToken(
                transferToken,
                Key.Level.STANDARD).getToken();

        return transferToken;
    }

    /**
     * Creates transfer token to a destination.
     *
     * @param payer Payer who has no linked bank accounts
     * @param payeeAlias Alias of payee member
     * @return a transfer Token
     */
    public static Token createTransferTokenToDestination(
            Member payer,
            Alias payeeAlias) {

        // Create a transfer token.
        Token transferToken =
                payer.createTransferToken(
                        100.0, // amount
                        "EUR")  // currency
                        .setAccountId(payer.getAccounts().get(0).id())
                        .setToAlias(payeeAlias)
                        .addDestination(Destinations.sepa(
                                "XUIWC2489",
                                "DE89 3704 0044 0532 0130 00"))
                        .execute();

        // Payer endorses a token to a payee by signing it with her secure private key.
        transferToken = payer.endorseToken(
                transferToken,
                Key.Level.STANDARD).getToken();

        return transferToken;
    }
}
