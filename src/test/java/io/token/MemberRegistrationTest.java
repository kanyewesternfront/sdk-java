package io.token;

import io.token.proto.common.security.SecurityProtos.Key.Level;
import io.token.security.Crypto;
import io.token.security.SecretKey;
import io.token.util.Util;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

import static io.token.asserts.MemberAssertion.assertThat;

public class MemberRegistrationTest {
    @Rule public TokenRule rule = new TokenRule();

    @Test
    public void createMember() {
        String alias = "alexey-" + Util.generateNonce();
        Member member = rule.token().createMember(alias);
        assertThat(member)
                .hasAlias(alias)
                .hasOneKey();
    }

    @Test
    public void loginMember() {
        Member member = rule.member();
        Member loggedIn = rule.token().login(member.memberId(), member.key());
        assertThat(loggedIn)
                .hasAliases(member.aliases())
                .hasOneKey();
    }

    @Test
    public void addKey() {
        SecretKey key2 = Crypto.generateSecretKey();
        SecretKey key3 = Crypto.generateSecretKey();

        Member member = rule.member();
        member.approveKey(key2.getPublicKey(), Level.STANDARD);
        member.approveKey(key3.getPublicKey(), Level.PRIVILEGED);

        assertThat(member)
                .hasOneAlias()
                .hasNKeys(3)
                .hasKey(key2.getPublicKey())
                .hasKey(key3.getPublicKey());
    }

    @Test
    public void removeKey() {
        Member member = rule.member();

        SecretKey key2 = Crypto.generateSecretKey();
        member.approveKey(key2.getPublicKey(), Level.STANDARD);
        assertThat(member)
                .hasNKeys(2)
                .hasKey(key2.getPublicKey());

        member.removeKey(key2.getId());
        assertThat(member)
                .hasOneAlias()
                .hasOneKey();
    }

    @Test
    public void addAlias() {
        String alias1 = "alexey-" + Util.generateNonce();
        String alias2 = "alex-" + Util.generateNonce();
        String alias3 = "ak-" + Util.generateNonce();

        Member member = rule.token().createMember(alias1);
        member.addAlias(alias2);
        member.addAlias(alias3);

        assertThat(member)
                .hasAliases(alias1, alias2, alias3)
                .hasOneKey();
    }

    @Test
    public void removeAlias() {
        String alias1 = "alexey-" + Util.generateNonce();
        String alias2 = "alex-" + Util.generateNonce();

        Member member = rule.token().createMember(alias1);

        member.addAlias(alias2);
        assertThat(member).hasAliases(alias1, alias2);

        member.removeAlias(alias2);
        assertThat(member)
                .hasAliases(alias1)
                .hasOneKey();
    }

    @Test
    public void aliasDoesNotExist() {
        Assertions.assertThat(rule.member().aliasExists("john" + Util.generateNonce())).isFalse();
    }

    @Test
    public void aliasExists() {
        String alias = "john-" + Util.generateNonce();
        rule.token().createMember(alias);
        Assertions.assertThat(rule.member().aliasExists(alias)).isTrue();
    }
}
