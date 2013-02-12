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
package org.apache.ws.security.stax.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;

import org.apache.ws.security.stax.ext.WSSConstants;
import org.apache.ws.security.stax.ext.WSSConstants.WSSKeyIdentifierType;
import org.apache.ws.security.stax.ext.WSSSecurityProperties;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is a test for Certificate Revocation List checking before encryption. 
 * 
 * This test reuses the revoked certificate from SignatureCRLTest
 */
public class EncryptionCRLTest extends AbstractTestBase {

    @Test
    public void testEncryptionWithOutRevocationCheck() throws Exception {

        ByteArrayOutputStream baos;
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            WSSConstants.Action[] actions = new WSSConstants.Action[]{WSSConstants.ENCRYPT};
            securityProperties.setOutAction(actions);
            securityProperties.loadEncryptionKeystore(this.getClass().getClassLoader().getResource("wss40rev.jks"), "security".toCharArray());
            securityProperties.setEncryptionUser("wss40rev");
            securityProperties.setEncryptionKeyIdentifierType(WSSKeyIdentifierType.SECURITY_TOKEN_DIRECT_REFERENCE);

            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            baos = doOutboundSecurity(securityProperties, sourceDocument);

            Document document = documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(baos.toByteArray()));
            NodeList nodeList = document.getElementsByTagNameNS(WSSConstants.TAG_xenc_EncryptedKey.getNamespaceURI(), WSSConstants.TAG_xenc_EncryptedKey.getLocalPart());
            Assert.assertEquals(nodeList.item(0).getParentNode().getLocalName(), WSSConstants.TAG_wsse_Security.getLocalPart());

            XPathExpression xPathExpression = getXPath("/env:Envelope/env:Header/wsse:Security/xenc:EncryptedKey/xenc:EncryptionMethod[@Algorithm='http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p']");
            Node node = (Node) xPathExpression.evaluate(document, XPathConstants.NODE);
            Assert.assertNotNull(node);

            nodeList = document.getElementsByTagNameNS(WSSConstants.TAG_xenc_DataReference.getNamespaceURI(), WSSConstants.TAG_xenc_DataReference.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);

            nodeList = document.getElementsByTagNameNS(WSSConstants.TAG_xenc_EncryptedData.getNamespaceURI(), WSSConstants.TAG_xenc_EncryptedData.getLocalPart());
            Assert.assertEquals(nodeList.getLength(), 1);

            xPathExpression = getXPath("/env:Envelope/env:Body/xenc:EncryptedData/xenc:EncryptionMethod[@Algorithm='http://www.w3.org/2001/04/xmlenc#aes256-cbc']");
            node = (Node) xPathExpression.evaluate(document, XPathConstants.NODE);
            Assert.assertNotNull(node);

            Assert.assertEquals(node.getParentNode().getParentNode().getLocalName(), "Body");
            NodeList childNodes = node.getParentNode().getParentNode().getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    Assert.assertEquals(child.getTextContent().trim(), "");
                } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Assert.assertEquals(child, nodeList.item(0));
                } else {
                    Assert.fail("Unexpected Node encountered");
                }
            }
        }
    }
    
    @Test
    public void testEncryptionWithRevocationCheck() throws Exception {
        {
            WSSSecurityProperties securityProperties = new WSSSecurityProperties();
            WSSConstants.Action[] actions = new WSSConstants.Action[]{WSSConstants.ENCRYPT};
            securityProperties.setEnableRevocation(true);
            securityProperties.setOutAction(actions);
            securityProperties.loadEncryptionKeystore(this.getClass().getClassLoader().getResource("wss40rev.jks"), "security".toCharArray());
            securityProperties.setEncryptionUser("wss40rev");
            securityProperties.setEncryptionKeyIdentifierType(WSSKeyIdentifierType.SECURITY_TOKEN_DIRECT_REFERENCE);
            securityProperties.loadCRLCertStore(this.getClass().getClassLoader().getResource("wss40CACRL.pem"));

            InputStream sourceDocument = this.getClass().getClassLoader().getResourceAsStream("testdata/plain-soap-1.1.xml");
            
            try {
                doOutboundSecurity(securityProperties, sourceDocument);
                Assert.fail("Expected failure on a revocation check");
            } catch (Exception ex) {
                String errorMessage = ex.getMessage();
                // Different errors using different JDKs...
                Assert.assertTrue(errorMessage.contains("Certificate has been revoked")
                    || errorMessage.contains("Certificate revocation")
                    || errorMessage.contains("Error during certificate path validation"));
            }
        }
    }
    
}