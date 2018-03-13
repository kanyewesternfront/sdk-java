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

import com.google.auto.value.AutoValue;
import io.token.exceptions.InvalidTokenRequestQuery;
import io.token.proto.ProtoJson;
import io.token.proto.common.security.SecurityProtos.Signature;
import io.token.proto.common.token.TokenProtos.TokenRequestState;

import java.util.HashMap;
import java.util.Map;

@AutoValue
public abstract class TokenRequestQueryParser {
    private static final String TOKEN_ID_HEADER = "token-id";
    private static final String NONCE_HEADER = "nonce";
    private static final String STATE_HEADER = "state";
    private static final String SIGNATURE_HEADER = "signature";

    /**
     * Create a new instance of TokenRequestQueryParser.
     *
     * @param query query
     * @return instance of TokenRequestQueryParser
     */
    public static TokenRequestQueryParser create(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();

        for (String param : params) {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(name, value);
        }

        verifyParameters(map);

        return new AutoValue_TokenRequestQueryParser(
                map.get(TOKEN_ID_HEADER),
                map.get(NONCE_HEADER),
                (TokenRequestState) ProtoJson.fromJson(
                        map.get(STATE_HEADER),
                        TokenRequestState.newBuilder()),
                (Signature) ProtoJson.fromJson(map.get(SIGNATURE_HEADER), Signature.newBuilder()));
    }

    private static void verifyParameters(Map<String, String> map) {
        if (!map.containsKey(TOKEN_ID_HEADER)
                || !map.containsKey(NONCE_HEADER)
                || !map.containsKey(STATE_HEADER)
                || !map.containsKey(SIGNATURE_HEADER)) {
            throw new InvalidTokenRequestQuery();
        }
    }

    public abstract String getTokenId();

    public abstract String getNonce();

    public abstract TokenRequestState getState();

    public abstract Signature getSignature();
}
