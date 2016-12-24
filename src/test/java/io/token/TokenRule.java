package io.token;

import static java.lang.Math.pow;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.util.Strings.isNullOrEmpty;

import com.google.common.net.HostAndPort;
import io.token.proto.bankapi.Fank;
import io.token.proto.common.security.SecurityProtos.SealedMessage;
import io.token.util.Util;

import java.time.Duration;
import java.util.List;
import org.junit.rules.ExternalResource;

/**
 * One can control what gateway the tests run against by setting system property on the
 * command line. E.g:
 * <p>
 * ./gradlew -DTOKEN_GATEWAY=some-ip -DTOKEN_BANK=some-ip test
 */
public class TokenRule extends ExternalResource {
    private final Token token;
    private final BankClient bankClient;

    public TokenRule() {
        HostAndPort gateway = HostAndPort
                .fromString(getEnvProperty("TOKEN_GATEWAY", "localhost"))
                .withDefaultPort(9000);

        HostAndPort bank = HostAndPort
                .fromString(getEnvProperty("TOKEN_BANK", "localhost"))
                .withDefaultPort(9100);

        boolean useSsl = Boolean.parseBoolean(getEnvProperty("TOKEN_USE_SSL", "false"));

        this.bankClient = new BankClient(
                bank.getHostText(),
                bank.getPort(),
                useSsl);

        this.token = Token.builder()
                .hostName(gateway.getHostText())
                .port(gateway.getPort())
                .timeout(Duration.ofMinutes(10))  // Set high for easy debugging.
                .useSsl(useSsl)
                .build();
    }

    private static String getEnvProperty(String name, String defaultValue) {
        String override = System.getenv(name);
        if (!isNullOrEmpty(override)) {
            return override;
        }

        override = System.getProperty(name);
        if (!isNullOrEmpty(override)) {
            return override;
        }

        return defaultValue;
    }

    private static String string() {
        int length = randomInt(3, 7);
        return randomAlphabetic(length);
    }

    private static int randomInt(int digits) {
        return randomInt(
                (int) pow(10, digits),
                (int) pow(10, digits + 1) - 1);
    }

    private static int randomInt(int min, int max) {
        return (int) (Math.random() * (max - min)) + min;
    }

    public Token token() {
        return token;
    }

    public Member member() {
        String username = "username-" + Util.generateNonce();
        return token.createMember(username);
    }

    public Account account() {
        Member member = member();
        String bankAccountNumber = "iban:" + randomInt(7);
        Fank.Client client = bankClient.addClient("Test " + string(), "Testoff");
        bankClient.addAccount(client, "Test Account", bankAccountNumber, 1000000.00, "USD");
        List<SealedMessage> accountLinkPayloads = bankClient.startAccountsLinking(
                member.firstUsername(),
                client.getId(),
                singletonList(bankAccountNumber));

        return member
                .linkAccounts("iron", accountLinkPayloads)
                .get(0);
    }

    public BankClient bankClient() {
        return bankClient;
    }

    @Override
    protected void before() throws Throwable {
        super.before();
    }
}
