//---------------------------------------------------------------------------------------------------------------------------------
// File: SQLServerAeadAes256CbcHmac256EncryptionKey.java
//
//
// Microsoft JDBC Driver for SQL Server
// Copyright(c) Microsoft Corporation
// All rights reserved.
// MIT License
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files(the ""Software""), 
//  to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
//  and / or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions :
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
//  IN THE SOFTWARE.
//---------------------------------------------------------------------------------------------------------------------------------
 

package com.microsoft.sqlserver.jdbc;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;

/**
 * Encryption key class which consist of following 4 keys :
 * 1) root key - Main key which is used to derive following keys
 * 2) encryption key - A derived key that is used to encrypt the plain text and generate cipher text
 * 3) mac_key - A derived key that is used to compute HMAC of the cipher text
 * 4) iv_key - A derived key that is used to generate a synthetic IV from plain text data.
 */
 class SQLServerAeadAes256CbcHmac256EncryptionKey extends SQLServerSymmetricKey
{

    // This is the key size in the bits, since we are using AES256, it will 256
    static final int keySize = 256;
    // Name of algorithm associated with this key
    private final String algorithmName;
    // Salt used to derive encryption key
    private String encryptionKeySaltFormat;
    // Salt used to derive mac key
    private String macKeySaltFormat;
    // Salt used to derive iv key
    private String ivKeySaltFormat;
    private SQLServerSymmetricKey encryptionKey;
    private SQLServerSymmetricKey macKey;
    private SQLServerSymmetricKey ivKey;

    /**
     * Derive all the keys from the root key
     * @param rootKey key used to derive other keys
     * @param algorithmName name of the algorithm associated with keys 
     * @throws SQLServerException 
     */
     SQLServerAeadAes256CbcHmac256EncryptionKey(byte[] rootKey, String algorithmName) throws SQLServerException
    {
        super(rootKey);
        this.algorithmName = algorithmName;
        encryptionKeySaltFormat = "Microsoft SQL Server cell encryption key with encryption algorithm:" +
            this.algorithmName + " and key length:" + keySize;
        macKeySaltFormat = "Microsoft SQL Server cell MAC key with encryption algorithm:" +
            this.algorithmName + " and key length:" + keySize;
        ivKeySaltFormat = "Microsoft SQL Server cell IV key with encryption algorithm:" +
            this.algorithmName + " and key length:" + keySize;
        int keySizeInBytes = (keySize / 8);
        if (rootKey.length != keySizeInBytes)
        {
            MessageFormat form =new MessageFormat(SQLServerException.getErrString("R_InvalidKeySize"));
            Object[] msgArgs={rootKey.length,keySizeInBytes,this.algorithmName};
            throw new SQLServerException(this, form.format(msgArgs), null, 0, false);
            
        }

        // Derive encryption key

        byte[] encKeyBuff = new byte[keySizeInBytes];
        try
        {
            // By default Java is big endian, we are getting bytes in little endian(LE in UTF-16LE)
            // to make it compatible with C# driver which is little endian
            encKeyBuff = SQLServerSecurityUtility.getHMACWithSHA256(
                encryptionKeySaltFormat.getBytes("UTF-16LE"),
                rootKey,
                encKeyBuff.length);

            encryptionKey = new SQLServerSymmetricKey(encKeyBuff);

            // Derive mac key from root key
            byte[] macKeyBuff = new byte[keySizeInBytes];
            macKeyBuff = SQLServerSecurityUtility.getHMACWithSHA256(
                macKeySaltFormat.getBytes("UTF-16LE"),
                rootKey,
                macKeyBuff.length);

            macKey = new SQLServerSymmetricKey(macKeyBuff);

            // Derive the initialization vector from root key
            byte[] ivKeyBuff = new byte[keySizeInBytes];
            ivKeyBuff = SQLServerSecurityUtility.getHMACWithSHA256(
                ivKeySaltFormat.getBytes("UTF-16LE"),
                rootKey,
                ivKeyBuff.length);
            ivKey = new SQLServerSymmetricKey(ivKeyBuff);
        }
        catch (UnsupportedEncodingException e)
        {
            MessageFormat form = new MessageFormat(
                SQLServerException.getErrString("R_unsupportedEncoding"));
            Object[] msgArgs = { "UTF-16LE" };
            throw new SQLServerException(this, form.format(msgArgs), null, 0, false);
        }
        catch (InvalidKeyException  | NoSuchAlgorithmException e)
        {
            MessageFormat form = new MessageFormat(
                SQLServerException.getErrString("R_KeyExtractionFailed"));
            Object[] msgArgs = { e.getMessage() };
            throw new SQLServerException(this, form.format(msgArgs), null, 0, false);
        }
        

    }

    /**
     * 
     * @return encryption key 
     */
     byte[] getEncryptionKey()
    {
        return encryptionKey.getRootKey();
    }

    /**
     * 
     * @return mac key
     */
     byte[] getMacKey()
    {
        return macKey.getRootKey();
    }

    /**
     * 
     * @return iv key 
     */
     byte[] getIVKey()
    {
        return ivKey.getRootKey();
    }

}
