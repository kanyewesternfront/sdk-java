package io.token.sample;

import static io.token.sample.TestUtil.createClient;
import static io.token.sample.TestUtil.randomAlias;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.grpc.StatusRuntimeException;
import io.token.tpp.Member;
import io.token.tpp.TokenClient;

import org.junit.Test;

public class DeleteMemberSampleTest {
    @Test
    public void deleteMemberTest() {
        try (TokenClient tokenClient = createClient()) {
            Member member = tokenClient.createMemberBlocking(randomAlias());

            assertThat(tokenClient.getMemberBlocking(member.memberId()).memberId())
                    .isEqualTo(member.memberId());

            member.deleteMemberBlocking();

            assertThatExceptionOfType(StatusRuntimeException.class).isThrownBy(() ->
                    tokenClient.getMemberBlocking(member.memberId()));
        }
    }
}
