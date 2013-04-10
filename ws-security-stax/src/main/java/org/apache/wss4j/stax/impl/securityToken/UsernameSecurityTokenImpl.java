/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.wss4j.stax.impl.securityToken;

import org.apache.wss4j.common.bsp.BSPRule;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.common.principal.UsernameTokenPrincipal;
import org.apache.wss4j.stax.ext.WSInboundSecurityContext;
import org.apache.wss4j.stax.ext.WSSConstants;
import org.apache.wss4j.stax.securityToken.UsernameSecurityToken;
import org.apache.wss4j.stax.securityToken.WSSecurityTokenConstants;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.stax.config.JCEAlgorithmMapper;
import org.apache.xml.security.stax.ext.XMLSecurityConstants;
import org.apache.xml.security.stax.impl.securityToken.AbstractInboundSecurityToken;

import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Subject;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;

public class UsernameSecurityTokenImpl extends AbstractInboundSecurityToken implements UsernameSecurityToken {

    private static final long DEFAULT_ITERATION = 1000;

    private WSSConstants.UsernameTokenPasswordType usernameTokenPasswordType;
    private String username;
    private String password;
    private String createdTime;
    private byte[] nonce;
    private byte[] salt;
    private Long iteration;
    private final WSInboundSecurityContext wsInboundSecurityContext;
    private Subject subject;
    private Principal principal;

    public UsernameSecurityTokenImpl(WSSConstants.UsernameTokenPasswordType usernameTokenPasswordType,
                                     String username, String password, String createdTime, byte[] nonce,
                                     byte[] salt, Long iteration,
                                     WSInboundSecurityContext wsInboundSecurityContext, String id,
                                     WSSecurityTokenConstants.KeyIdentifier keyIdentifier) {
        super(wsInboundSecurityContext, id, keyIdentifier);
        this.usernameTokenPasswordType = usernameTokenPasswordType;
        this.username = username;
        this.password = password;
        this.createdTime = createdTime;
        this.nonce = nonce;
        this.salt = salt;
        this.iteration = iteration;
        this.wsInboundSecurityContext = wsInboundSecurityContext;
    }

    @Override
    public boolean isAsymmetric() throws XMLSecurityException {
        return false;
    }

    @Override
    protected Key getKey(String algorithmURI, XMLSecurityConstants.AlgorithmUsage algorithmUsage,
                         String correlationID) throws XMLSecurityException {

        Key key = getSecretKey().get(algorithmURI);
        if (key != null) {
            return key;
        }

        byte[] secretToken = generateDerivedKey(wsInboundSecurityContext);
        String algoFamily = JCEAlgorithmMapper.getJCERequiredKeyFromURI(algorithmURI);
        key = new SecretKeySpec(secretToken, algoFamily);
        setSecretKey(algorithmURI, key);
        return key;
    }

    @Override
    public WSSecurityTokenConstants.TokenType getTokenType() {
        return WSSecurityTokenConstants.UsernameToken;
    }

    /**
     * This method generates a derived key as defined in WSS Username
     * Token Profile.
     *
     * @return Returns the derived key a byte array
     * @throws WSSecurityException
     */
    public byte[] generateDerivedKey() throws WSSecurityException {
        return generateDerivedKey(wsInboundSecurityContext);
    }

    /**
     * This method generates a derived key as defined in WSS Username
     * Token Profile.
     *
     * @return Returns the derived key a byte array
     * @throws org.apache.wss4j.common.ext.WSSecurityException
     *
     */
    protected byte[] generateDerivedKey(WSInboundSecurityContext wsInboundSecurityContext) throws WSSecurityException {

        if (wsInboundSecurityContext != null) {
            if (salt == null || salt.length == 0) {
                wsInboundSecurityContext.handleBSPRule(BSPRule.R4217);
            }
            if (iteration == null || iteration < DEFAULT_ITERATION) {
                wsInboundSecurityContext.handleBSPRule(BSPRule.R4218);
            }
        }

        Long iters = iteration;
        if (iters == null || iters == 0) {
            iters = DEFAULT_ITERATION;
        }
        byte[] pwBytes;
        try {
            pwBytes = password.getBytes("UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY, e);
        }

        byte[] pwSalt = new byte[salt.length + pwBytes.length];
        System.arraycopy(pwBytes, 0, pwSalt, 0, pwBytes.length);
        System.arraycopy(salt, 0, pwSalt, pwBytes.length, salt.length);

        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.INVALID_SECURITY, "noSHA1availabe", e);
        }
        sha.reset();

        // Make the first hash round with start value
        byte[] k = sha.digest(pwSalt);

        // Perform the 1st up to iteration-1 hash rounds
        for (int i = 1; i < iters; i++) {
            k = sha.digest(k);
        }
        return k;
    }

    @Override
    public Principal getPrincipal() throws WSSecurityException {
        if (this.principal == null) {
            this.principal = new UsernameTokenPrincipal() {
                //todo passwordType and passwordDigest return Enum-Type ?
                @Override
                public boolean isPasswordDigest() {
                    return usernameTokenPasswordType == WSSConstants.UsernameTokenPasswordType.PASSWORD_DIGEST;
                }


                @Override
                public String getPasswordType() {
                    return usernameTokenPasswordType.getNamespace();
                }

                @Override
                public String getName() {
                    return username;
                }

                @Override
                public String getPassword() {
                    return password;
                }

                @Override
                public String getCreatedTime() {
                    return createdTime;
                }

                @Override
                public byte[] getNonce() {
                    return nonce;
                }
            };
        }
        return this.principal;
    }

    public WSSConstants.UsernameTokenPasswordType getUsernameTokenPasswordType() {
        return usernameTokenPasswordType;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getSalt() {
        return salt;
    }

    public Long getIteration() {
        return iteration;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    @Override
    public Subject getSubject() throws WSSecurityException {
        return subject;
    }
}