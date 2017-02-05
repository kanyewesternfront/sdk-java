package io.token.sample;

import static io.token.Token.TokenCluster.DEVELOPMENT;
import static io.token.sample.CreateAndEndorseTransferTokenSample.createTransferToken;
import static org.assertj.core.api.Assertions.assertThat;

import io.token.Member;
import io.token.proto.common.token.TokenProtos.Token;

import org.junit.Test;

public class CreateAndEndorseTransferTokenSampleTest {
    @Test
    public void createPaymentTokenTest() {
        Member payer = LinkMemberAndBankSample.linkBankAccounts(DEVELOPMENT);
        Member payee = LinkMemberAndBankSample.linkBankAccounts(DEVELOPMENT);

        Token token = createTransferToken(payer, payee.firstUsername());
        assertThat(token).isNotNull();
    }
}
